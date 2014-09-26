/* Copyright (C) Intel 2010
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
 * @file anruiwdt.c
 * @brief File containing functions for anr and uiwdt events processing.
 *
 * This file contains functions used to process ANR and UIWDT events.
 */

#include <sys/sendfile.h>
#include <sys/inotify.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <string.h>
#include <stdlib.h>
#include <fcntl.h>
#include <stdio.h>

#include <cutils/properties.h>
#ifdef FULL_REPORT
#ifdef CRASHLOGD_MODULE_BACKTRACE
#include <backtrace.h>
#endif
#endif

#include "crashutils.h"
#include "privconfig.h"
#include "anruiwdt.h"
#include "dropbox.h"
#include "fsutils.h"

#ifdef FULL_REPORT
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
        if (do_chown(destion, PERM_USER, PERM_GROUP)!=0) {
            LOGE("%s: do_chown failed : status=%s...\n", __FUNCTION__, strerror(errno));}
    }
}
#else
static void priv_prepare_anruiwdt(char *destion)
{
    char cmd[PATHMAX];
    int len = strlen(destion);
    if (len < 4) return;

    if ( destion[len-3] == '.' && destion[len-2] == 'g' && destion[len-1] == 'z') {
        /* extract gzip file */
        do_copy_tail(destion, LOGS_DIR "/tmp_anr_uiwdt.gz",0);
        system("gunzip " LOGS_DIR "/tmp_anr_uiwdt.gz");
        do_chown(LOGS_DIR "/tmp_anr_uiwdt", PERM_USER, PERM_GROUP);
        destion[strlen(destion) - 3] = 0;
        do_copy_tail(LOGS_DIR "/tmp_anr_uiwdt",destion,0);
        remove(LOGS_DIR "/tmp_anr_uiwdt");
    }
}
#endif

#ifdef FULL_REPORT
static void process_anruiwdt_tracefile(char *destion, int dir, int removeunparsed)
{
    char cmd[PATHMAX];
    int src, dest;
    char dest_path[PATHMAX] = {'\0'};
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
            if (!strlen(tracefile)) {
                LOGE("%s: Found lookup pattern, but without tracefile\n", __FUNCTION__);
                break;
            }
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
#ifdef CRASHLOGD_MODULE_BACKTRACE
            backtrace_parse_tombstone_file(dest_path);
#else
            LOGW("%s: CRASHLOGD_MODULE_BACKTRACE disabled, no tombstone backtrace parsing\n", __FUNCTION__);
#endif
            if ( removeunparsed && unlink(dest_path)) {
                LOGE("Failed to remove unparsed tracefile %s:%s\n", dest_path, strerror(errno));
            }
            break;
        }
    }
    fclose(fp);

    if (dest_path[0] == '\0') {
        LOGE("%s: Destination path not set\n", __FUNCTION__);
        return;
    }
    do_chown(dest_path, PERM_USER, PERM_GROUP);
    snprintf(dest_path_symb, sizeof(dest_path_symb), "%s_symbol", dest_path);
    do_chown(dest_path_symb, PERM_USER, PERM_GROUP);
}
#endif

static void backtrace_anruiwdt(char *dest, int dir) {
#ifdef FULL_REPORT
    char value[PROPERTY_VALUE_MAX];

    property_get(PROP_ANR_USERSTACK, value, "0");
    if (strncmp(value, "1", 1)) {
        process_anruiwdt_tracefile(dest, dir, 0);
    }
#endif
}

#define PATH_LENGTH			256
void do_copy_pvr(char * src, char * dest) {
   char *token = NULL;
   char *str = NULL;
   char buf[PATH_LENGTH] = {0, };
   char path[PATH_LENGTH] = {0, };
   FILE * fs = NULL;
   FILE * fd = NULL;
   fs = fopen(src,"r");
   fd = fopen(dest,"w");
   if (fs && fd) {
		while(fgets(buf, PATH_LENGTH, fs)) {
		    fputs(buf ,fd);
		 }
   }
   if (fs)
      fclose(fs);
   if (fd)
      fclose(fd);

   return;
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

    /* Check for duplicate dropbox event first */
    if ( manage_duplicate_dropbox_events(event) )
        return 1;

    dir = find_new_crashlog_dir(MODE_CRASH);
    snprintf(destion,sizeof(destion),"%s%d/%s", CRASH_DIR, dir, "pvr_debug_dump.txt");
    do_copy_pvr("/d/pvr/debug_dump", destion);
    snprintf(destion,sizeof(destion),"%s%d/%s", CRASH_DIR, dir, "fence_sync.txt");
    do_copy_pvr("/d/sync", destion);
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
    snprintf(destion, sizeof(destion), "%s%d", CRASH_DIR, dir);
    key = raise_event(CRASHEVENT, entry->eventname, NULL, destion);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), entry->eventname, destion);
    switch (entry->eventtype) {
#ifdef FULL_REPORT
        case ANR_TYPE:
            if (start_dumpstate_srv(CRASH_DIR, dir, key) <= 0) {
                /* Finally raise the event as the dumpstate server is busy or failed to be started */
                free(key);
            }
            break;
#endif
        default:
            free(key);
            break;
    }
    return 1;
}
