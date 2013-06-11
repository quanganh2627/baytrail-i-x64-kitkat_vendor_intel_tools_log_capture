#include "inotify_handler.h"
#include "privconfig.h"
#include "crashutils.h"
#include "dropbox.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>

/**
* @brief structure containing directories watched by crashlogd
*
* This structure contains every watched directories, with the itnotify mask value, and the associated
* filename that should trigger a processing
* note: a watched directory can only have one mask value
*/
struct watch_entry wd_array[] = {
    /* -------------Warning: if table is updated, don't forget to update also WDCOUNT and gwdcountstart in main function--- */
    {0, DROPBOX_DIR_MASK,   LOST_TYPE,      LOST_EVNAME,        DROPBOX_DIR,        ".lost",                    NULL}, /* for full dropbox */
    {0, DROPBOX_DIR_MASK,   SYSSERVER_TYPE, SYSSERVER_EVNAME,   DROPBOX_DIR,        "system_server_watchdog",   NULL},
    {0, DROPBOX_DIR_MASK,   ANR_TYPE,       ANR_EVNAME,         DROPBOX_DIR,        "anr",                      NULL},
    {0, TOMBSTONE_DIR_MASK, TOMBSTONE_TYPE, TOMBSTONE_EVNAME,   TOMBSTONE_DIR,      "tombstone",                NULL},
    {0, DROPBOX_DIR_MASK,   JAVACRASH_TYPE, JAVACRASH_EVNAME,   DROPBOX_DIR,        "crash",                    NULL},
    {0, CORE_DIR_MASK,      APCORE_TYPE,    APCORE_EVNAME,      HISTORY_CORE_DIR,   ".core",                    NULL},
    {0, CORE_DIR_MASK,      HPROF_TYPE,     HPROF_EVNAME,       HISTORY_CORE_DIR,   ".hprof",                   NULL},
    {0, STAT_DIR_MASK,      STATTRIG_TYPE,  STATSTRIG_EVNAME,   STAT_DIR,           "_trigger",                 NULL},
    {0, APLOG_DIR_MASK,     APLOGTRIG_TYPE, APLOGTRIG_EVNAME,   APLOG_DIR,          "_trigger",                 NULL},
    {0, APLOG_DIR_MASK,     CMDTRIG_TYPE,   CMDTRIG_EVNAME,     APLOG_DIR,          "_cmd",                     NULL},
    /* -----------------------------above is dir, below is file------------------------------------------------------------ */
    {0, UPTIME_MASK,        UPTIME_TYPE,    UPTIME_EVNAME,      UPTIME_FILE,        NULL,                      NULL},
    /* -------------------------above is AP, below is modem---------------------------------------------------------------- */
    {0, MDMCRASH_DIR_MASK,  MDMCRASH_TYPE,  MDMCRASH_EVNAME,    LOGS_MODEM_DIR,     "mpanic.txt",               NULL},/*for modem crash */
    {0, MDMCRASH_DIR_MASK,  APIMR_TYPE,     APIMR_EVNAME,       LOGS_MODEM_DIR,     "apimr.txt",                NULL},
    {0, MDMCRASH_DIR_MASK,  MRST_TYPE,      MRST_EVNAME,        LOGS_MODEM_DIR,     "mreset.txt",               NULL},
};

int set_watch_entry_callback(unsigned int watch_type, inotify_callback pcallback) {

    if ( watch_type >= DIM(wd_array) ) {
        LOGE("%s: Cannot set the callback for type %u (max is %lu)\n",
            __FUNCTION__, watch_type, (long unsigned int)DIM(wd_array));
        return -1;
    }
    wd_array[watch_type].pcallback = pcallback;
    return 0;
}

/**
 * @brief File Monitor module init function
 *
 * Initialize File Monitor module by adding all watched
 * files/directories to the inotify mechanism and expose it in the
 * module FD. The list of watched files/directories is defined by the
 * wd_array global array.
 *
 * @return 0 on success, -1 on failure.
 */
int init_inotify_handler() {

    int fd, res, i;

#ifndef __TEST__
    fd = inotify_init();
#else
    fd = inotify_init1(IN_NONBLOCK);
#endif
    if (fd < 0) {
        LOGE("inotify_init failed, %s\n", strerror(errno));
        return -errno;
    }

    for (i = 0; i < (int)DIM(wd_array); i++) {
        int alreadywatched = 0, j;
        /* install watches only for new unwatched paths */
        for (j = 0 ; j < i ; j++) {
            if ( !strcmp(wd_array[j].eventpath, wd_array[i].eventpath) ) {
                alreadywatched = 1;
                wd_array[i].wd = wd_array[j].wd;
                LOGI("Dont duplicate the watch for %s\n", wd_array[j].eventpath);
                break;
            }
        }
        if (alreadywatched) continue;
        wd_array[i].wd = inotify_add_watch(fd, wd_array[i].eventpath,
                wd_array[i].eventmask);
        if (wd_array[i].wd < 0) {
            LOGE("Can't add watch for %s - %s.\n",
                wd_array[i].eventpath, strerror(errno));
            res = -errno;
            for (--i ; i >= 0 ; i--) {
                inotify_rm_watch(fd, wd_array[i].wd);
            }
            return res;
        }
        LOGI("%s, wd=%d has been snooped\n", wd_array[i].eventpath, wd_array[i].wd);
    }
    return fd;
}

static struct watch_entry *get_event_entry(int wd, char *eventname) {
    int idx;
    for (idx = 0 ; idx < (int)DIM(wd_array) ; idx++) {
        if ( wd_array[idx].wd == wd ) {
            if ( !eventname && !wd_array[idx].eventpattern )
                return &wd_array[idx];
            if ( eventname &&
                    strstr(eventname, wd_array[idx].eventpattern) )
                return &wd_array[idx];
        }
    }
    return NULL;
}

/**
 * @brief Show the contents of an array of inotify_events
 * Called when a problem occurred during the parsing of
 * the array to ease the debug
 *
 * @param buffer: buffer containing the inotify events
 * @param len: length of the buffer
 */
static void dump_inotify_events(char *buffer, unsigned int len,
    char *lastevent) {

    struct inotify_event *event;
    int i;

    LOGD("%s: Dump the wd_array:\n", __FUNCTION__);
    for (i = 0; i < (int)DIM(wd_array) ; i++) {
        LOGD("%s: wd_array[%d]: filename=%s, wd=%d\n", __FUNCTION__, i, wd_array[i].eventpath, wd_array[i].wd);
    }

    while (1) {
        if (len == 0) {
            /* End of the buffer */
            return;
        }
        event = (struct inotify_event*)buffer;
        if (len < sizeof(struct inotify_event) ||
            len < (sizeof(struct inotify_event) + event->len)) {
            /* Not enought room the last event,
             * get it from the lastevent */
            event = (struct inotify_event*)lastevent;
            if (!event->wd && !event->mask && !event->cookie && !event->len)
                // no last event
                return;
        } else {
            buffer += sizeof(struct inotify_event) + event->len;
            len -= sizeof(struct inotify_event) + event->len;
        }
        LOGD("%s: event received (name=%s, wd=%d, mask=0x%x, len=%d)\n", __FUNCTION__, event->name, event->wd, event->mask, event->len);
    }
}

/**
 * @brief Handle inotify events
 *
 * Calls the callcbacks
 *
 * @param files nb max of logs destination directories (crashlog,
 * aplogs, bz... )
 *
 * @return 0 on success, -1 on error.
 */
int receive_inotify_events(int inotify_fd) {
    int len = 0, orig_len, idx, wd;
    char orig_buffer[sizeof(struct inotify_event)+PATHMAX], *buffer, lastevent[sizeof(struct inotify_event)+PATHMAX];
    struct inotify_event *event;
    struct watch_entry *entry;

    len = read(inotify_fd, orig_buffer, sizeof(orig_buffer));
    if (len < 0) {
        LOGE("%s: Cannot read file_monitor_fd, error is %s\n", __FUNCTION__, strerror(errno));
        return -errno;
    }

    buffer = &orig_buffer[0];
    orig_len = len;
    event = (struct inotify_event *)buffer;

    /* Preinitialize lastevent (in case it was not used so it is not dumped) */
    ((struct inotify_event *)lastevent)->wd = 0;
    ((struct inotify_event *)lastevent)->mask = 0;
    ((struct inotify_event *)lastevent)->cookie = 0;
    ((struct inotify_event *)lastevent)->len = 0;

    while (1) {
        if (len == 0) {
            /* End of the events to read */
            return 0;
        }
        if ((unsigned int)len < sizeof(struct inotify_event)) {
            /* Not enought room for an empty event */
            LOGI("%s: incomplete inotify_event received (%d bytes), complete it\n", __FUNCTION__, len);
            /* copy the last bytes received */
            memcpy(lastevent, buffer, len);
            /* read the missing bytes to get the full length */
            if ( read(inotify_fd, &lastevent[len], len-sizeof(struct inotify_event))
                    != len-(int)sizeof(struct inotify_event)) {
                LOGE("%s: Cannot complete the last inotify_event received (structure part) - %s\n",
                    __FUNCTION__, strerror(errno));
                return -1;
            }
            event = (struct inotify_event*)lastevent;
            /* now, reads the full last event, including its name field */
            if ( read(inotify_fd, &lastevent[sizeof(struct inotify_event)],
                event->len) != (int)event->len) {
                LOGE("%s: Cannot complete the last inotify_event received (name part) - %s\n",
                    __FUNCTION__, strerror(errno));
                return -1;
            }
            len = 0;
            /* now, the last event is complete, we can continue the parsing */
        } else if ( (unsigned int)len < sizeof(struct inotify_event) + event->len ) {
            int res, missing_bytes = (int)sizeof(struct inotify_event) + event->len - len;
            event = (struct inotify_event*)lastevent;
            /* The event was truncated */
            LOGI("%s: truncated inotify_event received (%d bytes missing), complete it\n", __FUNCTION__, missing_bytes);
            /* copy the last bytes received */
            memcpy(lastevent, buffer, len);
            /* now, reads the full last event, including its name field */
            res = read(inotify_fd, &lastevent[len], missing_bytes);
            if ( res != missing_bytes ) {
                LOGE("%s: Cannot complete the last inotify_event received (name part2); received %d bytes, expected %d bytes - %s\n",
                    __FUNCTION__, res, missing_bytes, strerror(errno));
                return -1;
            }
            len = 0;
            /* now, the last event is complete, we can continue the parsing */
        } else {
            event = (struct inotify_event *)buffer;
            buffer += sizeof(struct inotify_event) + event->len;
            len -= sizeof(struct inotify_event) + event->len;
        }

        entry = get_event_entry(event->wd, (event->len ? event->name : NULL));
        if ( !entry ) {
            /* Didn't find any entry for this event, check for
             * a dropbox final event... */
            if (event->len > 8 && !strncmp(event->name, "dropbox-", 8)) {
                /* dumpstate is done so remove the watcher */
                LOGD("%s: Received a dropbox event(%s)...",
                    __FUNCTION__, event->name);
                inotify_rm_watch(inotify_fd, event->wd);
                finalize_dropbox_pending_event(event);
                continue;
            }
            /* Manage case where a watched directory is deleted*/
            if ( event->mask & (IN_DELETE_SELF | IN_MOVE_SELF) ) {
                /* Recreate the dir and reinstall the watch */
                for (idx = 0 ; idx < (int)DIM(wd_array) ; idx++) {
                    if ( wd_array[idx].wd == event->wd )
                        entry = &wd_array[idx];
                }
                if ( entry && entry->eventpath ) {
                    mkdir(entry->eventpath, 0777); /* TO DO : restoring previous rights/owner/group ?*/
                    inotify_rm_watch(inotify_fd, event->wd);
                    wd = inotify_add_watch(inotify_fd, entry->eventpath, entry->eventmask);
                    if ( wd < 0 ) {
                        LOGE("Can't add watch for %s.\n", entry->eventpath);
                        return -1;
                    }
                    LOGW("%s: watched directory %s : \'%s\' has been created and snooped",__FUNCTION__,
                            (event->mask & (IN_DELETE_SELF) ? "deleted" : "moved"), entry->eventpath);
                    /* if the watch was duplicated, set it for all the entries */
                    for (idx = 0 ; idx < (int)DIM(wd_array) ; idx++) {
                        if (wd_array[idx].wd == event->wd)
                            wd_array[idx].wd = wd;
                    }
                    /* Do nothing more on directory events */
                    continue;
                }
            }
            /* Stray event... */
            LOGD("%s: Can't handle the event \"%s\", no valid entry found, drop it...\n",
                __FUNCTION__, (event->len ? event->name : "empty event"));
            continue;
        }
        if ( entry->pcallback && entry->pcallback(entry, event) < 0 ) {
            LOGE("%s: Can't handle the event %s...\n", __FUNCTION__,
                event->name);
            dump_inotify_events(orig_buffer, orig_len, lastevent);
            return -1;
        }
    }

    return 0;
}
