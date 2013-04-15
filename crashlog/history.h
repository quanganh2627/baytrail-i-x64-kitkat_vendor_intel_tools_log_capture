#ifndef __HISTORY__H__
#define __HISTORY__H__

#include "inotify_handler.h"

#include <sys/inotify.h>

struct history_entry {
    char *event;
    char *type;
    char *log;
    const char* lastuptime;
    char* key;
    char* eventtime;
};

int get_uptime_string(char newuptime[24], int *hours);
int update_history_file(struct history_entry *entry);
int reset_uptime_history();
int uptime_history();
int history_has_event(char *eventdir);
int reset_history_cache();
int add_uptime_event();
int update_history_on_cmd_delete(char *events);
int process_uptime_event(struct watch_entry *entry, struct inotify_event *event);

#endif /* __HISTORY__H__ */
