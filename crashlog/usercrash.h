#ifndef __USERCRASH__H__
#define __USERCRASH__H__

#include "inotify_handler.h"

int process_usercrash_event(struct watch_entry *entry, struct inotify_event *event);
int process_hprof_event(struct watch_entry *entry, struct inotify_event *event);
int process_apcore_event(struct watch_entry *entry, struct inotify_event *event);

#endif
