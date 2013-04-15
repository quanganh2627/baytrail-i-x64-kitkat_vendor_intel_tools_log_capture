#ifndef __ANRUIWDT_H__
#define __ANRUIWDT_H__

#include "inotify_handler.h"

int process_anruiwdt_event(struct watch_entry *, struct inotify_event *);

#endif /* __ANRUIWDT_H__ */
