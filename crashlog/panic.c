#include "crashutils.h"
#include "fsutils.h"
#include "privconfig.h"

#include <stdlib.h>

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
        /* Cleanup the patterns_array allocated in commchain... */
        for (idx = 0 ; idx < nbrecords ; idx++) {
            free(patterns_array[idx]);
        }
        free(patterns_array);
    }
    else {
        /* By default, searches for the single following pattern... */
        res = find_str_in_file(filename, "EIP is at SGXInitialise", NULL);
    }

    return res;
}

static void set_ipanic_crashtype_and_reason(char *crashtype, char *reason) {
    char *key;

    /* Set crash type according to pattern found in Ipanic console file or according to startup reason value*/
    if ( find_str_in_file(SAVED_CONSOLE_NAME, "Kernel panic - not syncing: Kernel Watchdog", NULL))
        strcpy(crashtype, KERNEL_SWWDT_CRASH);
    else if  ( find_str_in_file(SAVED_CONSOLE_NAME, "EIP is at pmu_sc_irq", NULL) || !strncmp(reason,"HWWDT_RESET", 11))
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

int crashlog_check_panic(char *reason, int test) {
    char destination[PATHMAX];
    char crashtype[32] = {'\0'};
    int dir;
    const char *dateshort = get_current_time_short(1);
    char *key;

    if ( !test && !file_exists(CURRENT_PANIC_CONSOLE_NAME) ) {
        /* Nothing to do */
        return 0;
    }

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
