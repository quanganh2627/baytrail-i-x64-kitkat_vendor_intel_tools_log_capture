#ifndef __CRASHUTILS_H__
#define __CRASHUTILS_H__

#define TIME_FORMAT_LENGTH	32
#define DATE_FORMAT_SHORT   "%Y%m%d"
#define TIME_FORMAT_SHORT   "%Y%m%d%H%M%S"
#define TIME_FORMAT_LONG    "%Y-%m-%d/%H:%M:%S  "
#define PRINT_TIME(var_tmp, format_time, local_time) {              \
    strftime(var_tmp, TIME_FORMAT_LENGTH, format_time, local_time); \
    var_tmp[TIME_FORMAT_LENGTH-1]=0;                                \
    }

char *get_time_formated(char *format, char *dest);

static inline const char *get_current_time_short(int refresh) {

    static char shorttime[TIME_FORMAT_LENGTH] = {0,};

    if (!refresh && shorttime[0] != 0) return shorttime;

    /* not initialized yet or to refresh */
    return get_time_formated(TIME_FORMAT_SHORT, shorttime);
}
static inline const char *get_current_date_short(int refresh) {

    static char shortdate[TIME_FORMAT_LENGTH] = {0,};

    if (!refresh && shortdate[0] != 0) return shortdate;

    /* not initialized yet or to refresh */
    return get_time_formated(DATE_FORMAT_SHORT, shortdate);
}

static inline const char *get_current_time_long(int refresh) {

    static char longtime[TIME_FORMAT_LENGTH] = {0,};

    if (!refresh && longtime[0] != 0) return longtime;

    /* not initialized yet or to refresh */
    return get_time_formated(TIME_FORMAT_LONG, longtime);
}

unsigned long long get_uptime(int refresh, int *error);

char **commachain_to_fixedarray(char *chain,
        unsigned int recordsize, unsigned int maxrecords, int *res);
int do_screenshot_copy(char* bz_description, char* bzdir);

void do_last_kmsg_copy(int dir);
void clean_crashlog_in_sd(char *dir_to_search, int max);
void check_crashlog_died();
int raise_infoerror(char *type, char *subtype);
char *raise_event(char *event, char *type, char *subtype, char *log);
char *raise_event_nouptime(char *event, char *type, char *subtype, char *log);
char *raise_event_bootuptime(char *event, char *type, char *subtype, char *log);
char *raise_event_dataready(char *event, char *type, char *subtype, char *log, int data_ready);
void create_infoevent(char* filename, char* data0, char* data1,
    char* data2);
void notify_crashreport();
char *create_crashdir_move_crashfile(char *origpath, char *crashfile, int copylogs);

void start_daemon(const char *daemonname);
void restart_profile_srv(int serveridx);
void check_running_power_service();

int get_build_board_versions(char *filename, char *buildver, char *boardver);
const char *get_build_footprint();
int create_minimal_crashfile(const char* type, const char* path, char* key, const char* uptime, const char* date, int data_ready);


void process_info_and_error(char *filename, char *name);

#endif /* __CRASHUTILS_H__ */
