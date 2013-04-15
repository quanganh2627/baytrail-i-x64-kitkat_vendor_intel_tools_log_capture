#ifndef __MODEM_H__
#define __MODEM_H__

int process_modem_event(struct watch_entry *entry, struct inotify_event *event);
int crashlog_check_modem_shutdown();

#endif /* __MODEM_H__ */
