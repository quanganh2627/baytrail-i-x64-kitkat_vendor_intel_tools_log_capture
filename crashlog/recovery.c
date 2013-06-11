#include "crashutils.h"
#include "fsutils.h"
#include "privconfig.h"

#include <stdlib.h>

int crashlog_check_recovery() {
    char destination[PATHMAX];
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
    destination[0] = '\0';
    snprintf(destination, sizeof(destination), "%s%d/%s", CRASH_DIR, dir, "recovery_last_log");
    if (do_copy(RECOVERY_ERROR_LOG, destination, MAXFILESIZE) < 0)
        LOGE("%s: %s copy failed", __FUNCTION__, RECOVERY_ERROR_LOG);
    do_last_kmsg_copy(dir);
    destination[0] = '\0';
    snprintf(destination, sizeof(destination), "%s%d/", CRASH_DIR, dir);
    key = raise_event(CRASHEVENT, RECOVERY_ERROR, NULL, destination);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), RECOVERY_ERROR, destination);
    remove(RECOVERY_ERROR_TRIGGER);
    free(key);

    return 0;
}
