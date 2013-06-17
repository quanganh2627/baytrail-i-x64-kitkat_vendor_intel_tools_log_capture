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
 * @file history.c
 * @brief File containing every functions for operations on the history file and
 * for uptime management.
 *
 * This file contains the functions to handle the history file and the uptime event.
 * A circular buffer locally defined has its content synchronized on the history file
 * content.
 */

#include "crashutils.h"
#include "privconfig.h"
#include "history.h"
#include "fsutils.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <stdio.h>
#include <sys/sha1.h>

#define HISTORY_FIRST_LINE_FMT  "#V1.0 " UPTIME_EVNAME "   %-24s\n"
#define HISTORY_BLANK_LINE1     "#V1.0 " UPTIME_EVNAME "   0000:00:00              \n"
#define HISTORY_BLANK_LINE2     "#EVENT  ID                    DATE                 TYPE\n"

static char *historycache[MAX_RECORDS];
static int nextline = -1;
static int loop_uptime_event = 1;
/* last uptime value set at device boot only */
static char lastbootuptime[24] = "0000:00:00";
extern int gabortcleansd;
// global variable to enable dynamic change of uptime frequency
int gcurrent_uptime_hour_frequency = UPTIME_HOUR_FREQUENCY;

int get_lastboot_uptime(char bootuptime[24]) {
    if (lastbootuptime[0] != 0) {
        strncpy(bootuptime, lastbootuptime, 24);
        return 0;
    }
    return -1;
}

int get_uptime_string(char newuptime[24], int *hours) {
    long long tm;
    int seconds, minutes, res;
    tm = get_uptime(1, &res);
    if (res) return res;

    *hours = (int) (tm / 1000000000LL);
    seconds = *hours % 60; *hours /= 60;
    minutes = *hours % 60; *hours /= 60;
    errno = 0;
    return (snprintf(newuptime, 24, "%04d:%02d:%02d", *hours,
        minutes, seconds) == 3 ? 0 : -errno);
}

/**
* Name          : get_timed_firstline
* Description   : computes first line to be written in history file.
*                 returns 0 if success. errno if failure.
* Parameters    :
*  buffer     : the string containing the line well formatted
*  hours      : the number of elapsed hours since last boot
*  lastuptime : the time elapsed since last boot under well formatted string format
*  refresh    : flag to force refresh or to take last computed value
*/
static int get_timed_firstline(char *buffer, int *hours, char lastuptime[24], int refresh) {

    static int saved_hours = 0;
    static char saved_lastuptime[24] = {0,};
    /* to not refresh and already initialized */
    if (!refresh && saved_lastuptime[0] != 0) {
        *hours = saved_hours;
        lastuptime = saved_lastuptime;
        return (sprintf(buffer, HISTORY_FIRST_LINE_FMT, lastuptime) > 0 ? 0 : -errno);
    }
    /* not initialized yet or to refresh */
    if (get_uptime_string(lastuptime, hours) != 0)
        return -errno;

    if (sprintf(buffer, HISTORY_FIRST_LINE_FMT, lastuptime) > 0) {
        /* save to static variables for next call without refreshing */
        saved_hours = *hours;
        strcpy(saved_lastuptime, lastuptime);
        return 0;
    }
    else {
        /*Re-initialize to force refresh next time */
        saved_hours = 0;
        saved_lastuptime[0] = 0;
        return -errno;
    }
}

static int cache_history_file() {
    int res;
    if ( !file_exists(HISTORY_FILE) ) {
        char firstline[MAXLINESIZE];
        char lastuptime[24];
        int tmp;
        FILE *to = fopen(HISTORY_FILE, "w");
        if (to == NULL) return -errno;

        do_chmod(HISTORY_FILE, "644");
        do_chown(HISTORY_FILE, PERM_USER, PERM_GROUP);
        get_timed_firstline(firstline, &tmp, lastuptime, 1);
        fprintf(to, "%s", firstline);
        fprintf(to, HISTORY_BLANK_LINE2);
        fclose(to);
    }
    /* Cache the history file without the 2 first lines */
    res = cache_file(HISTORY_FILE, (char**)historycache, MAX_RECORDS, CACHE_TAIL, 2);

    if ( res < 0 ) {
        LOGE("%s: Cannot cache the contents of %s - %s.\n",
            __FUNCTION__, HISTORY_FILE, strerror(-res));
        return res;
    }
    nextline = res % MAX_RECORDS;
    return res;
}

int reset_history_cache() {
    if (nextline > 0) {
        int idx;
        /* delete the cache first */
        for (idx = 0 ; idx < MAX_RECORDS ; idx++)
            if (historycache[idx]) {
                free(historycache[idx]);
                historycache[idx] = NULL;
            }
    }
    return cache_history_file();
}

static void entry_to_history_line(struct history_entry *entry,
    char newline[MAXLINESIZE]) {
    newline[0] = 0;
    if (entry->log != NULL) {
        char *ptr;
        char tmp[MAXLINESIZE];
        strncpy(tmp, entry->log, MAXLINESIZE);
        ptr = strrchr(tmp,'/');
        if (ptr && ptr[1] == 0) ptr[0] = 0;
        snprintf(newline, MAXLINESIZE, "%-8s%-22s%-20s%s %s\n",
            entry->event, entry->key, entry->eventtime, entry->type, tmp);
    } else if (entry->type != NULL && entry->type[0]) {
        if (entry->lastuptime != NULL) {
            snprintf(newline, MAXLINESIZE, "%-8s%-22s%-20s%-16s %s\n",
                entry->event, entry->key, entry->eventtime, entry->type,
                entry->lastuptime);
            }
        else {
            snprintf(newline, MAXLINESIZE, "%-8s%-22s%-20s%-16s\n",
                entry->event, entry->key, entry->eventtime, entry->type);
        }
    } else {
        snprintf(newline, MAXLINESIZE, "%-8s%-22s%-20s%s\n",
            entry->event, entry->key, entry->eventtime, entry->lastuptime);
    }
}

int update_history_file(struct history_entry *entry) {

    /* historycache is a circular buffer indexed with next index */
    int index = 0, fd, res;
    char newline[MAXLINESIZE];
    char firstline[MAXLINESIZE];
    char lastuptime[24];
    int tmp;
    if (!entry || !entry->key ||
            !entry->eventtime)
        return -EINVAL;

    if ( !file_exists(HISTORY_FILE) && (res = reset_history_cache()) < 0 ) {
        LOGE("%s: Cannot reset history cache %s - %s.\n", __FUNCTION__,
            HISTORY_FILE, strerror(-res));
        return res;
    } else if ( nextline < 0 && (res = cache_history_file()) < 0  ) {
        LOGE("%s: Cannot cache %s - %s.\n", __FUNCTION__,
            HISTORY_FILE, strerror(-res));
        return res;
    }

    entry_to_history_line(entry, newline);

    if (newline[0] == 0) {
        LOGE("%s: Cannot build the history line for entry %s - %s.\n",
            __FUNCTION__, entry->key, strerror(errno));
        return -errno;
    }

    /* Check if the buffer is full */
    if ( historycache[nextline] == NULL ) {
        /* Still have some room */
        if ( (historycache[nextline] = strdup(newline)) == NULL) {
            newline[strlen(newline) - 1] = 0; /*Remove trailing character for display purpose*/
            LOGE("%s: Cannot copy the line %s - %s.\n", __FUNCTION__,
                newline, strerror(nextline));
            return -errno;
        }
        nextline = (nextline + 1) % MAX_RECORDS;
        /* We can just write the new line at the end of the file */
        res = append_file(HISTORY_FILE, newline);
        if (res > 0) return 0;
        newline[strlen(newline) - 1] = 0; /*Remove trailing character for display purpose*/
        LOGE("%s: Cannot append the line %s to %s- %s.\n", __FUNCTION__,
            newline, HISTORY_FILE, strerror(-res));
        return res;
    }

    /* The buffer is full */
    LOGD("%s : History cache buffer is full\n", __FUNCTION__);

    free(historycache[nextline]);
    if ( (historycache[nextline] = strdup(newline)) == NULL) {
        newline[strlen(newline) - 1] = 0; /*Remove trailing character for display purpose*/
        LOGE("%s: Cannot copy the line %s - %s.\n", __FUNCTION__,
            newline, strerror(nextline));
        return -errno;
    }
    nextline = (nextline + 1) % MAX_RECORDS;
    /* We need to recreate a new file and write the full buffer
     * costly!!!
     */
    fd = open(HISTORY_FILE, O_RDWR | O_TRUNC | O_CREAT, S_IRUSR | S_IWUSR);
    if (fd < 0) {
        LOGE("%s: Cannot create %s\n", HISTORY_FILE, strerror(errno));
        return -errno;
    }
    /* Write the two first lines : get 'lastuptime' last computed value */
    if ( get_timed_firstline(firstline, &tmp, lastuptime, 0) == 0 ) {
        if ( (write(fd, firstline, strlen(firstline))) != (int)strlen(firstline) ) {
            close(fd);
            return -errno;
        }
    }
    else {
        LOGE("%s: can't get timed first line for history file", __FUNCTION__);
        if ( write(fd, HISTORY_BLANK_LINE1, strlen(HISTORY_BLANK_LINE1)) != (int)strlen(HISTORY_BLANK_LINE1) ) {
           close(fd);
           return -errno;
       }
    }
    if ( write(fd, HISTORY_BLANK_LINE2, strlen(HISTORY_BLANK_LINE2)) != (int)strlen(HISTORY_BLANK_LINE2) ) {
       close(fd);
       return -errno;
   }

    /* Copy the buffer from nextline to the end */
    for (index = nextline ; index < MAX_RECORDS ; index++) {
        if (write(fd, historycache[index], strlen(historycache[index]))
            != (int)strlen(historycache[index]) ) {
            close(fd);
            return -errno;
        }
    }
    /* Copy the buffer from 0 to nextline */
    for (index = 0 ; index < nextline ; index++) {
        if (write(fd, historycache[index], strlen(historycache[index]))
            != (int)strlen(historycache[index]) ) {
            close(fd);
            return -errno;
        }
    }
    close(fd);
    return 0;
}

int uptime_history() {
    FILE *to;
    int res;
    char name[32];
    const char *datelong = get_current_time_long(1);
    to = fopen(HISTORY_FILE, "r");
    if (to == NULL) {
        res = errno;
        LOGE("%s: Cannot open %s - %s\n", __FUNCTION__,
            HISTORY_FILE, strerror(errno));
        return -res;
    }
    fscanf(to, "#V1.0 %16s%24s\n", name, lastbootuptime);
    fclose(to);
    if (memcmp(name, "CURRENTUPTIME", sizeof("CURRENTUPTIME"))) {
        LOGE("%s: Bad first line; cannot continue\n",
            __FUNCTION__);
        return -1;
    }

    to = fopen(HISTORY_FILE, "r+");
    if (to == NULL) {
        res = errno;
        LOGE("%s: Cannot reopen %s - %s\n", __FUNCTION__,
            HISTORY_FILE, strerror(errno));
        return -res;
    }
    fprintf(to, HISTORY_BLANK_LINE1);
    strcpy(name, PER_UPTIME);
    fseek(to, 0, SEEK_END);
    fprintf(to, "%-8s00000000000000000000  %-20s%s\n", name, datelong, lastbootuptime);
    fclose(to);
    return 0;
}

/**
* Name          : reset_uptime_history
* Description   : cache the history file, write the 2 first lines
*/
int reset_uptime_history() {
    FILE *to;
    if ( nextline < 0 && cache_history_file() < 0) {
        LOGE("%s: Cannot cache %s - %s.\n", __FUNCTION__,
            HISTORY_FILE, strerror(errno));
        return -errno;
    }

    to = fopen(HISTORY_FILE, "w");
    if (to == NULL) {
        LOGE("%s: Cannot open %s - %s.\n", __FUNCTION__,
            HISTORY_FILE, strerror(errno));
        return -errno;
    }
    fprintf(to, HISTORY_BLANK_LINE1);
    fprintf(to, HISTORY_BLANK_LINE2);
    fclose(to);

    /* create uptime file */
    to = fopen(HISTORY_UPTIME, "w");
    if (to == NULL) {
        LOGE("%s: Create/open %s error - %s\n", __FUNCTION__,
            HISTORY_UPTIME, strerror(errno));
        return -1;
    }
    fclose(to);
    return 0;
}

int history_has_event(char *eventdir) {

    int idx;
    if (!eventdir) return -EINVAL;

    if ( nextline < 0 && cache_history_file() < 0) {
        LOGE("%s: Cannot cache %s - %s.\n", __FUNCTION__,
            HISTORY_FILE, strerror(errno));
        return -errno;
    }

    for (idx = 0 ; idx < MAX_RECORDS ; idx ++) {
        if (historycache[idx] &&
                strstr(historycache[idx], eventdir) != NULL)
            return 1;
    }
    return 0;
}

/*
* Name          : add_uptime_event
* Description   : adds an uptime event to the history file and upload an event if 12hours passed
* Parameters    :
*/
int add_uptime_event() {
    int res, hours;
    FILE *fd;
    char firstline[MAXLINESIZE];
    char lastuptime[24];
    fd = fopen(HISTORY_FILE, "r+");
    if (fd == NULL) return -errno;

    res = get_timed_firstline(firstline, &hours, lastuptime, 1);
    if ( res != 0 ) {
        LOGE("%s: can't get timed first line for history file", __FUNCTION__);
        return res;
    }
    /* Clean obsolete legacy crashlog folders at each uptime event */
    if (!gabortcleansd)
        clean_crashlog_in_sd(SDCARD_LOGS_DIR, 10);

    /* Update history file first line (uptime line) */
    errno = 0;
    fprintf(fd, firstline, &hours);
    fclose(fd);
    if (errno != 0) {
        return -errno;
    }
    /* Send an uptime event every 12 hours (by default, depending on uptime frequency value set)*/
    if ((hours / gcurrent_uptime_hour_frequency) >= loop_uptime_event) {
        char *key = raise_event(PER_UPTIME, "", NULL, NULL);
        free(key);
        loop_uptime_event = (hours / gcurrent_uptime_hour_frequency) + 1;
        restart_profile_srv(2);
        check_running_power_service();
    }
    return 0;
}

/**
* Name          : update_history_on_cmd_delete
* Description   : This function updates the history_event on a CMDDELETE command
*                 The line of the history event containing one of events list is updated:
*                 The CRASH keyword is replaced by DELETE keyword
*                 The crashlog folder is removed
* Parameters    :
*   char *events          -> chain containing events separated by comma
**/
int update_history_on_cmd_delete(char *events) {
    char **events_list = NULL, crashdir[MAXLINESIZE], line[MAXLINESIZE], eventid[SHA1_DIGEST_LENGTH+1];
    int nbpatterns, maxpatterns = 10, maxpatternsize = 48, res, idx;
    FILE *fd;
    fd = fopen(HISTORY_FILE, "r+");
    if (!fd) {
        LOGE("%s: Unable to open %s - %s\n",
            __FUNCTION__, HISTORY_FILE, strerror(errno));
        return -1;
    }
    /* Get events list from input events comma chain*/
    events_list = commachain_to_fixedarray(events, maxpatternsize, maxpatterns, &nbpatterns);
    if (nbpatterns <= 0) {
        LOGE("%s: Not patterns found in %s... stop the operation\n",
            __FUNCTION__, events);
        fclose(fd);
        return -1;
    }
    /*read each line of history file and check if event id matches one of the list*/
    while (freadline(fd, line) > 0) {
        res = sscanf(line, "CRASH %s %*s %*s %s\n", eventid, crashdir);
        if (res == 2) {
            /* Found a crash line, check the patterns */
            for (idx = 0 ; idx < nbpatterns ; idx++)
                if (strstr(eventid, events_list[idx])) {
                    fseek(fd, -strlen(line), SEEK_CUR);
                    fwrite("DELETE", 1, 6, fd);
                    fseek(fd, strlen(line)-6, SEEK_CUR);
                    rmfr(crashdir);
                }
        }
    }
    /*free allocated resources*/
    for (idx = 0 ; idx < maxpatterns ; idx++) {
        free(events_list[idx]);
    }
    free(events_list);
    fclose(fd);
    return 0;
}

int process_uptime_event(struct watch_entry __attribute__((unused)) *entry, struct inotify_event __attribute__((unused)) *event) {

    return add_uptime_event();
}