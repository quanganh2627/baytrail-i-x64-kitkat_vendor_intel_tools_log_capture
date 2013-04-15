#include "crashutils.h"
#include "privconfig.h"
#include "fsutils.h"

#include <stdlib.h>

struct fabric_type {
    char *keyword;
    char *tail;
    char *name;
};

struct fabric_type fabric_types_array[] = {
    {"DW0:", "f501", "MEMERR"},
    {"DW0:", "f502", "INSTERR"},
    {"DW0:", "f504", "SRAMECCERR"},
    {"DW0:", "00dd", "HWWDTLOGERR"},
};

struct fabric_type fabric_fakes_array[] = {
    {"DW2:", "02608002", "FABRIC_FAKE"},
    {"DW3:", "40102ff4", "FABRIC_FAKE"},
};

int crashlog_check_fabric(char *reason, int test) {
    const char *dateshort = get_current_time_short(1);
    char destination[PATHMAX];
    char crashtype[32] = {'\0'};
    int dir;
    unsigned int i = 0;
    char *key;

    if ( !test && !file_exists(CURRENT_PROC_FABRIC_ERROR_NAME) ) return 0;

    /* Looks for fake fabrics */
    for (i = 0; i < DIM(fabric_fakes_array); i++) {
        if ( find_str_in_file(SAVED_FABRIC_ERROR_NAME, fabric_fakes_array[i].keyword, fabric_fakes_array[i].tail) > 0 ) {
            /* Got it, it's a fake!! */
            strncpy(crashtype, fabric_fakes_array[i].name, sizeof(crashtype)-1);
            if (!strncmp(reason, "HWWDT_RESET", strlen("HWWDT_RESET")))
                strcat(reason,"_FAKE");
            break;
        }
    }

    if (crashtype[0] == 0) {
        /* Not a fake, checks for the type in the know fabrics list */
        for (i = 0; i < DIM(fabric_types_array); i++) {
            if ( find_str_in_file(SAVED_FABRIC_ERROR_NAME, fabric_types_array[i].keyword, fabric_types_array[i].tail) > 0 ) {
               /* Got it! */
               strncpy(crashtype, fabric_types_array[i].name, sizeof(crashtype)-1);
               break;
            }
        }
        if (crashtype[0] == 0) {
            /* Not a fake but still unknown!! set a default type */
            strcpy(crashtype, FABRIC_ERROR);
        }
    }

    dir = find_new_crashlog_dir(CRASH_MODE);
    if (dir < 0) {
        LOGE("%s: find_new_crashlog_dir failed\n", __FUNCTION__);
        key = raise_event(CRASHEVENT, crashtype, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), crashtype);
        free(key);
        return -1;
    }

    destination[0] = '\0';
    snprintf(destination, sizeof(destination), "%s%d/%s_%s.txt", CRASH_DIR, dir,
            FABRIC_ERROR_NAME, dateshort);
    do_copy(SAVED_FABRIC_ERROR_NAME, destination, MAXFILESIZE);
    do_last_kmsg_copy(dir);

    destination[0] = '\0';
    snprintf(destination,sizeof(destination),"%s%d/",CRASH_DIR,dir);
    key = raise_event(CRASHEVENT, crashtype, NULL, destination);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), crashtype, destination);
    free(key);
    return 0;
}
