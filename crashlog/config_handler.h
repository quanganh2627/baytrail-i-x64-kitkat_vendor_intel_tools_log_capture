#ifndef CONFIG_HANDLER_H_
#define CONFIG_HANDLER_H_

#include "privconfig.h"
#include "inotify_handler.h"
#include "config.h"

#include <sys/types.h>

typedef struct config * pconfig;

struct config {
    int type;  /* 0 => file 1 => directory */
    pchar matching_pattern; /* pattern to check when notified */
    pchar eventname; /* event name to generate when pattern found */
    pconfig next;
    char path[PATHMAX];
    struct watch_entry wd_config;
};

pconfig get_generic_config(char* event_name, pconfig config_to_match);
int generic_match(char* event_name, pconfig config_to_match);
void generic_add_watch(pconfig config_to_watch, int fd);
pconfig generic_match_by_wd(char* event_name, pconfig config_to_match, int wd);
void free_config(pconfig first);
void store_config(char *section, struct config_handle a_conf_handle);
void load_config_by_pattern(char *section_pattern, char *key_pattern, struct config_handle a_conf_handle);
void load_config();

#endif /* CONFIG_HANDLER_H_ */
