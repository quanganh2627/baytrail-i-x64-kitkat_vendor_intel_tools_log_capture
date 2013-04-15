#ifndef __STARTUP_REASON_H__
#define __STARTUP_REASON_H__

void read_startupreason(char *startupreason);
int crashlog_check_startupreason(char *reason);

#endif /* __STARTUP_REASON_H__ */
