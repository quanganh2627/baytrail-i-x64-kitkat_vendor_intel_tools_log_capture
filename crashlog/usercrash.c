#include "inotify_handler.h"
#include "crashutils.h"
#include "usercrash.h"
#include "dropbox.h"
#include "fsutils.h"

#include "cutils/log.h"
#include "sha1.h"

#include <stdlib.h>

static void backup_apcoredump(unsigned int dir, char* name, char* path) {

    char des[512] = { '\0', };
    snprintf(des, sizeof(des), "%s%d/%s", CRASH_DIR, dir, name);
    int status = do_copy_tail(path, des, 0);
    if (status != 0)
        LOGE("backup ap core dump status: %d.\n",status);
    else
        remove(path);
}

/*
* Name          : __process_usercrash_event
* Description   : processes hprof, apcoredump, javacrash and tombstones events
* Parameters    :
* entry: watch_entry which triggered the notification
* event: inotify_event received
*/
static int priv_process_usercrash_event(struct watch_entry *entry, struct inotify_event *event) {
    char path[PATHMAX];
    char destion[PATHMAX];
    char *key;
    int dir;

    dir = find_new_crashlog_dir(CRASH_MODE);
    snprintf(path, sizeof(path),"%s/%s", entry->eventpath, event->name);
    if (dir < 0 || !file_exists(path)) {
        if (dir < 0)
            LOGE("%s: Cannot get a valid new crash directory...\n", __FUNCTION__);
        else
            LOGE("%s: Cannot access %s\n", __FUNCTION__, path);
        key = raise_event(CRASHEVENT, entry->eventname, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), entry->eventname);
        free(key);
        return -1;
    }

    snprintf(destion,sizeof(destion),"%s%d/%s", CRASH_DIR, dir, event->name);
    do_copy_tail(path, destion, MAXFILESIZE);
    switch (entry->eventtype) {
        case APCORE_TYPE:
            backup_apcoredump(dir, event->name, path);
            break;
        case TOMBSTONE_TYPE:
        case JAVACRASH_TYPE:
            usleep(TIMEOUT_VALUE);
            do_log_copy(entry->eventname, dir, get_current_time_short(1), APLOG_TYPE);
            break;
        case HPROF_TYPE:
            break;
        default:
            LOGE("%s: Unexpected type of event(%d)\n", __FUNCTION__, entry->eventtype);
    }
    remove(path);
    snprintf(destion, sizeof(destion), "%s%d", CRASH_DIR, dir);
    key = raise_event(CRASHEVENT, entry->eventname, NULL, destion);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), entry->eventname, destion);
    if ( (entry->eventtype != TOMBSTONE_TYPE && entry->eventtype != JAVACRASH_TYPE)
        || !start_dumpstate_srv(CRASH_DIR, dir, key) ) {
        /*
         * Didn't start the dumpstate server
         * (already running or not necessary)
         */
        free(key);
    }
    return 1;
}

int process_usercrash_event(struct watch_entry *entry, struct inotify_event *event) {

    return priv_process_usercrash_event(entry , event);
}

int process_hprof_event(struct watch_entry *entry, struct inotify_event *event) {

    return priv_process_usercrash_event(entry , event);
}

int process_apcore_event(struct watch_entry *entry, struct inotify_event *event) {

    return priv_process_usercrash_event(entry , event);
}
