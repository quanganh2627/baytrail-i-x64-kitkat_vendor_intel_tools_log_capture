#include <sys/sendfile.h>
#include <sys/inotify.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <string.h>
#include <stdlib.h>
#include <fcntl.h>
#include <stdio.h>

#include <cutils/properties.h>
#include <backtrace.h>

#include "crashutils.h"
#include "privconfig.h"
#include "anruiwdt.h"
#include "dropbox.h"
#include "fsutils.h"

static void priv_prepare_anruiwdt(char *destion)
{
    char cmd[PATHMAX];
    int len = strlen(destion);
    if (len < 4) return;

    if ( destion[len-3] == '.' && destion[len-2] == 'g' && destion[len-1] == 'z') {
        /* extract gzip file */
        snprintf(cmd, sizeof(cmd), "gunzip %s", destion);
        system(cmd);
        destion[len-3] = 0;
        do_chown(destion, PERM_USER, PERM_GROUP);
    }
}

static void process_anruiwdt_tracefile(char *destion, int dir, int removeunparsed)
{
    char cmd[PATHMAX];
    int src, dest;
    char dest_path[PATHMAX];
    char dest_path_symb[PATHMAX];
    struct stat stat_buf;
    char *tracefile;
    FILE *fp;
    int i;

    fp = fopen(destion, "r");
    if (fp == NULL) {
        LOGE("%s: Failed to open file %s:%s\n", __FUNCTION__, destion, strerror(errno));
        return;
    }
    /* looking for "Trace file:" from the first 100 lines */
    for (i = 0; i < 100; i++) {
        if ( fgets(cmd, sizeof(cmd), fp) && !strncmp("Trace file:", cmd, 11) ) {
            tracefile = cmd + 11;
            tracefile[strlen(tracefile) - 1] = 0; /* eliminate trailing \n */
            if ( !file_exists(tracefile) ) {
                LOGE("%s: %s lists a trace file (%s) but it does not exist...\n", __FUNCTION__, destion, tracefile);
                break;
            }
            // copy
            src = open(tracefile, O_RDONLY);
            if (src < 0) {
                LOGE("%s: Failed to open trace file %s:%s\n", __FUNCTION__, tracefile, strerror(errno));
                break;
            }
            snprintf(dest_path, sizeof(dest_path), "%s%d/trace_all_stack.txt", CRASH_DIR, dir);
            fstat(src, &stat_buf);
            dest = open(dest_path, O_WRONLY|O_CREAT, 0600);
            if (dest < 0) {
                LOGE("%s: Failed to create dest file %s:%s\n", __FUNCTION__, dest_path, strerror(errno));
                close(src);
                break;
            }
            close(dest);
            do_chown(dest_path, PERM_USER, PERM_GROUP);
            dest = open(dest_path, O_WRONLY, stat_buf.st_mode);
            if (dest < 0) {
                LOGE("%s: Failed to open dest file %s after setting the access rights:%s\n", __FUNCTION__, dest_path, strerror(errno));
                close(src);
                break;
            }
            sendfile(dest, src, NULL, stat_buf.st_size);
            close(src);
            close(dest);
            // remove src file
            if (unlink(tracefile) != 0) {
                LOGE("%s: Failed to remove tracefile %s:%s\n", __FUNCTION__, tracefile, strerror(errno));
            }
            // parse
            backtrace_parse_tombstone_file(dest_path);
            if ( removeunparsed && unlink(dest_path)) {
                LOGE("Failed to remove unparsed tracefile %s:%s\n", dest_path, strerror(errno));
            }
            break;
        }
    }
    fclose(fp);
    do_chown(dest_path, PERM_USER, PERM_GROUP);
    snprintf(dest_path_symb, sizeof(dest_path_symb), "%s_symbol", dest_path);
    do_chown(dest_path_symb, PERM_USER, PERM_GROUP);
}

static void backtrace_anruiwdt(char *dest, int dir) {
    char value[PROPERTY_VALUE_MAX];

    property_get(PROP_ANR_USERSTACK, value, "0");
    if (strncmp(value, "1", 1)) {
        process_anruiwdt_tracefile(dest, dir, 0);
    }
}

/*
* Name          :
* Description   :
* Parameters    :
*/
int process_anruiwdt_event(struct watch_entry *entry, struct inotify_event *event) {
    char path[PATHMAX];
    char destion[PATHMAX];
    const char *dateshort = get_current_time_short(1);
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
    priv_prepare_anruiwdt(destion);
    usleep(TIMEOUT_VALUE);
    do_log_copy(entry->eventname, dir, dateshort, APLOG_TYPE);
    backtrace_anruiwdt(destion, dir);
    restart_profile_srv(1);
    remove(path);
    snprintf(destion, sizeof(destion), "%s%d", CRASH_DIR, dir);
    key = raise_event(CRASHEVENT, entry->eventname, NULL, destion);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), entry->eventname, destion);
    switch (entry->eventtype) {
        case ANR_TYPE:
            if (start_dumpstate_srv(CRASH_DIR, dir, key) <= 0) {
                /* Finally raise the event as the dumpstate server is busy or failed to be started */
                free(key);
            }
            break;
        default:
            free(key);
            break;
    }
    return 1;
}
