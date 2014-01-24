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
#include "fsutils.h"
#include "privconfig.h"
#include "inotify_handler.h"
#include "trigger.h"
#include "ramdump.h"

#include <stdlib.h>

typedef enum e_crashtype_mode {
    EMMC_PANIC_MODE = 0, /*< computation of panic crashtype */
    RAM_PANIC_MODE, /*< computation of ram panic crashtype */
} e_crashtype_mode_t;

int cfg_check_ram_panic = 1;

/*
* Name          : check_aplogs_tobackup
* Description   : backup a number of aplogs if a patten is found in a file
* Parameters    :
*   char *filename        -> filename where a pattern is searched
*/
static int check_aplogs_tobackup(char *filename) {
    char ipanic_chain[PROPERTY_VALUE_MAX];
    int nbpatterns, res;
    char **patterns_array_32;
    char **patterns_array_64;
    char *SGX_64_pattern[] = {"SGXInitialise"};
    int idx, nbrecords = 10, recordsize = PROPERTY_VALUE_MAX;

    if (property_get(PROP_IPANIC_PATTERN, ipanic_chain, "") > 0) {
        /* Found the property, split it into an array */
        patterns_array_32 = commachain_to_fixedarray(ipanic_chain, recordsize, nbrecords, &nbpatterns);
        if (nbpatterns < 0 ) {
            LOGE("%s: Cannot transform the property %s(which is %s) into an array... error is %d - %s\n",
                __FUNCTION__, PROP_IPANIC_PATTERN, ipanic_chain, nbpatterns, strerror(-nbpatterns));
            for (idx = 0 ; idx < nbrecords ; idx++) {
                free(patterns_array_32[idx]);
            }
            free(patterns_array_32);
            return 0;
        }
        if ( nbpatterns == 0 ) return 0;
        //pattern 64 bit is a copy of pattern 32 before transformation
        patterns_array_64 = (char**)malloc(nbrecords*sizeof(char*));
        /* Add the prepattern "EIP is at" to each of the patterns */
        for (idx = 0 ; idx < nbpatterns ; idx++) {
            //copy each item for 64 pattern
            patterns_array_64[idx] = (char*)malloc(recordsize*sizeof(char));
            strncpy(patterns_array_64[idx], patterns_array_32[idx], recordsize-1);
            char *prepattern = "EIP is at ";
            int prepatternlen = strlen(prepattern);
            memmove(&patterns_array_32[idx][prepatternlen], patterns_array_32[idx],
                MIN((int)strlen(patterns_array_32[idx])+1, PROPERTY_VALUE_MAX-prepatternlen));
            /* insure the chain is null terminated */
            patterns_array_32[idx][PROPERTY_VALUE_MAX-1] = 0;
            memcpy(patterns_array_32[idx], prepattern, prepatternlen);
        }
        res = (find_oneofstrings_in_file(filename, (char**)patterns_array_32, nbpatterns) ||
                find_oneofstrings_in_file_with_keyword(filename,  (char**)patterns_array_64, "RIP:", nbpatterns));
        if (res > 0){
            LOGE("%s: before process\n", __FUNCTION__);
            process_log_event(NULL, NULL, MODE_APLOGS);
        }
        /* Cleanup the patterns_array allocated in commchain... */
        for (idx = 0 ; idx < nbrecords ; idx++) {
            free(patterns_array_32[idx]);
        }
        // pattern_64 is based on nbpatterns size
        for (idx = 0 ; idx < nbpatterns ; idx++) {
            free(patterns_array_64[idx]);
        }
        free(patterns_array_32);
        free(patterns_array_64);
    }
    else {
        /* By default, searches for the single following pattern... */
        res = (find_str_in_file(filename, "EIP is at SGXInitialise", NULL) ||
                find_oneofstrings_in_file_with_keyword(filename, SGX_64_pattern, "RIP:", 1));
        if (res > 0)
            process_log_event(NULL, NULL, MODE_APLOGS);
    }

    return res;
}

static void set_ipanic_crashtype_and_reason(char *console_name, char *crashtype, char *reason,
        e_crashtype_mode_t mode) {
    char *key;
    char *fake_32_pattern[] = {"EIP is at panic_dbg_set", "EIP is at kwd_trigger_open", "EIP is at kwd_trigger_write"};
    char *fake_64_pattern[] = {"panic_dbg_set", "kwd_trigger_write"};
    char *hwwdt_64_pattern[] = {"pmu_sc_irq"};
    const int size_fake_32_pattern = ( sizeof (fake_32_pattern) ) / ( sizeof fake_32_pattern[0] );
    const int size_fake_64_pattern = ( sizeof (fake_64_pattern) ) / ( sizeof fake_64_pattern[0] );
    const int size_hwwdt_64_pattern = ( sizeof (hwwdt_64_pattern) ) / ( sizeof hwwdt_64_pattern[0] );

    /* Set crash type according to pattern found in Ipanic console file or according to startup reason value*/
    if (find_str_in_file(console_name, "Kernel panic - not syncing: Kernel Watchdog", NULL) > 0) {
        strcpy(crashtype, KERNEL_SWWDT_CRASH);
        if (find_str_in_file(console_name, "[SHTDWN] WATCHDOG TIMEOUT for test!", NULL) > 0)
            strcpy(crashtype, KERNEL_SWWDT_FAKE_CRASH);
    }
    else if (find_str_in_file(console_name, "EIP is at pmu_sc_irq", NULL) > 0 ||
            (find_oneofstrings_in_file_with_keyword(console_name, hwwdt_64_pattern, "RIP:", size_hwwdt_64_pattern) > 0))
        // This panic is triggered by a fabric error
        // It is marked as a kernel panic linked to a HW watdchog
        // to create a link between these 2 critical crashes
        strcpy(crashtype, KERNEL_HWWDT_CRASH);
    else if ((find_oneofstrings_in_file(console_name, fake_32_pattern,size_fake_32_pattern) > 0) ||
            (find_oneofstrings_in_file_with_keyword(console_name, fake_64_pattern, "RIP:", size_fake_64_pattern) > 0))
        strcpy(crashtype, KERNEL_FAKE_CRASH);
    else
        strcpy(crashtype, KERNEL_CRASH);

    if ((mode == EMMC_PANIC_MODE) &&
            (find_str_in_file(console_name, "power_up_host: host controller power up is done", NULL) <= 0)) {
        // An error is raised when the panic console file does not end normally
       raise_infoerror(ERROREVENT, IPANIC_CORRUPTED);
    }
    if ((!strncmp(crashtype, KERNEL_FAKE_CRASH, sizeof(KERNEL_FAKE_CRASH))) ||
       (!strncmp(crashtype, KERNEL_SWWDT_FAKE_CRASH, sizeof(KERNEL_SWWDT_FAKE_CRASH))))
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
    char crash_path[PATHMAX] = {'\0'};
    char destination_tmp_name[PATHMAX] = {'\0'};
    char crash_console_name[PATHMAX] = {'\0'};
    char crashtype[32] = {'\0'};
    int dir;
    int copy_to_crash = 0, copy_to_panic = 0;
    const char *dateshort = get_current_time_short(1);
    char *key;

    if ( !test && !file_exists(CURRENT_PANIC_CONSOLE_NAME) ) {
        /* Nothing to do */
        return 1;
    }

    dir = find_new_crashlog_dir(MODE_CRASH);
    copy_to_crash = (dir >= 0);
    copy_to_panic = dir_exists(PANIC_DIR);

    if (copy_to_crash) {
        // use crash file directly
        snprintf(crash_path, sizeof(crash_path), "%s%d/", CRASH_DIR, dir);

        // Not created here...?
        snprintf(destination_tmp_name, sizeof(destination_tmp_name),
                    "%s%s_%s.txt", crash_path, LOGCAT_NAME, dateshort);
        do_copy(SAVED_LOGCAT_NAME, destination_tmp_name, MAXFILESIZE);

        // OPTIMIZE: Copy without dateshort to save proxy arrays
        snprintf(destination_tmp_name, sizeof(destination_tmp_name),
                    "%s%s_%s.txt", crash_path, EMMC_HEADER_NAME, dateshort);
        do_copy_eof(PANIC_HEADER_NAME, destination_tmp_name);

        snprintf(crash_console_name, sizeof(crash_console_name),
                    "%s%s_%s.txt", crash_path, CONSOLE_NAME, dateshort);
        do_copy_eof(PANIC_CONSOLE_NAME, crash_console_name);

        snprintf(destination_tmp_name, sizeof(destination_tmp_name),
                    "%s%s_%s.txt", crash_path, THREAD_NAME, dateshort);
        do_copy_eof(PANIC_THREAD_NAME, destination_tmp_name);

        snprintf(destination_tmp_name, sizeof(destination_tmp_name),
                    "%s%s_%s.bin", crash_path, GBUFFER_NAME, dateshort);
        do_copy_eof(PANIC_GBUFFER_NAME, destination_tmp_name);

        do_last_kmsg_copy(dir);
    } else {
        LOGE("%s: Cannot get a valid new crash directory...\n", __FUNCTION__);
    }

    // not exclusive with copy_to_crash
    if (copy_to_panic) {
        // copy to panic folder
        do_copy_eof(PANIC_HEADER_NAME, SAVED_HEADER_NAME);
        do_copy_eof(PANIC_CONSOLE_NAME, SAVED_CONSOLE_NAME);
        do_copy_eof(PANIC_THREAD_NAME, SAVED_THREAD_NAME);
        do_copy_eof(PANIC_GBUFFER_NAME, SAVED_GBUFFER_NAME);

        // Ram console (if available)
        snprintf(destination_tmp_name, sizeof(destination_tmp_name),
            "%s/%s.txt", PANIC_DIR, CONSOLE_RAMOOPS_FILE);
        if (file_exists(LAST_KMSG)) {
            do_copy_tail(LAST_KMSG, destination_tmp_name, MAXFILESIZE);
        } else if (file_exists(CONSOLE_RAMOOPS)) {
            do_copy_tail(CONSOLE_RAMOOPS, destination_tmp_name, MAXFILESIZE);
        }

        // saved file to use for processing
        snprintf(crash_console_name, sizeof(crash_console_name),
                    "%s", SAVED_CONSOLE_NAME);
    }

    if (!copy_to_crash && !copy_to_panic) {
        // use temp file
        snprintf(crash_console_name, sizeof(crash_console_name),
                    "%s", LOG_PANICTEMP);
        do_copy_eof(PANIC_CONSOLE_NAME, LOG_PANICTEMP);
    }

    //crashtype calculation should be done after CONSOLE_NAME computation
    set_ipanic_crashtype_and_reason(crash_console_name, crashtype, reason, EMMC_PANIC_MODE);

    if (copy_to_crash) {
        key = raise_event(CRASHEVENT, crashtype, NULL, crash_path);
        LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), crashtype, crash_path);
        free(key);

        // if a pattern is found in the console file, upload a large number of aplogs
        // property persist.crashlogd.panic.pattern is used to fill the list of pattern
        // Each pattern is split by a semicolon in the property
        check_aplogs_tobackup(crash_console_name);
        return 0;
    } else {
        key = raise_event(CRASHEVENT, crashtype, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), crashtype);
        free(key);
        /* Remove temporary files */
        remove(LOG_PANICTEMP);
        return -1;
    }
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
int crashlog_check_ram_panic(char *reason) {
    char crash_path[PATHMAX] = {'\0'};
    char crash_ramconsole_name[PATHMAX] = {'\0'};
    char crash_header_name[PATHMAX] = {'\0'};
    char ram_console[PATHMAX] = {'\0'};
    char crashtype[32] = {'\0'};
    int dir;
    int copy_to_crash = 0, copy_to_panic = 0;
    const char *dateshort = get_current_time_short(1);
    char *key;

    if (file_exists(LAST_KMSG)) {
        strcpy(ram_console, LAST_KMSG);
    } else if (file_exists(CONSOLE_RAMOOPS)) {
        strcpy(ram_console, CONSOLE_RAMOOPS);
    } else {
        // no file found, should return
        return 1;
    }

    if (find_str_in_file(ram_console, "Kernel panic - not syncing:", NULL) <= 0) {
        return 1; /* Not a PANIC : return */
    }

    dir = find_new_crashlog_dir(MODE_CRASH);

    copy_to_crash = (dir >= 0);
    copy_to_panic = dir_exists(PANIC_DIR);

    if (copy_to_crash) {
        // use crash file directly
        snprintf(crash_path, sizeof(crash_path), "%s%d/", CRASH_DIR, dir);

        snprintf(crash_header_name, sizeof(crash_header_name),
                    "%s%s_%s.txt", crash_path, EMMC_HEADER_NAME, dateshort);
        do_copy_eof(PANIC_HEADER_NAME, crash_header_name);

        snprintf(crash_ramconsole_name, sizeof(crash_ramconsole_name),
            "%s%s_%s.txt", crash_path, CONSOLE_RAMOOPS_FILE, dateshort);
        // to be homogeneous with do_last_kmsg_copy, we use do_copy_tail
        do_copy_tail(ram_console, crash_ramconsole_name, MAXFILESIZE);
    } else {
        LOGE("%s: Cannot get a valid new crash directory...\n", __FUNCTION__);
    }

    // NOT exclusive with copy_to_crash
    if (copy_to_panic) {
        snprintf(crash_header_name, sizeof(crash_header_name),
                    "%s", SAVED_HEADER_NAME);
        do_copy_eof(PANIC_HEADER_NAME, crash_header_name);

        // saved file to use for processing
        snprintf(crash_ramconsole_name, sizeof(crash_ramconsole_name),
            "%s/%s.txt", PANIC_DIR, CONSOLE_RAMOOPS_FILE);
        // to be homogeneous with do_last_kmsg_copy, we use do_copy_tail
        do_copy_tail(ram_console, crash_ramconsole_name, MAXFILESIZE);
    }

    // exclusive
    if (!copy_to_crash && !copy_to_panic) {
        // use temp file
        snprintf(crash_ramconsole_name, sizeof(crash_ramconsole_name),
                    "%s", LOG_PANICTEMP);
        // to be homogeneous with do_last_kmsg_copy, we use do_copy_tail
        do_copy_tail(ram_console, crash_ramconsole_name, MAXFILESIZE);
    }

    //crashtype calculation should be done after RAM_CONSOLE computation
    set_ipanic_crashtype_and_reason(crash_ramconsole_name, crashtype, reason, RAM_PANIC_MODE);

    // if a pattern is found in the console file, upload a large number of aplogs
    // property persist.crashlogd.panic.pattern is used to fill the list of pattern
    // Each pattern is split by a semicolon in the property
    check_aplogs_tobackup(crash_ramconsole_name);

    if (copy_to_crash) {
        key = raise_event(CRASHEVENT, crashtype, NULL, crash_path);
        LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), crashtype, crash_path);
        free(key);

        return 0;
    } else {
        key = raise_event(CRASHEVENT, crashtype, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), crashtype);
        free(key);
        /* Remove temporary file */
        remove(LOG_PANICTEMP);
        return -1;
    }
}

/**
 * @brief Checks in PANIC header if previous solutions failed
 *
 * This functions is called at reboot (ie at crashlogd boot ) and checks if a
 * PANIC event occurred by checking the panic partition header.
 * It then sets the IPANIC event type.
 * Returned values are :
 *  -1 if a problem occurs (can't create crash dir)
 *   0 for nominal case
 *   1 if the panic header doesn't exist or if it exists but no panic detected.
 */
int crashlog_check_panic_header(char *reason) {
    char crash_path[PATHMAX] = {'\0'};
    char crash_header_name[PATHMAX] = {'\0'};
    char destination_tmp_name[PATHMAX] = {'\0'};
    char crashtype[32] = {'\0'};
    int dir;
    int copy_to_crash = 0, copy_to_panic = 0;
    const char *dateshort = get_current_time_short(1);
    char *key;

    if ( !file_exists(CURRENT_PANIC_HEADER_NAME) ) {
        /* Nothing to do */
        return 1;
    }

    dir = find_new_crashlog_dir(MODE_CRASH);

    copy_to_crash = (dir >= 0);
    copy_to_panic = dir_exists(PANIC_DIR);

    if (copy_to_crash) {
        // use crash file directly
        snprintf(crash_path, sizeof(crash_path), "%s%d/", CRASH_DIR, dir);

        snprintf(crash_header_name, sizeof(crash_header_name),
                    "%s%s_%s.txt", crash_path, EMMC_HEADER_NAME, dateshort);
        do_copy_eof(PANIC_HEADER_NAME, crash_header_name);

        do_last_kmsg_copy(dir);
    }

    // NOT exclusive with copy_to_crash
    if (copy_to_panic) {
        // Ram console (if available, even if no panic-pattern)
        snprintf(destination_tmp_name, sizeof(destination_tmp_name),
            "%s/%s.txt", PANIC_DIR, CONSOLE_RAMOOPS_FILE);
        if (file_exists(LAST_KMSG)) {
            do_copy_tail(LAST_KMSG, destination_tmp_name, MAXFILESIZE);
        } else if (file_exists(CONSOLE_RAMOOPS)) {
            do_copy_tail(CONSOLE_RAMOOPS, destination_tmp_name, MAXFILESIZE);
        }

        // saved file to use for processing
        snprintf(crash_header_name, sizeof(crash_header_name),
                    "%s", SAVED_HEADER_NAME);
        do_copy_eof(PANIC_HEADER_NAME, crash_header_name);
    }

    // exclusive
    if (!copy_to_crash && !copy_to_panic) {
        // use temp file
        snprintf(crash_header_name, sizeof(crash_header_name),
                    "%s", LOG_PANICTEMP);
        do_copy_eof(PANIC_HEADER_NAME, crash_header_name);
    }

    //crashtype calculation should be done after HEADER_NAME computation
    set_ipanic_crashtype_and_reason(crash_header_name, crashtype, reason, EMMC_PANIC_MODE);

    if (copy_to_crash) {
        key = raise_event(CRASHEVENT, crashtype, NULL, crash_path);
        LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), crashtype, crash_path);
        free(key);

        return 0;
    } else {
        key = raise_event(CRASHEVENT, crashtype, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), crashtype);
        free(key);
        /* Remove temporary file */
        remove(LOG_PANICTEMP);
        return -1;
    }
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
    char destination[PATHMAX] = {'\0'};
    int dir;
    char *key;
    const char *dateshort = get_current_time_short(1);

    if (file_exists(KDUMP_START_FLAG))
        start_flag = 1;
    if (file_exists(KDUMP_FILE_NAME))
        file_flag = 1;
    if (file_exists(KDUMP_FINISH_FLAG))
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
        if (dir < 0) {
            LOGE("%s: Cannot get a valid new crash directory...\n", __FUNCTION__);
            key = raise_event(CRASHEVENT, crashtype, NULL, NULL);
            LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), crashtype);
            free(key);
            return -1;
        }

        if (curr_stat == 2 || curr_stat == 3) {
            snprintf(destination, sizeof(destination), "%s%d/%s_%s.core",
                    KDUMP_CRASH_DIR, dir, "kdumpfile", dateshort);
            do_mv(KDUMP_FILE_NAME, destination);
        }

        /* Copy aplogs to KDUMP crash directory */
        do_log_copy(crashtype, dir, dateshort, KDUMP_TYPE);

        snprintf(destination, sizeof(destination), "%s%d/", KDUMP_CRASH_DIR, dir);
        key = raise_event(CRASHEVENT, crashtype, NULL, destination);
        LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), crashtype, destination);
        free(key);

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
        if (crashlog_check_ram_panic(reason) == 1)
            /* Last resort: copy the panic partition header */
            if ( (crashlog_check_panic_header(reason) == 1) &&
                    strstr(reason, "SWWDT_") &&
                    (cfg_check_ram_panic == 1) )
                strcpy(watchdog, "WDT_UNHANDLED");

    /* Clears /proc/emmc_ipanic* entries */
    overwrite_file(CURRENT_PANIC_HEADER_NAME, "1");
}
