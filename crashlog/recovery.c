#include "crashutils.h"
#include "fsutils.h"
#include "config.h"

#include <stdlib.h>

int crashlog_check_recovery() {
    char destion[PATHMAX];
    int dir;
    char *key;

    //Check if trigger file exists
    if ( !file_exists(RECOVERY_ERROR_TRIGGER) ) {
        /* Nothing to do */
        return 0;
    }

    dir = find_new_crashlog_dir(CRASH_MODE);
    if (dir < 0) {
        LOGE("%s: Cannot get a valid new crash directory...\n", __FUNCTION__);
        key = raise_event(CRASHEVENT, RECOVERY_ERROR, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), RECOVERY_ERROR);
        remove(RECOVERY_ERROR_TRIGGER);
        free(key);
        return -1;
    }

    //copy log
    destion[0] = '\0';
    snprintf(destion, sizeof(destion), "%s%s", destion, "recovery_last_log");
    do_copy(RECOVERY_ERROR_LOG, destion, MAXFILESIZE);
    do_last_kmsg_copy(dir);
    destion[0] = '\0';
    snprintf(destion, sizeof(destion), "%s%d/", CRASH_DIR, dir);
    key = raise_event(CRASHEVENT, RECOVERY_ERROR, NULL, destion);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), RECOVERY_ERROR, destion);
    remove(RECOVERY_ERROR_TRIGGER);
    free(key);

    return 0;
}
