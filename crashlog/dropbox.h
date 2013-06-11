#ifndef __DROPBOX_H__
#define __DROPBOX_H__

#include <inotify_handler.h>

void dropbox_set_file_monitor_fd(int file_monitor_fd);
int start_dumpstate_srv(char* crash_dir, int crashidx, char *key);
long extract_dropbox_timestamp(char* filename);
int manage_duplicate_dropbox_events(struct inotify_event *event);
int process_lost_event(struct watch_entry *entry, struct inotify_event *event);
int finalize_dropbox_pending_event(const struct inotify_event *event);

#endif /* __DROPBOX_H__ */
