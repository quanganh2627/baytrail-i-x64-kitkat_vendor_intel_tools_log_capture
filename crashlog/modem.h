#ifndef __MODEM_H__
#define __MODEM_H__

#include "privconfig.h"
#include "inotify_handler.h"

#include <sys/types.h>

int process_modem_event(struct watch_entry *entry, struct inotify_event *event);
int crashlog_check_modem_shutdown();
int process_modem_generic(struct watch_entry *entry, struct inotify_event *event, int fd);

#endif /* __MODEM_H__ */
