/* Copyright (C) Intel 2013
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @file panic.h
 * @brief File containing functions to detect and process ipanic events.
 */

#include "crashutils.h"
#include "dropbox.h"
#include "fsutils.h"
#include "privconfig.h"
#include "inotify_handler.h"
#include "trigger.h"
#include "ramdump.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>

/*
* Name          : check_aplogs_tobackup
* Description   : backup a number of aplogs if a patten is found in a file
* Parameters    :
*   char *filename        -> filename where a pattern is searched
*/
static int check_aplogs_tobackup(char *filename) {
    char ipanic_chain[PROPERTY_VALUE_MAX];
    int nbpatterns, res;
    char **patterns_array;
    int idx, nbrecords = 10, recordsize = PROPERTY_VALUE_MAX;

    if (property_get(PROP_IPANIC_PATTERN, ipanic_chain, "") > 0) {
        /* Found the property, split it into an array */
        patterns_array = commachain_to_fixedarray(ipanic_chain, recordsize, nbrecords, &nbpatterns);
        if (nbpatterns < 0 ) {
            LOGE("%s: Cannot transform the property %s(which is %s) into an array... error is %d - %s\n",
                __FUNCTION__, PROP_IPANIC_PATTERN, ipanic_chain, nbpatterns, strerror(-nbpatterns));
            for (idx = 0 ; idx < nbrecords ; idx++) {
                free(patterns_array[idx]);
            }
            free(patterns_array);
            return 0;
        }
        if ( nbpatterns == 0 ) return 0;
        /* Add the prepattern "EIP is at" to each of the patterns */
        for (idx = 0 ; idx < nbpatterns ; idx++) {
            char *prepattern = "EIP is at ";
            int prepatternlen = strlen(prepattern);
            memmove(&patterns_array[idx][prepatternlen], patterns_array[idx],
                MIN((int)strlen(patterns_array[idx])+1, PROPERTY_VALUE_MAX-prepatternlen));
            /* insure the chain is null terminated */
            patterns_array[idx][PROPERTY_VALUE_MAX-1] = 0;
            memcpy(patterns_array[idx], prepattern, prepatternlen);
        }
        res = find_oneofstrings_in_file(filename, (char**)patterns_array, nbpatterns);
        if (res)
            process_log_event(NULL, NULL, MODE_APLOGS);
        /* Cleanup the patterns_array allocated in commchain... */
        for (idx = 0 ; idx < nbrecords ; idx++) {
            free(patterns_array[idx]);
        }
        free(patterns_array);
    }
    else {
        /* By default, searches for the single following pattern... */
        res = find_str_in_file(filename, "EIP is at SGXInitialise", NULL);
        if (res)
            process_log_event(NULL, NULL, MODE_APLOGS);
    }

    return res;
}

static void set_ipanic_crashtype_and_reason(char *crashtype, char *reason) {
    char *key;

    /* Set crash type according to pattern found in Ipanic console file or according to startup reason value*/
    if ( find_str_in_file(SAVED_CONSOLE_NAME, "Kernel panic - not syncing: Kernel Watchdog", NULL))
        strcpy(crashtype, KERNEL_SWWDT_CRASH);
    else if  ( find_str_in_file(SAVED_CONSOLE_NAME, "EIP is at pmu_sc_irq", NULL) )
        // This panic is triggered by a fabric error
        // It is marked as a kernel panic linked to a HW watdchog
        // to create a link between these 2 critical crashes
        strcpy(crashtype, KERNEL_HWWDT_CRASH);
    else if ( find_str_in_file(SAVED_CONSOLE_NAME, "EIP is at panic_dbg_set", NULL)  || find_str_in_file(SAVED_CONSOLE_NAME, "EIP is at kwd_trigger_open", NULL))
        strcpy(crashtype, KERNEL_FAKE_CRASH);
    else
        strcpy(crashtype, KERNEL_CRASH);

    if (!find_str_in_file(SAVED_CONSOLE_NAME, "sdhci_pci_power_up_host: host controller power up is done", NULL)) {
        // An error is raised when the panic console file does not end normally
       raise_infoerror(ERROREVENT, IPANIC_CORRUPTED);
    }
    if (!strncmp(crashtype, KERNEL_FAKE_CRASH, sizeof(KERNEL_FAKE_CRASH)))
         strcat(reason,"_FAKE");
    else if (!strncmp(reason, "HWWDT_RESET_FAKE", 16))
         strcpy(crashtype, KERNEL_FAKE_CRASH);
    else if (!strncmp(reason,"HWWDT_RESET", 11))
         strcpy(crashtype, KERNEL_HWWDT_CRASH);
    else if (strncmp(reason,"SWWDT_RESET", 11) != 0) {
         // In some corner cases, the startup reason is not correctly set
         // In this case, an ERROR is sent to have correct SWWDT metrics
         raise_infoerror(ERROREVENT, CRASHLOG_SWWDT_MISSING);
    }
}

/**
 * @brief Checks if a PANIC event occurred
 *
 * This functions is called at reboot (ie at crashlogd boot ) and checks if a
 * PANIC event occurred by parsing panic console file.
 * It then checks the IPANIC event type.
 * Returned values are :
 *  -1 if a problem occurs (can't create crash dir)
 *   0 for nominal case
 *   1 if the panic console file doesn't exist
 */
int crashlog_check_panic(char *reason, int test) {
    char destination[PATHMAX];
    char crashtype[32] = {'\0'};
    int dir;
    const char *dateshort = get_current_time_short(1);
    char *key;

    if ( !test && !file_exists(CURRENT_PANIC_CONSOLE_NAME) ) {
        /* Nothing to do */
        return 1;
    }
    //legacy : copy console to data/dontpanic
    do_copy_eof(PANIC_THREAD_NAME, SAVED_THREAD_NAME);
    do_copy_eof(PANIC_CONSOLE_NAME, SAVED_CONSOLE_NAME);
    //crashtype calculation should be done after SAVED_CONSOLE_NAME calculation
    set_ipanic_crashtype_and_reason(crashtype, reason);
    dir = find_new_crashlog_dir(CRASH_MODE);
    if (dir < 0) {
        LOGE("%s: Cannot get a valid new crash directory...\n", __FUNCTION__);
        key = raise_event(CRASHEVENT, crashtype, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), crashtype);
        free(key);
        return -1;
    }

    destination[0] = '\0';
    snprintf(destination, sizeof(destination), "%s%d/%s_%s.txt", CRASH_DIR, dir,
            THREAD_NAME, dateshort);
    do_copy(SAVED_THREAD_NAME, destination, MAXFILESIZE);

    snprintf(destination,sizeof(destination),"%s%d/",CRASH_DIR,dir);
    destination[0] = '\0';
    snprintf(destination, sizeof(destination), "%s%d/%s_%s.txt", CRASH_DIR, dir,
            CONSOLE_NAME, dateshort);

    do_copy(SAVED_CONSOLE_NAME, destination, MAXFILESIZE);

    destination[0] = '\0';
    snprintf(destination, sizeof(destination), "%s%d/%s_%s.txt", CRASH_DIR, dir,
            LOGCAT_NAME, dateshort);
    do_copy(SAVED_LOGCAT_NAME, destination, MAXFILESIZE);

    destination[0] = '\0';
    snprintf(destination, sizeof(destination), "%s%d/%s_%s.txt", CRASH_DIR, dir,
            GBUFFER_NAME, dateshort);
    do_copy(GBUFFER_FILE, destination, MAXFILESIZE);

    do_last_kmsg_copy(dir);

    overwrite_file(CURRENT_PANIC_CONSOLE_NAME, "1");

    destination[0] = '\0';
    snprintf(destination, sizeof(destination), "%s%d/", CRASH_DIR, dir);

    key = raise_event(CRASHEVENT, crashtype, NULL, destination);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), crashtype, destination);
    free(key);

    // if a pattern is found in the console file, upload a large number of aplogs
    // property persist.crashlogd.panic.pattern is used to fill the list of pattern
    // Each pattern is split by a semicolon in the property
    check_aplogs_tobackup(SAVED_CONSOLE_NAME);

    return 0;
}

static int crashlog_check_console_file(char *reason)
{
    char date_tmp[32];
    char date_tmp_2[32];
    time_t t;
    char destination[PATHMAX];
    char crashtype[32] = {'\0'};
    int dir,panic_found;
    char *key;
    char ram_console[PATHMAX];
    const char *dateshort = get_current_time_short(1);

    if (!find_str_in_file(SAVED_PANIC_RAM, "Kernel panic - not syncing:", NULL)) {
        return 1; /* Not a PANIC : return */
    }
    else {
        // to be homogeneous with do_last_kmsg_copy, we use do_copy_tail
        panic_found = 1;
    }

    if (panic_found) {
        if (find_str_in_file(SAVED_PANIC_RAM, "Kernel panic - not syncing: Kernel Watchdog", NULL))
            strcpy(crashtype, KERNEL_SWWDT_CRASH);
        else if  (find_str_in_file(SAVED_PANIC_RAM, "EIP is at pmu_sc_irq", NULL))
            // This panic is triggered by a fabric error
            // It is marked as a kernel panic linked to a HW watdchog
            // to create a link between these 2 critical crashes
            strcpy(crashtype, KERNEL_HWWDT_CRASH);
        else if (find_str_in_file(SAVED_PANIC_RAM, "EIP is at panic_dbg_set", NULL)  || find_str_in_file(SAVED_PANIC_RAM, "EIP is at kwd_trigger_open", NULL))
            strcpy(crashtype, KERNEL_FAKE_CRASH);
        else
            strcpy(crashtype, KERNEL_CRASH);

        if (!strncmp(crashtype, KERNEL_FAKE_CRASH, sizeof(KERNEL_FAKE_CRASH)))
             strcat(reason,"_FAKE");
        else if (!strncmp(reason, "HWWDT_RESET_FAKE", 16))
             strcpy(crashtype, KERNEL_FAKE_CRASH);
        else if (!strncmp(reason,"HWWDT_RESET", 11))
             strcpy(crashtype, KERNEL_HWWDT_CRASH);
         else if (strncmp(reason,"SWWDT_RESET", 11) != 0)
             // In some corner cases, the startupreason is not correctly set
             // In this case, an ERROR is sent to have correct SWWDT metrics
             raise_infoerror(ERROREVENT, CRASHLOG_SWWDT_MISSING);

        dir = find_new_crashlog_dir(CRASH_MODE);
        if (dir < 0) {
            LOGE("%s: Cannot get a valid new crash directory...\n", __FUNCTION__);
            key = raise_event(CRASHEVENT, crashtype, NULL, NULL);
            LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), crashtype);
            free(key);
            return -1;
        }

        destination[0] = '\0';
        snprintf(destination, sizeof(destination), "%s%d/%s_%s.txt", CRASH_DIR, dir,
                CONSOLE_NAME, dateshort);
        do_copy(SAVED_PANIC_RAM, destination, MAXFILESIZE);

        destination[0] = '\0';
        snprintf(destination, sizeof(destination), "%s%d/", CRASH_DIR, dir);
        key = raise_event(CRASHEVENT, crashtype, NULL, destination);
        LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), crashtype, destination);
        free(key);

        // if a pattern is found in the console file, upload a large number of aplogs
        // property persist.crashlogd.panic.pattern is used to fill the list of pattern
        // Each pattern is split by a semicolon in the property
        check_aplogs_tobackup(SAVED_PANIC_RAM);
        return 0;
       }
    /* No RAM console : nothing to do */
    return 1;
}

/**
 * @brief Checks in RAM console if a PANIC event occurred
 *
 * This functions is called at reboot (ie at crashlogd boot ) and checks if a
 * PANIC event occurred by parsing the RAM console.
 * It then checks the IPANIC event type.
 * Returned values are :
 *  -1 if a problem occurs (can't create crash dir)
 *   0 for nominal case
 *   1 if the RAM console doesn't exist or if it exists but no panic detected.
 */
int crashlog_check_ram_panic(char *reason)
{
    struct stat info;

    if (stat(LAST_KMSG, &info) == 0) {
        do_copy_tail(LAST_KMSG, SAVED_PANIC_RAM, MAXFILESIZE);
    } else if (stat(CONSOLE_RAMOOPS, &info) == 0) {
        do_copy_tail(CONSOLE_RAMOOPS, SAVED_PANIC_RAM, MAXFILESIZE);
    } else {
        // no file found, should return
        return 1;
    }

    return crashlog_check_console_file(reason);
}

/**
 * @brief Checks if a KDUMP event occured.
 *
 * This functions is called at boot (ie at crashlogd boot ) and checks if a
 * KDUMP event occurred by checking specific files existence.
 * It then checks the IPANIC event type.
 * Returned values are :
 *  -1 if a problem occurs (can't create crash dir)
 *   0 for nominal case
 */
int crashlog_check_kdump(char *reason, int test) {
    int start_flag = 0;
    int file_flag = 0;
    int finish_flag = 0;
    int curr_stat = 0;
    char *crashtype = NULL;
    struct stat info;
    char destination[PATHMAX];
    int dir;
    char *key;
    const char *dateshort = get_current_time_short(1);

    if (stat(KDUMP_START_FLAG, &info) == 0)
        start_flag = 1;
    if (stat(KDUMP_FILE_NAME, &info) == 0)
        file_flag = 1;
    if (stat(KDUMP_FINISH_FLAG, &info) == 0)
        finish_flag = 1;
    if (finish_flag == 1) {
        curr_stat = 3;
        crashtype = KDUMP_CRASH;
    }
    if ((finish_flag == 0) && (file_flag == 1)) {
        curr_stat = 2;
        LOGE("%s: KDUMP hasn't finished, maybe disk full or write timeout!!!\n", __FUNCTION__);
        raise_infoerror("KDUMP don't finish, maybe disk full or write timeout", "DONTF");
        return -1;
    }
    if ((finish_flag == 0) && (file_flag == 0) && (start_flag == 1)) {
        curr_stat = 1;
        LOGE("%s: KDUMP only enter, maybe user shutdown!!!\n", __FUNCTION__);
        raise_infoerror("KDUMP only enter, maybe user shutdown", "ENTER");
        return -1;
    }

    if ((curr_stat == 3) || (test == 1)) {

        dir = find_new_crashlog_dir(MODE_KDUMP);
        if (dir < 0 && crashtype != NULL) {
            LOGE("%s: Cannot get a valid new crash directory...\n", __FUNCTION__);
            key = raise_event(CRASHEVENT, crashtype, NULL, NULL);
            LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), crashtype);
            free(key);
            return -1;
        }

        if (curr_stat == 2 || curr_stat == 3) {
            destination[0] = '\0';
            snprintf(destination, sizeof(destination), "%s%d/%s_%s.core",
                    KDUMP_CRASH_DIR, dir, "kdumpfile", dateshort);
            do_mv(KDUMP_FILE_NAME, destination);
        }
        if(crashtype != NULL){
            /* Copy aplogs to KDUMP crash directory */
            do_log_copy(crashtype, dir, dateshort, KDUMP_TYPE);
        }
        destination[0] = '\0';
        snprintf(destination, sizeof(destination), "%s%d/", KDUMP_CRASH_DIR, dir);
		if(crashtype != NULL){
            key = raise_event(CRASHEVENT, crashtype, NULL, destination);
            LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), crashtype, destination);
            free(key);
		}

        remove(KDUMP_START_FLAG);
        remove(KDUMP_FILE_NAME);
        remove(KDUMP_FINISH_FLAG);
    }
    return 0;
}

/**
* @brief checks for IPANIC events relying on files exposed by emmc-ipanic and ram
* console modules.
*
* @param[in] reason   : start-up reason
* @param[in] watchdog : watchdog timer type
*
* @retval returns 'void'
*/
void crashlog_check_panic_events(char *reason, char *watchdog, int test) {

    if (crashlog_check_panic(reason, test) == 1)
        /* No panic console file : check RAM console to determine the watchdog event type */
        if ( crashlog_check_ram_panic(reason) == 1 && strstr(reason, "SWWDT_") )
            strcpy(watchdog, "WDT_UNHANDLED");
}

int process_kpanic_dmesg_event(struct watch_entry *entry,
                               struct inotify_event *event) {
    char gunzip_command[4096];
    char reason[256] = "SWWDT_RESET";

    if (manage_duplicate_dropbox_events(event))
        return 1;

    if (strchr(event->name, '\''))
        return -1;

    snprintf(gunzip_command, sizeof(gunzip_command), "gzip -d < '%s/%s' > '%s'",
             DROPBOX_DIR, event->name, SAVED_PANIC_RAM);

    if (system(gunzip_command)) {
        LOGE("%s: Could not gunzip", __FUNCTION__);
        return -1;
    }

    return crashlog_check_console_file(reason);
}
