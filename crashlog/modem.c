#include "inotify_handler.h"
#include "crashutils.h"
#include "fsutils.h"
#include "config.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <stdlib.h>

static int copy_modemcoredump(char *spath, char *dpath) {
    char src[PATHMAX];
    char des[PATHMAX];
    struct stat st;
    DIR *d;
    struct dirent *de;

    if (stat(spath, &st))
        return -errno;
    if (stat(dpath, &st))
        return -errno;

    src[0] = 0;
    des[0] = 0;

    d = opendir(spath);
    if (d == 0) {
        LOGE("%s: opendir failed - %s\n", __FUNCTION__, strerror(errno));
        return -errno;
    }
    while ((de = readdir(d)) != 0) {
        if (!strcmp(de->d_name, ".") || !strcmp(de->d_name, ".."))
            continue;
        if (de->d_name[0] == 'c' && de->d_name[1] == 'd'
                && strstr(de->d_name, ".tar.gz")) {
            /* file form is cd_xxx.tar.gz */
            snprintf(src, sizeof(src), "%s/%s", spath, de->d_name);
            snprintf(des, sizeof(des), "%s/%s", dpath, de->d_name);
            do_copy_tail(src, des, 0);
            remove(src);
        }
    }
    if (closedir(d) < 0){
        LOGE("%s: closedir failed - %s\n", __FUNCTION__, strerror(errno));
        return -errno;
    }
    return 0;

}

int process_modem_event(struct watch_entry *entry, struct inotify_event *event) {
    int dir;
    char path[PATHMAX];
    char destion[PATHMAX];
    const char *dateshort = get_current_time_short(1);
    char *key;

    snprintf(path, sizeof(path),"%s/%s", entry->eventpath, event->name);
    dir = find_new_crashlog_dir(CRASH_MODE);
    if (dir < 0) {
        LOGE("%s: find_new_crashlog_dir failed\n", __FUNCTION__);
        key = raise_event(CRASHEVENT, entry->eventname, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), entry->eventname);
        rmfr(path);
        free(key);
        return -1;
    }

    snprintf(destion,sizeof(destion),"%s%d", CRASH_DIR,dir);
    int status = copy_modemcoredump(entry->eventpath, destion);
    if (status != 0)
        LOGE("backup modem core dump status: %d.\n", status);
    snprintf(destion,sizeof(destion),"%s%d/", CRASH_DIR, dir);

    usleep(TIMEOUT_VALUE);
    do_log_copy(entry->eventname, dir, dateshort, APLOG_TYPE);
    do_log_copy(entry->eventname, dir, dateshort, BPLOG_TYPE);
    key = raise_event(CRASHEVENT, entry->eventname, NULL, destion);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), entry->eventname, destion);
    rmfr(path);
    free(key);
    return 0;
}

int crashlog_check_modem_shutdown() {
    const char *dateshort = get_current_time_short(1);
    char destion[PATHMAX];
    int dir;
    char *key;

    if ( !file_exists(MODEM_SHUTDOWN_TRIGGER) ) {
        /* Nothing to do */
        return 0;
    }

    dir = find_new_crashlog_dir(CRASH_MODE);
    if (dir < 0) {
        LOGE("%s: find_new_crashlog_dir failed\n", __FUNCTION__);
        key = raise_event(CRASHEVENT, MODEM_SHUTDOWN, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), MODEM_SHUTDOWN);
        remove(MODEM_SHUTDOWN_TRIGGER);
        free(key);
        return -1;
    }

    destion[0] = '\0';
    snprintf(destion, sizeof(destion), "%s%d/", CRASH_DIR, dir);
    key = raise_event(CRASHEVENT, MODEM_SHUTDOWN, NULL, destion);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), MODEM_SHUTDOWN, destion);
    usleep(TIMEOUT_VALUE);
    do_log_copy(MODEM_SHUTDOWN, dir, dateshort, APLOG_TYPE);
    do_last_kmsg_copy(dir);
    free(key);
    remove(MODEM_SHUTDOWN_TRIGGER);

    return 0;
}
