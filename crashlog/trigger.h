#ifndef __TRIGGER_H__
#define __TRIGGER_H__

int process_stat_event(struct watch_entry *entry, struct inotify_event *event);
int process_aplog_event(struct watch_entry *entry, struct inotify_event *event);
int process_log_event(char *rootdir, char *triggername, int mode);

#endif /* __TRIGGER_H__ */
