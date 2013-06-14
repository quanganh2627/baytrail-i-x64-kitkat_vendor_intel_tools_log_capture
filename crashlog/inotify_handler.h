#ifndef __INOTIFY_HANDLER_H__
#define __INOTIFY_HANDLER_H__

#include <sys/inotify.h>

/* Define inotify mask values for watched directories */
#define BASE_DIR_MASK       (IN_CLOSE_WRITE|IN_DELETE_SELF|IN_MOVE_SELF)
#define DROPBOX_DIR_MASK    (BASE_DIR_MASK|IN_MOVED_FROM|IN_MOVED_TO)
#define TOMBSTONE_DIR_MASK  (BASE_DIR_MASK)
#define CORE_DIR_MASK       (BASE_DIR_MASK)
#define STAT_DIR_MASK       (BASE_DIR_MASK)
#define APLOG_DIR_MASK      (BASE_DIR_MASK)
#define UPTIME_MASK         IN_CLOSE_WRITE
#define MDMCRASH_DIR_MASK   (BASE_DIR_MASK|IN_CREATE) /* create flag introduce bug*/

struct watch_entry;

/* The callback API is:
 * returns a negative value if any error occurred
 * returns 0 if the event was not handled
 * returns a positive value if the event was handled properly
 */
typedef int (*inotify_callback) (struct watch_entry *,
    struct inotify_event *);

struct watch_entry {
    int wd;
    int eventmask;
    int eventtype;
    char *eventname;
    char *eventpath;
    char *eventpattern;
    inotify_callback pcallback;
};

int init_inotify_handler();
int set_watch_entry_callback(unsigned int watch_type, inotify_callback pcallback);
int receive_inotify_events(int inotify_fd);

#endif /* __INOTIFY_HANDLER_H__ */
