#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <ctype.h>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include <time.h>
#include <sha1.h>

#include <cutils/properties.h>
#ifndef __TEST__
#include <linux/android_alarm.h>
#endif

#include <crashutils.h>
#include <privconfig.h>
#include <history.h>
#include <fsutils.h>

char gbuildversion[PROPERTY_VALUE_MAX] = {0,};
char gboardversion[PROPERTY_VALUE_MAX] = {0,};
char guuid[256] = {0,};
int gabortcleansd = 0;

static struct tm *gcurrenttime = NULL;

char *get_time_formated(char *format, char *dest) {

    time_t t;

    time(&t);
    gcurrenttime = localtime((const time_t *)&t);
    PRINT_TIME(dest, format, gcurrenttime);
    return dest;
}

unsigned long long get_uptime(int refresh, int *error)
{
    static long long time_ns = -1;
#ifndef __LINUX__
    struct timespec ts;
    int result;
    int fd;
#else
    struct timeval ts;
#endif
    *error = 0;

    if (!refresh && time_ns != -1) return time_ns;

#ifndef __LINUX__
    fd = open("/dev/alarm", O_RDONLY);
    if (fd <= 0) {
        *error = errno;
        LOGE("%s - Cannot open file: /dev/alarm: %s\n", __FUNCTION__,
            strerror(errno));
        return -1;
    }
    result = ioctl(fd,
        ANDROID_ALARM_GET_TIME(ANDROID_ALARM_ELAPSED_REALTIME), &ts);
    close(fd);
    if (result < 0) {
        *error = errno;
        LOGE("%s - Cannot ioctl /dev/alarm: %s\n", __FUNCTION__,
            strerror(errno));
        return -1;
    }
    time_ns = (((long long) ts.tv_sec) * 1000000000LL) + ((long long) ts.tv_nsec);
#else
#include <sys/time.h>
    gettimeofday(&ts, NULL);
    time_ns = (((long long) ts.tv_sec) * 1000000000LL) + ((long long) ts.tv_usec) * 1000LL;
#endif
    return time_ns;
}

void do_last_kmsg_copy(int dir) {
    char destion[PATHMAX];

    if ( file_exists(LAST_KMSG) ) {
        snprintf(destion, sizeof(destion), "%s%d/%s", CRASH_DIR, dir, LAST_KMSG_FILE);
        do_copy_tail(LAST_KMSG, destion, MAXFILESIZE);
    }
}

/* Compute key shall be checked only once at the first call to insure the
  memory allocation succeeded */
static int compute_key(char* key, char *event, char *type)
{
    static SHA1_CTX *sha = NULL;
    char buf[256] = { '\0', };
    long long time_ns=0;
    char *tmp_key = key;
    unsigned char results[SHA1_DIGEST_LENGTH];
    int i;

    if (sha == NULL) {
        sha = (SHA1_CTX*)malloc(sizeof(SHA1_CTX));
        if (sha == NULL) {
            LOGE("%s - Cannot create SHA1_CTX memory... fails to compute the key!\n", __FUNCTION__);
            return -1;
        }
        SHA1Init(sha);
    }

    if (!key || !event || !type) return -EINVAL;

    time_ns = get_uptime(1, &i);
    snprintf(buf, 256, "%s%s%s%s%lld", gbuildversion, guuid, event, type, time_ns);

    SHA1Update(sha, (unsigned char*) buf, strlen(buf));
    SHA1Final(results, sha);
    for (i = 0; i < SHA1_DIGEST_LENGTH/2; i++)
    {
        sprintf(tmp_key, "%02x", results[i]);
        tmp_key+=2;
    }
    *tmp_key=0;
    return 0;
}

char **commachain_to_fixedarray(char *chain,
        unsigned int recordsize, unsigned int maxrecords, int *res) {
    char *curptr, *copy, *psave, **array;
    int idx;

    if (!chain || !recordsize) {
        *res = -EINVAL;
        return NULL;
    }

    *res = 0;
    if (!maxrecords) return NULL;

    /* First copy the chain because it gets modified by strtok_r */
    copy = strdup(chain);
    if (!copy) {
        *res = -errno;
        return NULL;
    }
    curptr = strtok_r(copy, ";", &psave);
    if (!curptr) {
        free(copy);
        return NULL;
    }

    array = (char**)malloc(maxrecords*sizeof(char*));
    if (!array) {
        *res = -errno;
        LOGE("%s: Cannot allocate %d bytes of temporary memory - %s\n",
            __FUNCTION__, maxrecords*(int)sizeof(char*), strerror(errno));
        free(copy);
        return NULL;
    }
    for (idx = 0 ; (unsigned int)idx < maxrecords ; idx++) {
        array[idx] = (char*)malloc(recordsize*sizeof(char));
        if (!array[idx]) {
            *res = -errno;
            LOGE("%s: Cannot allocate %d bytes of temporary memory to store an aplogs pattern - %s\n",
                __FUNCTION__, recordsize*(int)sizeof(char), strerror(errno));
            for (--idx ; idx >= 0 ; idx--) {
                free(array[idx]);
            }
            free(array);
            free(copy);
            return NULL;
        }
    }

    /* Do the job, finally!! */
    for (idx = 0 ; curptr && (unsigned int)idx < maxrecords ; idx++) {
        strncpy(array[*res], curptr, recordsize-1);
        array[*res][recordsize-1] = 0;
        curptr = strtok_r(NULL, ";", &psave);
        (*res)++;
    }
    /* returns maxrecords + 1 if the chain tokens exceeded
        the array capacity */
    if (curptr != NULL) (*res)++;

    free(copy);
    return array;
}

static char *priv_raise_event(char *event, char *type, char *subtype, char *log, int add_uptime) {
    struct history_entry entry;
    char key[SHA1_DIGEST_LENGTH+1];
    char newuptime[32], *puptime = NULL;
    char lastbootuptime[24];
    int res, hours;
    const char *datelong = get_current_time_long(1);

    /* UPTIME      : event uptime value is get from system
     * UPTIME_BOOT : event uptime value get from history file first line
     * NO_UPTIME   : no event uptime value */
    switch(add_uptime) {
    case UPTIME :
        puptime = &newuptime[0];
        if ((res = get_uptime_string(newuptime, &hours)) < 0) {
            LOGE("%s failed: Cannot get the uptime - %s\n",
                __FUNCTION__, strerror(-res));
            return NULL;
        }
        break;
    case UPTIME_BOOT :
        if (!get_lastboot_uptime(lastbootuptime))
            puptime = &lastbootuptime[0];
        break;
    default : /* NO_UPTIME */
        break;
    }

    if (!event) {
        /* event is not set, then use type/subtype */
        event = type;
        type = subtype;
    }
    if (!subtype) subtype = type;
    compute_key(key, event, type);

    entry.event = event;
    entry.type = type;
    entry.log = log;
    entry.lastuptime = puptime;
    entry.key = key;
    entry.eventtime = (char*)datelong;

    errno = 0;
    if ((res = update_history_file(&entry)) != 0) {
        LOGE("%s: Cannot update the history file (%s), drop the event \"%-8s%-8s%-22s%-20s%s\"\n",
            __FUNCTION__, strerror(-res), event, type, key, datelong, type);
        errno = -res;
        return NULL;
    }
    if (!strncmp(event, CRASHEVENT, sizeof(CRASHEVENT)) || !strncmp(event, BZEVENT, sizeof(BZEVENT))) {
        /* Creating a minimal crashfile is required if log not null */
        if ( log && (res = create_minimal_crashfile( (!strncmp(event, CRASHEVENT, sizeof(CRASHEVENT)) ? subtype : event),
                log, key, puptime, datelong)) != 0) {
            LOGE("%s: Cannot create a minimal crashfile in %s - %s.\n", __FUNCTION__,
                entry.log, strerror(-res));
            errno = -res;
            return NULL;
        }
    }
    notify_crashreport();
    return strdup(key);
}

char *raise_event_nouptime(char *event, char *type, char *subtype, char *log) {
    return priv_raise_event(event, type, subtype, log, NO_UPTIME);
}

char *raise_event(char *event, char *type, char *subtype, char *log) {
    return priv_raise_event(event, type, subtype, log, UPTIME);
}

char *raise_event_bootuptime(char *event, char *type, char *subtype, char *log) {
    return priv_raise_event(event, type, subtype, log, UPTIME_BOOT);
}

void restart_profile_srv(int serveridx) {
    char value[PROPERTY_VALUE_MAX];
    char expected;
    char *profile_srv;

    switch (serveridx) {
        case 1:
            profile_srv = "profile1_rest";
            expected = '1';
            break;
        case 2:
            profile_srv = "profile2_rest";
            expected = '2';
            break;
        default: return;
    }

    if (property_get(PROP_PROFILE, value, NULL) <= 0) return;
    if ( value[0] == expected )
        start_daemon(profile_srv);
}

void check_running_power_service() {
#ifdef FULL_REPORT
    char powerservice[PROPERTY_VALUE_MAX];
    char powerenable[PROPERTY_VALUE_MAX];

    property_get("init.svc.profile_power", powerservice, "");
    property_get("persist.service.power.enable", powerenable, "");
    if (strcmp(powerservice, "running") && !strcmp(powerenable, "1")) {
        LOGE("power service stopped whereas property is set .. restarting\n");
        start_daemon("profile_power");
    }
#endif
}

int get_build_board_versions(char *filename, char *buildver, char *boardver) {
    FILE *fd;
    char buffer[MAXLINESIZE];
    char bldpattern[MAXLINESIZE], brdpattern[MAXLINESIZE];
    int bldfieldlen, brdfieldlen;

    buildver[0] = 0;
    boardver[0] = 0;
    property_get(PROP_BUILD_FIELD, buildver, "");
    property_get(PROP_BOARD_FIELD, boardver, "");
    if (buildver[0] != 0 && boardver[0] != 0) return 0;

    if ((fd = fopen(filename, "r")) == NULL) {
        LOGE("%s - Cannot read %s - %s\n", __FUNCTION__, filename, strerror(errno));
        return -errno;
    }

    /* build the patterns */
    bldfieldlen = strlen(PROP_BUILD_FIELD);
    brdfieldlen = strlen(PROP_BOARD_FIELD);
    memcpy(bldpattern, PROP_BUILD_FIELD, bldfieldlen);
    memcpy(brdpattern, PROP_BOARD_FIELD, brdfieldlen);
    bldpattern[bldfieldlen+3] = '0';bldpattern[bldfieldlen+2] = 's';bldpattern[bldfieldlen+1] = '%';bldpattern[bldfieldlen] = '=';
    brdpattern[brdfieldlen+3] = '0';brdpattern[brdfieldlen+2] = 's';brdpattern[brdfieldlen+1] = '%';brdpattern[brdfieldlen] = '=';

    /* read the file */
    while (freadline(fd, buffer) > 0) {
        if (buildver[0] == 0)
            sscanf(buffer, bldpattern, buildver);
        if (boardver[0] == 0)
            sscanf(buffer, brdpattern, boardver);
        if (buildver[0] != 0 && boardver[0] != 0) break;
    }
    fclose(fd);
    return ((buildver[0] != 0 && boardver[0] != 0) ? 0 : -1);
}

/*
* Name          : process_info_and_error
* Description   : This function manages treatment of error and info
*                 When event or error trigger file is detected, it copies data and trigger files
* Parameters    :
*   char *filename        -> path of watched directory/file
*   char *name            -> name of the file inside the watched directory that has triggered the event
*/
void process_info_and_error(char *filename, char *name) {
    int dir;
    char path[PATHMAX];
    char destion[PATHMAX];
    unsigned int j = 0;
    char *p;
    char tmp[32];
    char name_event[10];
    char file_ext[20];
    char type[20] = { '\0', };
    char tmp_data_name[PATHMAX];
    char *key;

    if (strstr(name, "_infoevent" )){
        snprintf(name_event,sizeof(name_event),"%s",INFOEVENT);
        snprintf(file_ext,sizeof(file_ext),"%s","_infoevent");
    }else if (strstr(name, "_errorevent" )){
        snprintf(name_event,sizeof(name_event),"%s",ERROREVENT);
        snprintf(file_ext,sizeof(file_ext),"%s","_errorevent");
    }else{ /*Robustness*/
        LOGE("Unknown stats trigger file\n");
        return;
    }
    snprintf(tmp,sizeof(tmp),"%s",name);

    dir = find_new_crashlog_dir(STATS_MODE);
    if (dir < 0) {
        LOGE("find dir for stat trigger failed\n");
        p = strstr(tmp,"trigger");
        if ( p ){
            strcpy(p,"data");
        }
        key = raise_event(name_event, tmp, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", name_event, key, get_current_time_long(0), tmp);
        free(key);
        return;
    }
    /*copy data file*/
    p = strstr(tmp,file_ext);
    if ( p ){
        strcpy(p,"_data");
        find_matching_file(filename,tmp, tmp_data_name);
        snprintf(path, sizeof(path),"%s/%s", filename,tmp_data_name);
        snprintf(destion,sizeof(destion),"%s%d/%s", STATS_DIR, dir, tmp_data_name);
        do_copy_tail(path, destion, 0);
        remove(path);
    }
    /*copy trigger file*/
    snprintf(path, sizeof(path),"%s/%s", filename,name);
    snprintf(destion,sizeof(destion),"%s%d/%s", STATS_DIR,dir,name);
    do_copy_tail(path, destion, 0);
    remove(path);
    snprintf(destion,sizeof(destion),"%s%d/", STATS_DIR,dir);
    /*create type */
    snprintf(tmp,sizeof(tmp),"%s",name);
    /*Set to upper case*/
    p = strstr(tmp,file_ext);
    if (p) {
        for (j=0;j<sizeof(type)-1;j++) {
            if (p == (tmp+j))
                break;
            type[j]=toupper(tmp[j]);
        }
    } else
        snprintf(type,sizeof(type),"%s",name);
    key = raise_event(name_event, type, NULL, destion);
    LOGE("%-8s%-22s%-20s%s %s\n", name_event, key, get_current_time_long(0), type, destion);
    free(key);
}

/**
 * @brief Generates an INFO event from an input trigger file and input data
 *
 * This function generates an INFO event by creating an infoevent file (instead of
 * using classical watcher mechanism) and filling it with input data (data0, data1 and data2)
 *
 * @param[in] filename : name of the infoevent file to create (shall contains "_infoevent" pattern)
 * @param[in] data0 : string set to DATA0 field in the infoevent file created
 * @param[in] data1 : string set to DATA1 field in the infoevent file created
 * @param[in] data2 : string set to DATA2 field in the infoevent file created
 */
void create_infoevent(char* filename, char* data0, char* data1, char* data2)
{
    FILE *fp;
    char fullpath[PATHMAX];

    snprintf(fullpath, sizeof(fullpath)-1, "%s/%s", LOGS_DIR, filename);

    fp = fopen(fullpath,"w");
    if (fp == NULL)
    {
        LOGE("can not create file1: %s\n", fullpath);
        return;
    }
    //Fill DATA fields
    if (data0 != NULL)
        fprintf(fp,"DATA0=%s\n", data0);
    if (data1 != NULL)
        fprintf(fp,"DATA1=%s\n", data1);
    if (data2 != NULL)
        fprintf(fp,"DATA2=%s\n", data2);
    fprintf(fp,"_END\n");
    fclose(fp);

    process_info_and_error(LOGS_DIR, filename);
}

const char *get_build_footprint() {
    static char footprint[SIZE_FOOTPRINT_MAX] = {0,};
    char prop[PROPERTY_VALUE_MAX];

    /* footprint contains:
     * buildId
     * fingerPrint
     * kernelVersion
     * buildUserHostname
     * modemVersion
     * ifwiVersion
     * iafwVersion
     * scufwVersion
     * punitVersion
     * valhooksVersion */
    if (footprint[0] != 0) return footprint;

    snprintf(footprint, SIZE_FOOTPRINT_MAX, "%s,", gbuildversion);

    property_get(FINGERPRINT_FIELD, prop, "");
    strncat(footprint, prop, SIZE_FOOTPRINT_MAX);
    strncat(footprint, ",", SIZE_FOOTPRINT_MAX);

    property_get(KERNEL_FIELD, prop, "");
    strncat(footprint, prop, SIZE_FOOTPRINT_MAX);
    strncat(footprint, ",", SIZE_FOOTPRINT_MAX);

    property_get(USER_FIELD, prop, "");
    strncat(footprint, prop, SIZE_FOOTPRINT_MAX);
    strncat(footprint, "@", SIZE_FOOTPRINT_MAX);

    property_get(HOST_FIELD, prop, "");
    strncat(footprint, prop, SIZE_FOOTPRINT_MAX);
    strncat(footprint, ",", SIZE_FOOTPRINT_MAX);

    read_file_prop_uid(MODEM_FIELD, MODEM_UUID, prop, "unknown");
    strncat(footprint, prop, SIZE_FOOTPRINT_MAX);
    strncat(footprint, ",", SIZE_FOOTPRINT_MAX);

    property_get(IFWI_FIELD, prop, "");
    strncat(footprint, prop, SIZE_FOOTPRINT_MAX);
    strncat(footprint, ",", SIZE_FOOTPRINT_MAX);

    property_get(IAFW_VERSION, prop, "");
    strncat(footprint, prop, SIZE_FOOTPRINT_MAX);
    strncat(footprint, ",", SIZE_FOOTPRINT_MAX);

    property_get(SCUFW_VERSION, prop, "");
    strncat(footprint, prop, SIZE_FOOTPRINT_MAX);
    strncat(footprint, ",", SIZE_FOOTPRINT_MAX);

    property_get(PUNIT_VERSION, prop, "");
    strncat(footprint, prop, SIZE_FOOTPRINT_MAX);
    strncat(footprint, ",", SIZE_FOOTPRINT_MAX);

    property_get(VALHOOKS_VERSION, prop, "");
    strncat(footprint, prop, SIZE_FOOTPRINT_MAX);
    return footprint;
}

static const char *get_imei() {
    static char imei[PROPERTY_VALUE_MAX] = { 0, };

    if (imei[0] != 0) return imei;

    property_get(IMEI_FIELD, imei, "");
    return imei;
}

void start_daemon(const char *daemonname) {
    property_set("ctl.start", (char *)daemonname);
}

//This function creates a minimal crashfile (without DATA0, DATA1 and DATA2 fields)
//Note:DATA0 is filled for Modem Panic case only
int create_minimal_crashfile(const char* type, const char* path, char* key, const char* uptime, const char* date)
{
    FILE *fp;
    char fullpath[PATHMAX];
    char mpanicpath[PATHMAX];

    snprintf(fullpath, sizeof(fullpath)-1, "%s/%s", path, CRASHFILE_NAME);

    //Create crashfile
    errno = 0;
    fp = fopen(fullpath,"w");
    if (fp == NULL)
    {
        LOGE("%s: Cannot create %s - %s\n", __FUNCTION__, fullpath, strerror(errno));
        return -errno;
    }
    fclose(fp);
    do_chown(fullpath, PERM_USER, PERM_GROUP);

    fp = fopen(fullpath,"w");
    if (fp == NULL)
    {
        LOGE("can not open file: %s\n", fullpath);
        return -errno;
    }

    //Fill crashfile
    if (!strncmp(type,BZEVENT,sizeof(BZEVENT))) {
        fprintf(fp,"EVENT=BZ\n");
    }
    else {
        fprintf(fp,"EVENT=CRASH\n");
    }
    fprintf(fp,"ID=%s\n", key);
    fprintf(fp,"SN=%s\n", guuid);
    fprintf(fp,"DATE=%s\n", date);
    fprintf(fp,"UPTIME=%s\n", uptime);
    fprintf(fp,"BUILD=%s\n", get_build_footprint());
    fprintf(fp,"BOARD=%s\n", gboardversion);
    fprintf(fp,"IMEI=%s\n", get_imei());
    if (!strncmp(type,BZEVENT,sizeof(BZEVENT))) {
        fprintf(fp,"TYPE=MANUAL\n");
    }
    else {
        fprintf(fp,"TYPE=%s\n", type);
    }
    //MPANIC crash : fill DATA0 field
    if (!strcmp(MDMCRASH_EVNAME, type)){
        LOGI("Modem panic detected : generating DATA0\n");
        FILE *fd_panic;
        DIR *d;
        struct dirent* de;
        d = opendir(path);
        if(!d) {
            LOGE("%s: Can't open dir %s\n",__FUNCTION__, path);
            fclose(fp);
            return -1;
        }
        while ((de = readdir(d))) {
            const char *name = de->d_name;
            int ismpanic, iscrashdata = 0;
            ismpanic = (strstr(name, "mpanic") != NULL);
            if (!ismpanic) iscrashdata = (strstr(name, "_crashdata") != NULL);
            if (ismpanic || iscrashdata) {
                snprintf(mpanicpath, sizeof(mpanicpath)-1, "%s/%s", path, name);
                fd_panic = fopen(mpanicpath, "r");
                if (fd_panic == NULL){
                    LOGE("can not open file: %s\n", mpanicpath);
                    break;
                }
                char value[PATHMAX] = "";
                fscanf(fd_panic, "%s", value);
                fclose(fd_panic);
                if (ismpanic)
                    fprintf(fp,"DATA0=%s\n", value);
                else // iscrashdata
                    fprintf(fp,"%s\n", value);
                break;
            }
        }
        closedir(d);
    }
    fprintf(fp,"_END\n");
    fclose(fp);
    return 0;
}

void notify_crashreport() {
    char boot_state[PROPERTY_VALUE_MAX];

    property_get(PROP_BOOT_STATUS, boot_state, "-1");
    if (strcmp(boot_state, "1"))
        return;

    int status = system("am broadcast -n com.intel.crashreport/.NotificationReceiver -a com.intel.crashreport.intent.CRASH_NOTIFY -c android.intent.category.ALTERNATIVE");
    if (status != 0)
        LOGI("notify crashreport status: %d.\n", status);
}

int raise_infoerror(char *type, char *subtype) {
    char *key;
    int status;

    if ((key = raise_event(NULL, type, subtype, LOGINFO_DIR)) == NULL)
        return -errno;
    LOGE("%-8s%-22s%-20s%s\n", type, key, get_current_time_long(0), subtype);
    free(key);
    unlink(LOGRESERVED);
#ifdef FULL_REPORT
    status = system("/system/bin/monitor_crashenv");
    if (status != 0)
        LOGE("monitor_crashenv status: %d.\n", status);
#endif
    return 0;
}

/*
 * Name         :   check_crashlog_dead
 * Description  :   Get the token property and had a 1 at the end.
 *                  When at least 2 ones are read, reports that an
 *                  error happenned in crashtool until 4 ones are
 *                  read, then gets silent and stop adding ones
 */
void check_crashlog_died()
{
    char token[PROPERTY_VALUE_MAX];
    property_get("crashlogd.token", token, "");
    if ((strlen(token) < 4)) {
         strcat(token, "1");
         property_set("crashlogd.token", token);
         if (!strncmp(token, "11", 2))
             raise_infoerror(ERROREVENT, CRASHLOG_ERROR_DEAD);
    }
}

int do_screenshot_copy(char* bz_description, char* bzdir) {
    char buffer[PATHMAX];
    char screenshot[PATHMAX];
    char destion[PATHMAX];
    FILE *fd1;
    struct stat info;
    int bz_num = 0;
    unsigned int screenshot_len;

    if (stat(bz_description, &info) < 0)
        return -1;

    fd1 = fopen(bz_description,"r");
    if(fd1 == NULL){
        LOGE("can not open file: %s\n", bz_description);
        return -1;
    }

    while(!feof(fd1)){
        if (fgets(buffer, sizeof(buffer), fd1) != NULL){
            if (strstr(buffer,SCREENSHOT_PATTERN)){
                //Compute length of screenshot path
                screenshot_len = strlen(buffer) - strlen(SCREENSHOT_PATTERN);
                if ((screenshot_len > 0) && (screenshot_len < sizeof(screenshot))) {
                    //Copy file path
                    strncpy(screenshot, buffer+strlen(SCREENSHOT_PATTERN), screenshot_len);
                    //If last character is '\n' replace it by '\0'
                    if ((screenshot[screenshot_len-1]) == '\n')
                        screenshot_len--;
                    screenshot[screenshot_len]= '\0';

                    if(bz_num == 0)
                        snprintf(destion,sizeof(destion),"%s/bz_screenshot.png", bzdir);
                    else
                        snprintf(destion,sizeof(destion),"%s/bz_screenshot%d.png", bzdir, bz_num);
                    bz_num++;
                    do_copy_tail(screenshot,destion,0);
                }
            }
        }
    }

    if (fd1 != NULL)
        fclose(fd1);

    return 0;
}

/*
* Name          : clean_crashlog_in_sd
* Description   : clean legacy crash log folder
* Parameters    :
*   char *dir_to_search       -> name of the folder
*   int max                   -> max number of folder to remove
*/
void clean_crashlog_in_sd(char *dir_to_search, int max) {
    char path[PATHMAX];
    DIR *d;
    struct dirent* de;
    int i = 0;

    d = opendir(dir_to_search);
    if (d) {
        while ((de = readdir(d))) {
            const char *name = de->d_name;
            snprintf(path, sizeof(path)-1, "%s/%s", dir_to_search, name);
            if ( (strstr(path, SDCARD_CRASH_DIR) ||
                 (strstr(path, SDCARD_STATS_DIR)) ||
                 (strstr(path, SDCARD_APLOGS_DIR))) )
                if (history_has_event(path)) {
                    if  (rmfr(path) < 0)
                        LOGE("failed to remove folder %s", path);
                    i++;
                    if (i >= max)
                       break;
                }
            }
            closedir(d);
    }
    // abort clean of legacy log folder when there is no more folders expect the ones in history_event
    gabortcleansd = (i < max);
}
