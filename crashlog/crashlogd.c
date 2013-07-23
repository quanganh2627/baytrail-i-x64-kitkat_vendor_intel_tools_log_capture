/* Copyright (C) Intel 2010
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <fcntl.h>
#include <ctype.h>
#include <errno.h>
#include <time.h>
#include <dirent.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/select.h>
#include <sys/time.h>
#include <private/android_filesystem_config.h>
#include <linux/ioctl.h>
#include <linux/rtc.h>
#define LOG_TAG "CRASHLOG"
#include <linux/android_alarm.h>
#include "cutils/log.h"
#include <sys/inotify.h>
#include <cutils/properties.h>
#include <sys/wait.h>
#include <sys/sendfile.h>
#include <sha1.h>
#ifdef FULL_REPORT
#include <backtrace.h>
#endif
#include <limits.h>
#include "mmgr_cli.h"
#include "mmgr_source.h"
#include "config.h"

#define CRASHEVENT "CRASH"
#define STATSEVENT "STATS"
#define INFOEVENT "INFO"
#define STATEEVENT "STATE"
#define APLOGEVENT "APLOG"
#define STATSTRIGGER "STTRIG"
#define HPROF "HPROF"
#define CMDTRIGGER "CMDTRIG"
#define APLOGTRIGGER "APLOGTRIG"
#define BZEVENT "BZ"
#define ERROREVENT "ERROR"
#define BZTRIGGER "bz_trigger"
#define BZMANUAL "MANUAL"
#define KERNEL_CRASH "IPANIC"
#define SYSSERVER_WDT "UIWDT"
#define KERNEL_SWWDT_CRASH "IPANIC_SWWDT"
#define KERNEL_HWWDT_CRASH "IPANIC_HWWDT"
#define FABRIC_FAKE_CRASH "FABRIC_FAKE"
#define KERNEL_FAKE_CRASH "IPANIC_FAKE"
#define ANR_CRASH "ANR"
#define JAVA_CRASH "JAVACRASH"
#define WTF_CRASH "WTF"
#define TOMB_CRASH "TOMBSTONE"
#define LOST "LOST_DROPBOX"
#define AP_COREDUMP "APCOREDUMP"
#define MODEM_CRASH "MPANIC"
#define MODEM_SHUTDOWN "MSHUTDOWN"
#define CURRENT_UPTIME "CURRENTUPTIME"
#define PER_UPTIME "UPTIME"
#define SYS_REBOOT "REBOOT"
#define AP_INI_M_RST "APIMR"
#define M_RST_WN_COREDUMP "MRESET"
#define FABRIC_ERROR "FABRICERR"
#define UNALIGNED_BLK_FS "NO_BLANKPHONE"
// Add Recovery error crash type
#define RECOVERY_ERROR "RECOVERY_ERROR"
#define CRASHLOG_ERROR_DEAD "CRASHLOG_DEAD"
#define CRASHLOG_ERROR_PATH "CRASHLOG_PATH"
#define CRASHLOG_SWWDT_MISSING "SWWDT_MISSING"
#define CRASHLOG_IPANIC_CORRUPTED "IPANIC_CORRUPTED"
#define USBBOGUS "USBBOGUS"

#define FILESIZE_MAX  (10*1024*1024)
#define PATHMAX 512
#define CMDSIZE_MAX   (21*20) + 1
#define UPTIME_FREQUENCY (5 * 60)
#define TIMEOUT_VALUE (20*1000)
#define UPTIME_HOUR_FREQUENCY 12
#define SIZE_FOOTPRINT_MAX (PROPERTY_VALUE_MAX + 1) * 11
#define BUILD_FIELD "ro.build.version.incremental"
#define BOARD_FIELD "ro.product.model"
#define FINGERPRINT_FIELD "ro.build.fingerprint"
#define KERNEL_FIELD "sys.kernel.version"
#define USER_FIELD "ro.build.user"
#define HOST_FIELD "ro.build.host"
#define IFWI_FIELD "sys.ifwi.version"
#define SCUFW_VERSION "sys.scu.version"
#define PUNIT_VERSION "sys.punit.version"
#define IAFW_VERSION "sys.ia32.version"
#define VALHOOKS_VERSION "sys.valhooks.version"
#define MODEM_FIELD "gsm.version.baseband"
#define IMEI_FIELD "persist.radio.device.imei"
#define PROP_CRASH "persist.service.crashlog.enable"
#define PROP_PROFILE "persist.service.profile.enable"
#define PROP_COREDUMP "persist.core.enabled"
#define PROP_CRASH_MODE "persist.sys.crashlogd.mode"
#define PROP_ANR_USERSTACK "persist.anr.userstack.disabled"
#define PROP_IPANIC_PATTERN "persist.crashlogd.panic.pattern"
#define PROP_APLOG_DEPTH "persist.crashreport.aplogdepth"
#define PROP_APLOG_NB_PACKET "persist.crashreport.packet"
#define PROP_APLOG_DEPTH_DEF "5"
#define PROP_APLOG_NB_PACKET_DEF "1"
#define PROP_LOGSYSTEMSTATE "init.svc.logsystemstate"
#define BOOT_STATUS "sys.boot_completed"
#define SYS_PROP "/system/build.prop"
#define SAVEDLINES  1
#define MAX_RECORDS 5000
#define MAX_DIR 1000
#define PERM_USER "system"
#define PERM_GROUP "log"
#define HISTORY_CORE_DIR  "/logs/core"
#define LOGS_MODEM_DIR  "/logs/modemcrash"
#define LOGS_GPS_DIR  "/logs/gps"
#define APLOG_FILE_BOOT   "/logs/aplog_boot"
#define APLOG_FILE_0        "/logs/aplog"
#define APLOG_FILE_1    "/logs/aplog.1"
#define BPLOG_FILE_0    "/logs/bplog"
#define BPLOG_FILE_1    "/logs/bplog.1"
#define APLOG_TYPE       0
#define BPLOG_TYPE       1
#define APLOG_STATS_TYPE 2
#define SDCARD_LOGS_DIR "/mnt/sdcard/logs"
#define SDCARD_CRASH_DIR "/mnt/sdcard/logs/crashlog"
#define EMMC_CRASH_DIR "/logs/crashlog"
#define SDCARD_STATS_DIR "/mnt/sdcard/logs/stats"
#define EMMC_STATS_DIR "/logs/stats"
#define SDCARD_APLOGS_DIR "/mnt/sdcard/logs/aplogs"
#define EMMC_APLOGS_DIR "/logs/aplogs"
#define SDCARD_BZ_DIR "/mnt/sdcard/logs/bz"
#define LOGS_DIR "/logs"
#define EMMC_BZ_DIR "/logs/bz"
#define LOGINFO_DIR "/logs/info/"
#define DROPBOX_DIR "/data/system/dropbox"
#define LOGRESERVED "/logs/reserved"
#define BZ_CURRENT_LOG "/logs/currentbzlog"
#define CRASH_CURRENT_LOG "/logs/currentcrashlog"
#define STATS_CURRENT_LOG "/logs/currentstatslog"
#define APLOGS_CURRENT_LOG "/logs/currentaplogslog"
#define SDSIZE_CURRENT_LOG "/logs/currentsdsize"
#define SDSIZE_SYSTEM_CMD "du -sk /mnt/sdcard/logs/ > /logs/currentsdsize"
#define HISTORY_FILE  "/logs/history_event"
#define HISTORY_UPTIME "/logs/uptime"
#define LOG_UUID "/logs/uuid.txt"
#define LOG_SPID "/logs/spid.txt"
#define LOG_BUILDID "/logs/buildid.txt"
#define MODEM_UUID "/logs/modemid.txt"
#define KERNEL_CMDLINE "/proc/cmdline"
#define CMDLINE_NAME "cmdline"
#define STARTUP_STR "androidboot.wakesrc="
#define PANIC_CONSOLE_NAME "/proc/emmc_ipanic_console"
#define PROC_FABRIC_ERROR_NAME "/proc/ipanic_fabric_err"
#define PROC_UUID  "/proc/emmc0_id_entry"
#define SYS_SPID_1  "/sys/spid/vendor_id"
#define SYS_SPID_2  "/sys/spid/manufacturer_id"
#define SYS_SPID_3  "/sys/spid/customer_id"
#define SYS_SPID_4  "/sys/spid/platform_family_id"
#define SYS_SPID_5  "/sys/spid/product_line_id"
#define SYS_SPID_6  "/sys/spid/hardware_id"
#define LAST_KMSG "/proc/last_kmsg"
#define LAST_KMSG_FILE "last_kmsg"
#define TIMESTAMP_MAX_SIZE 10 /* Unix style timestamp on 10 characters max.*/

#define SAVED_CONSOLE_NAME "/data/dontpanic/emmc_ipanic_console"
#define SAVED_THREAD_NAME "/data/dontpanic/emmc_ipanic_threads"
#define SAVED_LOGCAT_NAME "/data/dontpanic/emmc_ipanic_logcat"
#define SAVED_FABRIC_ERROR_NAME "/data/dontpanic/ipanic_fabric_err"
#define CONSOLE_NAME "emmc_ipanic_console"
#define THREAD_NAME "emmc_ipanic_threads"
#define LOGCAT_NAME "emmc_ipanic_logcat"
#define FABRIC_ERROR_NAME "ipanic_fabric_err"
#define CRASHFILE_NAME "crashfile"
#define ANR_DUPLICATE_INFOERROR "anr_duplicate_infoevent"
#define JAVACRASH_DUPLICATE_INFOERROR "javacrash_duplicate_infoevent"
#define UIWDT_DUPLICATE_INFOERROR "uiwdt_duplicate_infoevent"
#define ANR_DUPLICATE_DATA "anr_duplicate_data.txt"
#define JAVACRASH_DUPLICATE_DATA "javacrash_duplicate_data.txt"
#define UIWDT_DUPLICATE_DATA "uiwdt_duplicate_data.txt"
#define EXTRA_NAME "EXTRA"


#define MODEM_SHUTDOWN_TRIGGER "/logs/modemcrash/mshutdown.txt"
#define SCREENSHOT_PATTERN "SCREENSHOT="

// Add recovery error trigger
#define RECOVERY_ERROR_TRIGGER "/cache/recovery/recoveryfail"
// Add recovery error log path
#define RECOVERY_ERROR_LOG "/cache/recovery/last_log"

// Add blankphone trigger
#define BLANKPHONE_FILE "/logs/flashing/blankphone_file"
#define CRASHLOG_CONF_PATH "/system/etc/crashlog.conf"
#define NOTIFY_CONF_PATTERN "INOTIFY"
#define GENERAL_CONF_PATTERN "GENERAL"

#define CMDDELETE 1

#define TIME_FORMAT_1 "%Y%m%d%H%M%S"
#define TIME_FORMAT_2 "%Y-%m-%d/%H:%M:%S  "

//Define inotify mask values for watched directories
#define DROPBOX_DIR_MASK IN_MOVED_TO|IN_CLOSE_WRITE|IN_DELETE_SELF|IN_MOVE_SELF|IN_MOVED_FROM
#define TOMBSTONES_DIR_MASK IN_CLOSE_WRITE|IN_DELETE_SELF|IN_MOVE_SELF
#define CORE_DIR_MASK IN_CLOSE_WRITE|IN_DELETE_SELF|IN_MOVE_SELF
#define STATS_DIR_MASK IN_CLOSE_WRITE|IN_DELETE_SELF|IN_MOVE_SELF
#define APLOGS_DIR_MASK IN_CLOSE_WRITE|IN_DELETE_SELF|IN_MOVE_SELF
#define UPTIME_FILE_MASK IN_CLOSE_WRITE
#define MODEMCRASH_DIR_MASK IN_CLOSE_WRITE|IN_DELETE_SELF|IN_MOVE_SELF
#define VBCRASH_DIR_MASK IN_CLOSE_WRITE|IN_DELETE_SELF|IN_CREATE|IN_MOVE_SELF


#define PRINT_TIME(var_tmp, format_time, local_time) { \
    strftime(var_tmp, 32, format_time, local_time);    \
    var_tmp[31]=0;                                     \
}

struct wd_name {
    int wd;
    int mask;
    char *eventname;
    char *filename;
    char *cmp;
};

typedef struct config * pconfig;


struct config {
    int type;  /* 0 => file 1 => directory */
    int event_class; /* 0 => CRASH 1 => ERROR  2=> INFO */
    pchar matching_pattern; /* pattern to check when notified */
    pchar eventname; /* event name to generate when pattern found */
    pconfig next;
    char path[PATHMAX];
    char path_linked[PATHMAX];
    struct wd_name wd_config;
};

pconfig first_modem_config = NULL; /* Points to first modem_config in list */
pconfig current_modem_config = NULL; /* Points to current modem_config in list */
//Variables containing paths of files triggering IPANIC & FABRICERR & WDT treatment
char CURRENT_PANIC_CONSOLE_NAME[PATHMAX]={PANIC_CONSOLE_NAME};
char CURRENT_PROC_FABRIC_ERROR_NAME[PATHMAX]={PROC_FABRIC_ERROR_NAME};
char CURRENT_KERNEL_CMDLINE[PATHMAX]={KERNEL_CMDLINE};

char *CRASH_DIR = NULL;
char *STATS_DIR = NULL;
char *APLOGS_DIR = NULL;
char *BZ_DIR = NULL;

char buildVersion[PROPERTY_VALUE_MAX];
char boardVersion[PROPERTY_VALUE_MAX];
char uuid[256];
int loop_uptime_event = 1;
int test_flag = 0;
#ifdef FULL_REPORT
int index_cons = 0;
#endif
int WDCOUNT_START = 0;
// global variable to abort a clean procedure
int abort_clean_sd = 0;
// global variable to enable dynamic change of uptime frequency
int current_uptime_hour_frequency = UPTIME_HOUR_FREQUENCY;
long current_sd_size_limit = LONG_MAX;
int current_serial_device_id = 0;

static int do_mv(char *src, char *des)
{
    struct stat st;

    /* check if destination exists */
    if (stat(des, &st)) {
        /* an error, unless the destination was missing */
        if (errno != ENOENT) {
            LOGE("failed on %s - %s\n", des, strerror(errno));
            return -1;
        }
    }

    /* attempt to move it */
    if (rename(src, des)) {
        LOGE("failed on '%s' - %s\n", src, strerror(errno));
        return -1;
    }

    return 0;
}

static unsigned int android_name_to_id(const char *name)
{
    const struct android_id_info *info = android_ids;
    unsigned int n;

    for (n = 0; n < android_id_count; n++) {
        if (!strcmp(info[n].name, name))
            return info[n].aid;
    }

    return -1U;
}

static unsigned int decode_uid(const char *s)
{
    unsigned int v;

    if (!s || *s == '\0')
        return -1U;
    if (isalpha(s[0]))
        return android_name_to_id(s);

    errno = 0;
    v = (unsigned int)strtoul(s, 0, 0);
    if (errno)
        return -1U;
    return v;
}

static mode_t get_mode(const char *s)
{
    mode_t mode = 0;
    while (*s) {
        if (*s >= '0' && *s <= '7') {
            mode = (mode << 3) | (*s - '0');
        } else {
            return -1;
        }
        s++;
    }
    return mode;
}

static int do_chmod(char *file, char *mod)
{
    mode_t mode = get_mode(mod);
    if (chmod(file, mode) < 0) {
        return -errno;
    }
    return 0;
}

static int do_chown(const char *file, char *uid, char *gid)
{
    if (strstr(file, SDCARD_CRASH_DIR))
        return 0;

    if (chown(file, decode_uid(uid), decode_uid(gid)))
        return -errno;

    return 0;
}

ssize_t do_read(int fd, void *buf, size_t len)
{
    ssize_t nr;
    while (1) {
        nr = read(fd, buf, len);
        if ((nr < 0) && (errno == EAGAIN || errno == EINTR))
            continue;
        return nr;
    }
}

ssize_t do_write(int fd, const void *buf, size_t len)
{
    ssize_t nr;
    while (1) {
        nr = write(fd, buf, len);
        if ((nr < 0) && (errno == EAGAIN || errno == EINTR))
            continue;
        return nr;
    }
}

static int do_copy_eof(const char *src, const char *des)
{
    int buflen;
    char buffer[4*1024];
    int rc = 0;
    int fd1 = -1, fd2 = -1;
    struct stat info;
    int r_count, w_count;

    if (stat(src, &info) < 0)
        return -1;

    if ((fd1 = open(src, O_RDONLY)) < 0){
        LOGE("%s: can not open file: %s\n", __FUNCTION__, src);
        goto out_err;
    }

    if ((fd2 = open(des, O_WRONLY | O_CREAT | O_TRUNC, 0660)) < 0){
        LOGE("%s: can not open file: %s\n", __FUNCTION__, des);
        goto out_err;
    }

    while (1) {
        r_count = do_read(fd1, buffer, 4*1014);
        if (r_count < 0) {
            LOGE("%s: read failed, err:%s", __FUNCTION__, strerror(errno));
            goto out_err;
        }
        if (r_count == 0)
            break;

        w_count = do_write(fd2, buffer, r_count);
        if (w_count < 0) {
            LOGE("%s: write failed, err:%s", __FUNCTION__, strerror(errno));
            goto out_err;
        }
        if (r_count != w_count) {
            LOGE("%s: write failed, r_count:%d w_count:%d",
                 __FUNCTION__, r_count, w_count);
            goto out_err;
        }
    }

    rc = 0;
    goto out;
out_err:
    rc = -1;
out:
    if (fd1 >= 0)
        close(fd1);
    if (fd2 >= 0)
        close(fd2);

    do_chown(des, PERM_USER, PERM_GROUP);
    return rc;
}

static int do_copy(char *src, char *des, int limit)
{
    int buflen = 4*1024;
    char buffer[4*1024];
    int rc = 0;
    int fd1 = -1, fd2 = -1;
    struct stat info;
    int brtw, brtr;
    char *p;
    int filelen,tmp;

    if (stat(src, &info) < 0)
        return -1;

    if ((fd1 = open(src, O_RDONLY)) < 0){
        LOGE("can not open file: %s\n", src);
        goto out_err;
    }

    if ((fd2 = open(des, O_WRONLY | O_CREAT | O_TRUNC, 0660)) < 0){
        LOGE("can not open file: %s\n", des);
        goto out_err;
    }

    if ( (limit == 0) || (limit >= info.st_size) )
        filelen = info.st_size;
    else{
        filelen = limit;
        lseek(fd1, info.st_size-limit, SEEK_SET);
    }

    while(filelen){
        p = buffer;
        tmp = ((filelen>buflen) ? buflen : filelen);
        brtr = tmp;
        while (brtr) {
            rc = read(fd1, p, brtr);
            if (rc < 0)
                goto out_err;
            if (rc == 0)
                break;
            p += rc;
            brtr -= rc;
        }

        p = buffer;
        brtw = tmp;
        while (brtw) {
            rc = write(fd2, p, brtw);
            if (rc < 0)
                goto out_err;
            if (rc == 0)
                break;
            p += rc;
            brtw -= rc;
        }

        filelen = filelen - tmp;
    }

    rc = 0;
    goto out;
out_err:
    rc = -1;
out:
    if (fd1 >= 0)
        close(fd1);
    if (fd2 >= 0)
        close(fd2);

    do_chown(des, PERM_USER, PERM_GROUP);
    return rc;
}

static void flush_aplog_atboot(char *mode, int dir, char* ts)
{
    char cmd[512] = { '\0', };
    char log_boot_name[512] = { '\0', };
    struct stat info;

    snprintf(log_boot_name, sizeof(log_boot_name)-1, "%s%d/%s_%s_%s", CRASH_DIR, dir, strrchr(APLOG_FILE_BOOT,'/')+1,mode,ts);
    int status;
#ifdef FULL_REPORT
    status = system("/system/bin/logcat -b system -b main -b radio -b events -b kernel -v threadtime -d -f /logs/aplog_boot");
#else
    status = system("/system/bin/logcat -b system -b main -b radio -b events -v threadtime -d -f /logs/aplog_boot");
#endif
    if (status != 0) {
        LOGE("flush ap log from boot returns status: %d.\n", status);
        return;
    }
    if(!stat(APLOG_FILE_BOOT,&info)) {
        do_copy(APLOG_FILE_BOOT,log_boot_name,0);
        remove(APLOG_FILE_BOOT);
    }
    return ;
}

static void do_last_kmsg_copy(int dir)
{
    char destion[PATHMAX];
    struct stat info;

    if(stat(LAST_KMSG, &info) == 0) {
        snprintf(destion, sizeof(destion), "%s%d/%s", CRASH_DIR, dir, LAST_KMSG_FILE);
        do_copy(LAST_KMSG, destion, FILESIZE_MAX);
    }

}

#ifndef FULL_REPORT
static void flush_aplog()
{
    struct stat info;
    int status;

    if(stat(APLOG_FILE_0, &info) == 0){
        remove(APLOG_FILE_0);
    }
    status = system("/system/bin/logcat -b system -b main -b radio -b events -v threadtime -d -f /logs/aplog");
    if (status != 0)
        LOGE("dump logcat returns status: %d.\n", status);
    do_chown(APLOG_FILE_0, PERM_USER, PERM_GROUP);
}
#endif

static void do_log_copy(char *mode, int dir, char* ts, int type)
{
    char destion[PATHMAX];
    struct stat info;

    if(type == APLOG_TYPE){
#ifndef FULL_REPORT
        flush_aplog();
#endif
        if(stat(APLOG_FILE_0, &info) == 0){
            snprintf(destion,sizeof(destion), "%s%d/%s_%s_%s", CRASH_DIR, dir,strrchr(APLOG_FILE_0,'/')+1,mode,ts);
            do_copy(APLOG_FILE_0,destion, FILESIZE_MAX);
            if(info.st_size < 1*1024*1024){
                if(stat(APLOG_FILE_1, &info) == 0){
                    snprintf(destion,sizeof(destion), "%s%d/%s_%s_%s", CRASH_DIR, dir,strrchr(APLOG_FILE_1,'/')+1,mode,ts);
                    do_copy(APLOG_FILE_1,destion, FILESIZE_MAX);
                }
            }
#ifndef FULL_REPORT
                remove(APLOG_FILE_0);
#endif
        }
    }
    if(type == APLOG_STATS_TYPE){
        if(stat(APLOG_FILE_0, &info) == 0){
            snprintf(destion,sizeof(destion), "%s%d/%s_%s_%s", STATS_DIR, dir,strrchr(APLOG_FILE_0,'/')+1,mode,ts);
            do_copy(APLOG_FILE_0,destion, FILESIZE_MAX);
            if(info.st_size < 1*1024*1024){
                if(stat(APLOG_FILE_1, &info) == 0){
                    snprintf(destion,sizeof(destion), "%s%d/%s_%s_%s", STATS_DIR, dir,strrchr(APLOG_FILE_1,'/')+1,mode,ts);
                    do_copy(APLOG_FILE_1,destion, FILESIZE_MAX);
                }
            }
        }
    }
    if(type == BPLOG_TYPE){
        if(stat(BPLOG_FILE_0, &info) == 0){
            snprintf(destion,sizeof(destion), "%s%d/%s_%s_%s%s", CRASH_DIR, dir,strrchr(BPLOG_FILE_0,'/')+1,mode,ts,".istp");
            do_copy(BPLOG_FILE_0,destion, FILESIZE_MAX);
            if(info.st_size < 1*1024*1024){
                if(stat(BPLOG_FILE_1, &info) == 0){
                    snprintf(destion,sizeof(destion), "%s%d/%s_%s_%s%s", CRASH_DIR, dir,strrchr(BPLOG_FILE_1,'/')+1,mode,ts,".istp");
                    do_copy(BPLOG_FILE_1,destion, FILESIZE_MAX);
                }
            }
        }
    }

    return ;
}

static int remove_folder(char* path)
{
    char spath[PATHMAX];
    DIR *d;
    struct dirent* de;

    d = opendir(path);
    if (d == 0) {
         return -1;
    }
    else {
        while ((de = readdir(d)) != 0) {
           if (!strcmp(de->d_name, ".") || !strcmp(de->d_name, ".."))
               continue;
           snprintf(spath, sizeof(spath)-1,  "%s/%s", path, de->d_name);
           if (remove(spath) < 0)
               LOGE("Unable to remove %s", spath);
        }
        closedir(d);
    }
    int status = rmdir(path);
    return status;
}

static int write_file(const char *path, const char *value)
{
    int fd, ret, len;

    fd = open(path, O_WRONLY | O_CREAT, 0622);

    if (fd < 0){
        LOGE("can not open file: %s\n", path);
        return -errno;
    }
    len = strlen(value);

    do {
        ret = write(fd, value, len);
    } while (ret < 0 && errno == EINTR);

    close(fd);
    if (ret < 0) {
        return -errno;
    } else {
        return 0;
    }
}

static int get_version_info(char *fn, char *field, char *buf)
{

    char *data;
    int sz;
    int fd;
    int i = 0;
    int len = -1;
    int p;

    data = 0;
    fd = open(fn, O_RDONLY);
    if (fd < 0){
        LOGE("can not open file: %s\n", fn);
        return 0;
    }

    sz = lseek(fd, 0, SEEK_END);
    if (sz < 0)
        goto oops;

    if (lseek(fd, 0, SEEK_SET) != 0)
        goto oops;

    data = (char *)malloc(sz + 2);
    if (data == 0)
        goto oops;

    if (read(fd, data, sz) != sz)
        goto oops;

    data[sz] = '\n';
    data[sz + 1] = 0;

    while (i < sz) {
        if (data[i] == '=')
            if (i - strlen(field) > 0)
                if (!memcmp(&data[i - strlen(field)], field, strlen(field))) {
                    p = ++i;
                    while ((data[i] != '\n') && (data[i] != 0))
                        i++;
                    len = i - p;
                    if (len > 0) {
                        memcpy(buf, &data[p], len);
                        buf[len] = 0;
                        break;
                    }
                }
        i++;
    }

oops:
    close(fd);
    if (data != 0)
        free(data);
    return len;

}

static int get_uptime(long long *time_ns)
{
    struct timespec ts;
    int fd, result;

    fd = open("/dev/alarm", O_RDONLY);
    if (fd < 0){
        LOGE("can not open file: %s\n", "/dev/alarm");
        return -1;
    }
    result =
        ioctl(fd,
                ANDROID_ALARM_GET_TIME(ANDROID_ALARM_ELAPSED_REALTIME), &ts);
    close(fd);
    *time_ns = (((long long) ts.tv_sec) * 1000000000LL) + ((long long) ts.tv_nsec);
    return 0;
}

static void compute_key(char* key, char *event, char *type)
{
    SHA1_CTX sha;
    char buf[256] = { '\0', };
    long long time_ns=0;
    char *tmp_key = key;
    unsigned char results[SHA1_DIGEST_LENGTH];
    int i;

    get_uptime(&time_ns);
    snprintf(buf, 256, "%s%s%s%s%lld", buildVersion, uuid, event, type, time_ns);

    SHA1Init(&sha);
    SHA1Update(&sha, (unsigned char*) buf, strlen(buf));
    SHA1Final(results, &sha);
    for (i = 0; i < SHA1_DIGEST_LENGTH/2; i++)
    {
        sprintf(tmp_key, "%02x", results[i]);
        tmp_key+=2;
    }
    *tmp_key=0;
}

static void find_file_in_dir(char *name_file_found, char *dir_to_search, char *name_file_to_match)
{
    DIR *d;
    struct dirent* de;
    d = opendir(dir_to_search);
    if(!d) {
        LOGE("%s: Can't open dir %s\n",__FUNCTION__, dir_to_search);
        return;
    }
    while ((de = readdir(d))) {
        const char *name = de->d_name;
        if (strstr(name, name_file_to_match)){
            sprintf(name_file_found, "%s", name);
            break;
        }
    }
    closedir(d);
}

//ARGS for thread creation and copy_dir
struct arg_copy {
    int time_val;
    char orig[PATHMAX];
    char dest[PATHMAX];
};


static void copy_dir(void *arguments)
{
    struct arg_copy *args = (struct arg_copy *)arguments;
    DIR *d;
    struct dirent* de;
    char dir_src[PATHMAX] = { '\0', };
    char dir_des[PATHMAX] = { '\0', };
    int cp_time_val;
    //need a local copy at the beginning
    snprintf(dir_src, sizeof(dir_src), "%s", args->orig);
    snprintf(dir_des, sizeof(dir_des), "%s", args->dest);
    cp_time_val = args->time_val;
    //free parameter the sooner to avoid any possible leak
    free(args);
    d = opendir(dir_src);
    if(!d) {
        LOGE("%s: Can't open dir %s\n",__FUNCTION__, dir_src);
        return;
    }
    if (cp_time_val > 0){
        sleep(cp_time_val);
    }
    while ((de = readdir(d))) {
        //protection for . and .. "default folder"
        if (!strcmp(de->d_name, ".") || !strcmp(de->d_name, ".."))
          continue;
        const char *name = de->d_name;
        char src[PATHMAX] = { '\0', };
        char des[PATHMAX] = { '\0', };
        //TO DO : rework the "/" part
        snprintf(src, sizeof(src), "%s/%s", dir_src,name);
        snprintf(des, sizeof(des), "%s/%s", dir_des,name);
        int status = do_copy(src, des, 0);
        if (status != 0)
            LOGE("copy error for %s.\n",name);
    }
    closedir(d);
}


static void backup_apcoredump(unsigned int dir, char* name, char* path)
{
    char src[512] = { '\0', };
    char des[512] = { '\0', };
    snprintf(src, sizeof(src), "%s", path);
    snprintf(des, sizeof(des), "%s%d/%s", CRASH_DIR, dir, name);
    int status = do_copy(src, des, 0);
    if (status != 0)
        LOGE("backup ap core dump status: %d.\n",status);
    else
        remove(path);
}

static int file_read_value(const char *path, char *value, const char *default_value)
{
    struct stat info;
    FILE *fd;
    int ret = -1;

    if ( stat(path, &info) == 0 ) {
        fd = fopen(path, "r");
        if (fd == NULL){
            LOGE("can not open file: %s\n", path);
            return -1;
        }
        ret = fscanf(fd, "%s", value);
        fclose(fd);
        if (ret == 1)
            return 0;
    }
    if (default_value) {
        strcpy(value, default_value);
        return ret;
    } else {
        return ret;
    }
}

static void write_uid(char* filename, char *uuid_value)
{
    FILE *fd;

    fd = fopen(filename, "w");
    if (fd == NULL){
        LOGE("can not open file: %s\n", filename);
        return;
    }
    fprintf(fd, "%s", uuid_value);
    fclose(fd);
    do_chown(filename, PERM_USER, PERM_GROUP);
}

static void read_proc_uid(char* source, char *filename, char *uid, char* pattern)
{
    char temp_uid[256];

    if ((source && filename && uid && pattern) == 0)
        return;

    if (file_read_value(source, uid, pattern) != 0) {
        write_uid(filename, uid);
        LOGE("%s error\n", source);
        return;
    }
    file_read_value(filename, temp_uid, "");
    if (strcmp(uid, temp_uid) != 0)
        write_uid(filename, uid);
}

static void read_prop_uid(char* source, char *filename, char *uid, char* default_value)
{
    char temp_uid[PROPERTY_VALUE_MAX];

    if ((source && filename && uid && default_value) == 0)
        return;

    file_read_value(filename, uid, default_value);
    if (property_get(source, temp_uid, "") <= 0) {
        LOGE("Property %s not readable\n", source);
        return;
    }
    if (strcmp(uid, temp_uid) != 0) {
        strncpy(uid, temp_uid, sizeof(uid)-1);
        write_uid(filename, temp_uid);
    }
}


static void spid_read_concat(const char *path, char *complete_value)
{
    char temp_spid[5]="XXXX";
    if (file_read_value(path, temp_spid, "XXXX") != 0) {
        LOGE("spid_read_concat : %s error\n", path);
    }
    strncat(complete_value,"-",1);
    strncat(complete_value,temp_spid, sizeof(temp_spid));
}

static void read_sys_spid(char *filename)
{
    char complete_spid[256];
    char temp_spid[5]="XXXX";

    if (filename == 0)
        return;

    if (file_read_value(SYS_SPID_1, temp_spid, "XXXX") != 0) {
        LOGE("%s error\n", SYS_SPID_1);
    }
    snprintf(complete_spid, sizeof(complete_spid), "%s", temp_spid);

    spid_read_concat(SYS_SPID_2,complete_spid);
    spid_read_concat(SYS_SPID_3,complete_spid);
    spid_read_concat(SYS_SPID_4,complete_spid);
    spid_read_concat(SYS_SPID_5,complete_spid);
    spid_read_concat(SYS_SPID_6,complete_spid);

    write_uid(filename, complete_spid);
}

//to get pconfig if it exists
pconfig get_generic_config(char* event_name, pconfig config_to_match) {
    pconfig result = NULL;
    pconfig tmp_config = config_to_match;
    while (tmp_config) {
        if (strstr(event_name, tmp_config->matching_pattern)){
            result = tmp_config;
            break;
        }
        tmp_config = tmp_config->next;
    }
    return result;
}

//to get pconfig if it exists with path argument
pconfig get_generic_config_by_path(char* path_searched, pconfig config_to_match) {
    pconfig result = NULL;
    pconfig tmp_config = config_to_match;
    if (path_searched){
        while (tmp_config) {
            if(strncmp(path_searched, tmp_config->path, strlen(path_searched))==0) {
                result = tmp_config;
                break;
            }
            tmp_config = tmp_config->next;
        }
    }
    return result;
}

//to check if process should be done on generic events
int generic_match(char* event_name, pconfig config_to_match) {
    int result = 0;
    pconfig tmp_comfig = get_generic_config(event_name,config_to_match);
    if (tmp_comfig) {
        result =1;
    }
    return result;
}

//to check if process should be done on generic events with a WD paramer
pconfig generic_match_by_wd(char* event_name, pconfig config_to_match, int wd) {
    pconfig result = NULL;
    pconfig tmp_config = get_generic_config(event_name,config_to_match);
    if (tmp_config) {
        if (tmp_config->wd_config.wd == wd){
            result = tmp_config;
        }
    }
    return result;
}

/*
* Name          : generic_add_watch
* Description   : This function add watcher for generic config loaded
*/
void generic_add_watch(pconfig config_to_watch, int fd){
    pconfig tmp_config = config_to_watch;
    while (tmp_config) {
        if (strlen( tmp_config->path)>0){
            //add watch and store it
            tmp_config->wd_config.wd = inotify_add_watch(fd, tmp_config->path, VBCRASH_DIR_MASK);
            LOGI("generic_add_watch : %s\n", tmp_config->path);
            if (tmp_config->wd_config.wd < 0) {
                LOGE("Can't add watch for %s.\n", tmp_config->path);
            }else{
                //store WD in config WD
                tmp_config->wd_config.mask = VBCRASH_DIR_MASK;
                tmp_config->wd_config.filename = tmp_config->path;
                tmp_config->wd_config.eventname = EXTRA_NAME;
            }
        }
        tmp_config = tmp_config->next;
    }
}

/*
* Name          : free_config
* Description   : This function free the config structure created
*/
void free_config(pconfig first)
{
    pconfig nextconfig;
    pconfig current = first;
    while (current){
        nextconfig = current->next;
        free(current->eventname);
        free(current->matching_pattern);
        free(current);
        current = nextconfig;
    }
    first=NULL;
}

static void build_footprint(char *id)
{
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

    snprintf(id, SIZE_FOOTPRINT_MAX, "%s,", buildVersion);

    property_get(FINGERPRINT_FIELD, prop, "");
    strncat(id, prop, SIZE_FOOTPRINT_MAX);
    strncat(id, ",", SIZE_FOOTPRINT_MAX);

    property_get(KERNEL_FIELD, prop, "");
    strncat(id, prop, SIZE_FOOTPRINT_MAX);
    strncat(id, ",", SIZE_FOOTPRINT_MAX);

    property_get(USER_FIELD, prop, "");
    strncat(id, prop, SIZE_FOOTPRINT_MAX);
    strncat(id, "@", SIZE_FOOTPRINT_MAX);

    property_get(HOST_FIELD, prop, "");
    strncat(id, prop, SIZE_FOOTPRINT_MAX);
    strncat(id, ",", SIZE_FOOTPRINT_MAX);

    read_prop_uid(MODEM_FIELD, MODEM_UUID, prop, "unknown");
    strncat(id, prop, SIZE_FOOTPRINT_MAX);
    strncat(id, ",", SIZE_FOOTPRINT_MAX);

    property_get(IFWI_FIELD, prop, "");
    strncat(id, prop, SIZE_FOOTPRINT_MAX);
    strncat(id, ",", SIZE_FOOTPRINT_MAX);

    property_get(IAFW_VERSION, prop, "");
    strncat(id, prop, SIZE_FOOTPRINT_MAX);
    strncat(id, ",", SIZE_FOOTPRINT_MAX);

    property_get(SCUFW_VERSION, prop, "");
    strncat(id, prop, SIZE_FOOTPRINT_MAX);
    strncat(id, ",", SIZE_FOOTPRINT_MAX);

    property_get(PUNIT_VERSION, prop, "");
    strncat(id, prop, SIZE_FOOTPRINT_MAX);
    strncat(id, ",", SIZE_FOOTPRINT_MAX);

    property_get(VALHOOKS_VERSION, prop, "");
    strncat(id, prop, SIZE_FOOTPRINT_MAX);
}

//This function creates a minimal crashfile (without DATA0, DATA1 and DATA2 fields)
//Note:DATA0 is filled for Modem Panic case only
static void create_minimal_crashfile(char* event, char* type, char* path,
                                     char* key, char* uptime, char* date,
                                     int data_ready)
{
    FILE *fp;
    char fullpath[PATHMAX];
    char mpanicpath[PATHMAX];
    char footprint[SIZE_FOOTPRINT_MAX] = { '\0', };
    char imei[PROPERTY_VALUE_MAX];

    property_get(IMEI_FIELD, imei, "");

    build_footprint(footprint);

    snprintf(fullpath, sizeof(fullpath)-1, "%s/%s", path, CRASHFILE_NAME);

    //Create crashfile
    fp = fopen(fullpath,"w");
    if (fp == NULL)
    {
        LOGE("can not create file: %s\n", fullpath);
        return;
    }
    fclose(fp);
    do_chown(fullpath, PERM_USER, PERM_GROUP);

    fp = fopen(fullpath,"w");
    if (fp == NULL)
    {
        LOGE("can not open file: %s\n", fullpath);
        return;
    }

    //Fill crashfile
    fprintf(fp,"EVENT=%s\n", event);
    fprintf(fp,"ID=%s\n", key);
    fprintf(fp,"SN=%s\n", uuid);
    fprintf(fp,"DATE=%s\n", date);
    fprintf(fp,"UPTIME=%s\n", uptime);
    fprintf(fp,"BUILD=%s\n", footprint);
    fprintf(fp,"BOARD=%s\n", boardVersion);
    fprintf(fp,"IMEI=%s\n", imei);
    fprintf(fp,"TYPE=%s\n", type);
    fprintf(fp,"DATA_READY=%d\n", data_ready);
    //MPANIC crash : fill DATA0 field
    if (!strcmp(MODEM_CRASH,type)){
        LOGI("Modem panic detected : generating DATA0\n");
        FILE *fd_panic;
        DIR *d;
        struct dirent* de;
        char value[PATHMAX] = "";
        d = opendir(path);
        if(!d) {
            LOGE("%s: Can't open dir %s\n",__FUNCTION__, path);
            fclose(fp);
            return;
        }
        while ((de = readdir(d))) {
            const char *name = de->d_name;
            if (strstr(name, "mpanic")){
                snprintf(mpanicpath, sizeof(mpanicpath)-1, "%s/%s", path, name);
                fd_panic = fopen(mpanicpath, "r");
                if (fd_panic == NULL){
                    LOGE("can not open file: %s\n", mpanicpath);
                    break;
                }
                fscanf(fd_panic, "%s", value);
                fclose(fd_panic);
                fprintf(fp,"DATA0=%s\n", value);
                break;
            } else if (strstr(name, "_crashdata")){ //MMGR case
                snprintf(mpanicpath, sizeof(mpanicpath)-1, "%s/%s", path, name);
                fd_panic = fopen(mpanicpath, "r");
                if (fd_panic == NULL){
                    LOGE("can not open file: %s\n", mpanicpath);
                    break;
                }
                fscanf(fd_panic, "%s", value);
                fclose(fd_panic);
                fprintf(fp,"%s\n", value);
                break;
            }
        }
        closedir(d);
    }
    fprintf(fp,"_END\n");
    fclose(fp);
}

#ifdef FULL_REPORT
static void notify_crash_to_upload(char* event_id)
{
    char cmd[512];
    char boot_state[PROPERTY_VALUE_MAX];

    property_get(BOOT_STATUS, boot_state, "-1");
    if (strcmp(boot_state, "1"))
        return;

    snprintf(cmd,sizeof(cmd)-1,"am broadcast -n com.intel.crashreport/.specific.NotificationReceiver -a com.intel.crashreport.intent.CRASH_LOGS_COPY_FINISHED -c android.intent.category.ALTERNATIVE --es %s %s",
             "com.intel.crashreport.extra.EVENT_ID",event_id);
    int status = system(cmd);
    if (status != 0)
        LOGI("notify crashreport status: %d.\n", status);
}
#endif

static void notify_crashreport()
{
    char boot_state[PROPERTY_VALUE_MAX];

    property_get(BOOT_STATUS, boot_state, "-1");
    if (strcmp(boot_state, "1"))
        return;

    int status = system("am broadcast -n com.intel.crashreport/.specific.NotificationReceiver -a com.intel.crashreport.intent.CRASH_NOTIFY -c android.intent.category.ALTERNATIVE");
    if (status != 0)
        LOGI("notify crashreport status: %d.\n", status);
}

static int history_file_updated(char *data, char* filters)
{
    char *p, *p1, *p2, *cmd, *line;
    int i, size, updated = 0;
    char path[PATHMAX];

    if (!data)
        return 0;
    cmd = filters;
    while (cmd) {
       char filter[64]={'\0'};
        p = strchr(cmd, ';');
        if (p) {
            size = p - cmd;
        if ((size > 0) && ((unsigned int) size < sizeof(filter)))
                memcpy(filter, cmd, size);
            cmd = ++p;
            p = strstr(data, filter);
        } else
            cmd = p;
        if (p) {
            line = data;
            for (p1 = p; p1 > data; p1--)
                if (*p1 == '\n') {
                   line = ++p1;
                   break;
            }
            p2 = strstr(line, SDCARD_CRASH_DIR);
            if (!p2)
                p2 = strstr(line, EMMC_CRASH_DIR);
            if (p2) {
                size = p2 - line;
                for (i = 0; i < size; i++)
                    if (line[i] == '\n')
                        break;
                if ((i == size) && !strncmp(line, CRASHEVENT, sizeof(CRASHEVENT)-1)) {
                    p1 = strchr(p2, '\n');
                    if (p1) {
                        size = p1 - p2;
                        if (size > 0 && ((unsigned int) size < sizeof(path)-1)) {
                            strncpy(path, p2, size);
                            path[size] = '\0';
                            if (!remove_folder(path)) {
                                memcpy(line, "DELETE", 6);
                                updated = 1;
                            }
                        }
                    }
                }
            }
        }
    }
    return updated;
}

static void history_file_write_ex(char *event, char *type, char *subtype, char *log, char* lastuptime, char* key, char* date_tmp_2, int data_ready)
{
    char uptime[32];
    struct stat info;
    long long tm=0;
    int hours, seconds, minutes;
    FILE *to;
    char tmp[PATHMAX];
    char * p;

    // compute subtype
    if (!subtype)
        subtype = type;

    // compute uptime
    get_uptime(&tm);
    hours = (int) (tm / 1000000000LL);
    seconds = hours % 60;
    hours /= 60;
    minutes = hours % 60;
    hours /= 60;
    snprintf(uptime,sizeof(uptime),"%04d:%02d:%02d",hours, minutes,seconds);

    if (stat(HISTORY_FILE, &info) != 0) {
        to = fopen(HISTORY_FILE, "w");
        if (to == NULL){
            LOGE("can not open file: %s\n", HISTORY_FILE);
            return;
        }
        do_chmod(HISTORY_FILE, "644");
        do_chown(HISTORY_FILE, PERM_USER, PERM_GROUP);
        fprintf(to, "#V1.0 %-16s%-24s\n", CURRENT_UPTIME, uptime);
        fprintf(to, "#EVENT  ID                    DATE                 TYPE\n");
        fclose(to);
    }

    if (log != NULL) {
        snprintf(tmp, sizeof(tmp), "%s", log);
        if((p = strrchr(tmp,'/'))){
            p[0] = '\0';
        }
        to = fopen(HISTORY_FILE, "a");
        if (to == NULL){
            LOGE("can not open file: %s\n", HISTORY_FILE);
            return;
        }
        fprintf(to, "%-8s%-22s%-20s%s %s\n", event, key, date_tmp_2, type, tmp);
        fclose(to);
        if (!strncmp(event, CRASHEVENT, sizeof(CRASHEVENT)))
            create_minimal_crashfile(event, subtype, tmp, key, uptime, date_tmp_2, data_ready);
        else if(!strncmp(event, BZEVENT, sizeof(BZEVENT)))
            create_minimal_crashfile(event, BZMANUAL, tmp, key, uptime, date_tmp_2, data_ready);
        else if(!strncmp(event, INFOEVENT, sizeof(INFOEVENT)) &&
                !strncmp(type, "FIRMWARE", sizeof("FIRMWARE")))
            create_minimal_crashfile(event, type, tmp, key, uptime, date_tmp_2, data_ready);
    } else if (type != NULL) {

        to = fopen(HISTORY_FILE, "a");
        if (to == NULL){
            LOGE("can not open file: %s\n", HISTORY_FILE);
            return;
        }
        if (lastuptime != NULL)
            fprintf(to, "%-8s%-22s%-20s%-16s %s\n", event, key, date_tmp_2, type, lastuptime);
        else
            fprintf(to, "%-8s%-22s%-20s%-16s\n", event, key, date_tmp_2, type);
        fclose(to);
    } else {

        to = fopen(HISTORY_FILE, "a");
        if (to == NULL){
            LOGE("can not open file: %s\n", HISTORY_FILE);
            return;
        }
        fprintf(to, "%-8s%-22s%-20s%s\n", event, key, date_tmp_2, lastuptime);
        fclose(to);
    }
    return;
}

//proxy function for history_file_write_ex with data ready parameter
static void history_file_write(char *event, char *type, char *subtype, char *log, char* lastuptime, char* key, char* date_tmp_2)
{
    history_file_write_ex(event,type,subtype,log,lastuptime,key,date_tmp_2,1);
}


static int del_file_more_lines(char *fn)
{
    char *data;
    int sz;
    int fd, i;
    int count = 0;
    int tmp = 0;
    int dest = 0;
    data = 0;
    fd = open(fn, O_RDWR);
    if (fd < 0){
        LOGE("can not open file: %s\n", fn);
        return 0;
    }

    sz = lseek(fd, 0, SEEK_END);
    if (sz < 0) {
        close(fd);
        return 0;
    }

    if (lseek(fd, 0, SEEK_SET) != 0) {
        close(fd);
        return 0;
    }

    data = (char *)malloc(sz + 2);
    if (data == 0) {
        close(fd);
        return 0;
    }

    if (read(fd, data, sz) != sz) {
        close(fd);
        if (data != 0)
            free(data);
        return 0;
    }

    close(fd);

    data[sz] = '\n';
    data[sz + 1] = 0;

    for (i = 0; i < sz; i++)
        if (data[i] == '\n')
            count++;

    if (count >= MAX_RECORDS + SAVEDLINES) {

        count = count - (MAX_RECORDS >> 1);
        for (i = 0; i < sz; i++) {
            if (data[i] == '\n') {
                tmp++;
                if (tmp == SAVEDLINES)
                    dest = i;
                if (tmp >= count)
                    break;
            }
        }
        memcpy(&data[dest + 1], &data[i + 1], sz - i - 1);
        fd = open(fn, O_RDWR | O_TRUNC);
        if (fd < 0) {
            free(data);
            LOGE("can not open file: %s\n", fn);
            return 0;
        }

        if (write(fd, &data[0], sz - i - 1 + dest + 1) !=
                (sz - i - 1 + dest + 1)) {
            close(fd);
            free(data);
            return 0;
        }
        close(fd);
    }

    if (data != 0)
        free(data);
    return 0;
}

static void restart_profile1_srv(void)
{
    char value[PROPERTY_VALUE_MAX];

    property_get(PROP_PROFILE, value, "");
    if (!strncmp(value, "1", 1)){
        property_set("ctl.start", "profile1_rest");
    }
}

static void restart_profile2_srv(void)
{
    char value[PROPERTY_VALUE_MAX];

    property_get(PROP_PROFILE, value, "");
    if (!strncmp(value, "2", 1)){
        property_set("ctl.start", "profile2_rest");
    }
}

static void init_profile_srv(void)
{
    char value[PROPERTY_VALUE_MAX];

    property_get(PROP_PROFILE, value, "");
    if (!strncmp(value, "1", 1)){
        property_set("ctl.start", "profile1_init");
    }
    if (!strncmp(value, "2", 1)){
        property_set("ctl.start", "profile2_init");
    }
}

static void start_dumpstate_srv(char* crash_dir, int crashseq) {
    char dumpstate_dir[PATHMAX];
    if (crash_dir == NULL) return;
    snprintf(dumpstate_dir, sizeof(dumpstate_dir), "%s%d/", crash_dir, crashseq);
    property_set("crashlogd.storage.path", dumpstate_dir);
    property_set("ctl.start", "logsystemstate");
}

static int mv_modem_crash(char *spath, char *dpath)
{

    char src[512] = { '\0', };
    char des[512] = { '\0', };
    struct stat st;
    DIR *d;
    struct dirent *de;

    if (stat(spath, &st))
        return -1;
    if (stat(dpath, &st))
        return -1;

    d = opendir(spath);
    if (d == 0) {
        LOGE("opendir failed, %s\n", strerror(errno));
        return -1;
    }
    while ((de = readdir(d)) != 0) {
        if (!strcmp(de->d_name, ".") || !strcmp(de->d_name, ".."))
            continue;
        if (strstr(de->d_name, "cd") && strstr(de->d_name, ".tar.gz")){
            snprintf(src, sizeof(src), "%s/%s", spath, de->d_name);
            snprintf(des, sizeof(des), "%s/%s", dpath, de->d_name);
            do_copy(src, des, 0);
            remove(src);
        }
    }
    if (closedir(d) < 0){
        LOGE("closedir failed, %s\n", strerror(errno));
        return -1;
    }
    return 0;

}


/**
* @brief structure containing directories watched by crashlogd
*
* This structure contains every watched directories, with the itnotify mask value, and the associated
* filename that should trigger a processing
* note: a watched directory can only have one mask value
*/
struct wd_name wd_array[] = {
    /* -------------Warning: if table is updated, don't forget to update also WDCOUNT and WDCOUNT_START in main function--- */
    {0, DROPBOX_DIR_MASK, SYSSERVER_WDT, DROPBOX_DIR, "system_server_watchdog"},
    {0, DROPBOX_DIR_MASK, ANR_CRASH, DROPBOX_DIR, "anr"},
    {0, TOMBSTONES_DIR_MASK, TOMB_CRASH, "/data/tombstones", "tombstone"},
    {0, DROPBOX_DIR_MASK, JAVA_CRASH, DROPBOX_DIR, "crash"},
    {0, DROPBOX_DIR_MASK, LOST , DROPBOX_DIR, ".lost"}, /* for full dropbox */
#ifdef FULL_REPORT
    {0, CORE_DIR_MASK, AP_COREDUMP ,"/logs/core", ".core"},
    {0, CORE_DIR_MASK, HPROF, "/logs/core", ".hprof"},
    {0, STATS_DIR_MASK, STATSTRIGGER, "/logs/stats", "_trigger"},
    {0, APLOGS_DIR_MASK, CMDTRIGGER, "/logs/aplogs", "_cmd"},
#endif
    {0, APLOGS_DIR_MASK, APLOGTRIGGER, "/logs/aplogs", "_trigger"},
    /* -----------------------------above is dir, below is file------------------------------------------------------------ */
    {0, UPTIME_FILE_MASK, CURRENT_UPTIME, "/logs/uptime", ""},
    /* -------------------------above is AP, below is modem---------------------------------------------------------------- */
    {0, MODEMCRASH_DIR_MASK, MODEM_CRASH ,"/logs/modemcrash", "mpanic.txt"},/*for modem crash */
    {0, MODEMCRASH_DIR_MASK, AP_INI_M_RST ,"/logs/modemcrash", "apimr.txt"},
    {0, MODEMCRASH_DIR_MASK, M_RST_WN_COREDUMP ,"/logs/modemcrash", "mreset.txt"},
};


int WDCOUNT = ((int)(sizeof(wd_array)/sizeof(struct wd_name)));

 /**
 * @brief returns the watch descriptor of a watched directory declared in 'wd_name' array
 *
 * @param[in] dir_name : input directory path as declared to inotify watcher
 *
 * @retval returns the watch descriptor value the input directory is associated with (otherwise it returns -1)
 */
int get_watched_directory_wd( char *dir_name)
{
    int i;

    for (i = WDCOUNT_START; i < WDCOUNT; i++) {
        if (!strncmp( wd_array[i].filename, dir_name, sizeof(wd_array[i].filename)) ) {
            return wd_array[i].wd;
        }
    }
    return -1;
}

#ifdef FULL_REPORT

void prepare_anr_or_uiwdt(char *destion)
{
    char cmd[PATHMAX];
    if (!strcmp(".gz", &destion[strlen(destion) - 3])) {
    /* extract gzip file */
        snprintf(cmd, sizeof(cmd), "gunzip %s", destion);
        system(cmd);
        destion[strlen(destion) - 3] = 0;
        do_chown(destion, PERM_USER, PERM_GROUP);

    }

}

void process_anr_or_uiwdt_tracefile(char *destion, int dir, int remove_path)
{
    char cmd[PATHMAX];
    int src, dest;
    char dest_path[PATHMAX];
    char dest_path_symb[PATHMAX];
    struct stat stat_buf;
    char *tracefile;
    FILE *fp;
    int i;

    fp = fopen(destion, "r");
    if (fp == NULL) {
        LOGE("Failed to open file %s:%s\n", destion, strerror(errno));
        return;
    }
    /* looking for "Trace file:" from the first 100 lines */
    for (i = 0; i < 100; i++) {
        if (fgets(cmd, sizeof(cmd), fp)) {
            if (!strncmp("Trace file:", cmd, 11)) {
                tracefile = cmd + 11;
                tracefile[strlen(tracefile) - 1] = 0; /* eliminate trailing \n */
                // copy
                snprintf(dest_path,sizeof(dest_path),"%s%d/trace_all_stack.txt", CRASH_DIR, dir);
                src = open(tracefile, O_RDONLY);
                if (src < 0) {
                    LOGE("Failed to open file %s:%s\n", tracefile, strerror(errno));
                    break;
                }
                fstat(src, &stat_buf);
                dest = open(dest_path, O_WRONLY|O_CREAT, 0600);
                close(dest);
                do_chmod(dest_path, "600");
                do_chown(dest_path, PERM_USER, PERM_GROUP);
                dest = open(dest_path, O_WRONLY, stat_buf.st_mode);
                if (dest < 0) {
                    LOGE("Failed to open file %s:%s\n", dest_path, strerror(errno));
                    close(src);
                    break;
                }
                sendfile(dest, src, NULL, stat_buf.st_size);
                close(src);
                close(dest);
                // remove src file
                if (unlink(tracefile) != 0) {
                    LOGE("Failed to remove file %s:%s\n", tracefile, strerror(errno));
                }
                // parse

                backtrace_parse_tombstone_file(dest_path);

                if ((remove_path > 0) && unlink(dest_path) != 0) {
                    LOGE("Failed to remove file %s:%s\n", dest_path, strerror(errno));
                }
                break;
            }
        }
    }
    fclose(fp);
    do_chown(dest_path, PERM_USER, PERM_GROUP);
    snprintf(dest_path_symb, sizeof(dest_path_symb), "%s_symbol", dest_path);
    do_chown(dest_path_symb, PERM_USER, PERM_GROUP);
}

void compress_aplog_folder(char *folder_path)
{
    char cmd[PATHMAX];
    char spath[PATHMAX];
    DIR *d;
    struct dirent* de;

    snprintf(cmd, sizeof(cmd), "gzip %s/[ab]plog*", folder_path);
    system(cmd);

    d = opendir(folder_path);
    if (d == 0) {
         return;
    }
    else {
        while ((de = readdir(d)) != 0) {
           if (!strcmp(de->d_name, ".") || !strcmp(de->d_name, ".."))
               continue;
           snprintf(spath, sizeof(spath)-1,  "%s/%s", folder_path, de->d_name);
           do_chown(spath, PERM_USER, PERM_GROUP);
        }
        closedir(d);
    }

}

static int get_str_in_file(char *file, char *keyword, char *result, unsigned int sizemax)
{
    char buffer[PATHMAX];
    int rc = -1;
    FILE *fd1;
    struct stat info;

    if (stat(file, &info) < 0)
        return -1;

    if (keyword == NULL)
        return -1;

    fd1 = fopen(file,"r");
    if(fd1 == NULL){
        LOGE("can not open file: %s\n", file);
        return -1;
    }

    while(!feof(fd1)){
        if (fgets(buffer, sizeof(buffer), fd1) != NULL){
            if (keyword && strstr(buffer,keyword)){
                unsigned int buflen = strlen(buffer);
                if((buflen > 0) && (buffer[buflen-1]) == '\n')
                    buffer[--buflen]= '\0';
                if (buflen > sizemax + strlen(keyword))
                    return -1;
                unsigned int size = buflen - strlen(keyword);
                if (size < sizemax) {
                    strncpy(result,buffer+strlen(keyword),size);
                    rc = 0;
                    break;
                }
                else
                  return -1;
            }
        }
    }

    if (fd1 != NULL)
        fclose(fd1);

    return rc;
}

void backtrace_anr_uiwdt(char *dest, int dir)
{
    char value[PROPERTY_VALUE_MAX];
    property_get(PROP_ANR_USERSTACK, value, "0");
    if (strncmp(value, "1", 1)) {
        process_anr_or_uiwdt_tracefile(dest, dir, 0);
    }
}

void monitor_crashenv(void)
{
    int status = system("/system/bin/monitor_crashenv");
    if (status != 0)
        LOGE("monitor_crashenv status: %d.\n", status);
    return ;
}

void check_running_power_service()
{
    char powerservice[PROPERTY_VALUE_MAX];
    char powerenable[PROPERTY_VALUE_MAX];

    property_get("init.svc.profile_power", powerservice, "");
    property_get("persist.service.power.enable", powerenable, "");
    if (strcmp(powerservice, "running") && !strcmp(powerenable, "1")) {
        LOGE("power service stopped whereas property is set .. restarting\n");
        property_set("ctl.start", "profile_power");
    }
}
#else
void prepare_anr_or_uiwdt(char *destion)
{
    char cmd[PATHMAX];

    if (!strcmp(".gz", &destion[strlen(destion) - 3])) {
        /* extract gzip file */
        do_copy(destion,"/logs/tmp_anr_uiwdt.gz",0);
        system("gunzip /logs/tmp_anr_uiwdt.gz");
        do_chown("/logs/tmp_anr_uiwdt", PERM_USER, PERM_GROUP);
        destion[strlen(destion) - 3] = 0;
        do_copy("/logs/tmp_anr_uiwdt",destion,0);
        remove("/logs/tmp_anr_uiwdt");
    }
}
void monitor_crashenv(void) {}
void backtrace_anr_uiwdt(char *dest, int dir) {}
void check_running_power_service() {}
void compress_aplog_folder(char *folder_path) {}
static int get_str_in_file(char *file, char *keyword, char *result, unsigned int sizemax) {return -1;}
#endif

void init_mmgr_cli(void)
{
    LOGD("init_mmgr_cli begin");
    init_mmgr_cli_source();

    LOGD("init_mmgr_cli end");
}

static int crashlog_raise_infoerror(char *type, char *subtype)
{
    char date_tmp_2[32];
    time_t t;
    struct tm *time_tmp;
    char key[SHA1_DIGEST_LENGTH+1];

    time(&t);
    time_tmp = localtime((const time_t *)&t);
    PRINT_TIME(date_tmp_2, TIME_FORMAT_2, time_tmp);
    // compute crash id
    compute_key(key, type, subtype);

    LOGE("%-8s%-22s%-20s%s\n", type, key, date_tmp_2, subtype);
    unlink(LOGRESERVED);
    history_file_write(type, subtype, NULL, LOGINFO_DIR, NULL, key, date_tmp_2);
    del_file_more_lines(HISTORY_FILE);
    monitor_crashenv();
    notify_crashreport();
    return 0;
}

void check_crashlog_dead()
{
    char token[PROPERTY_VALUE_MAX];
    property_get("crashlogd.token", token, "");
    if ((strlen(token) < 4)) {
         strcat(token, "1");
         property_set("crashlogd.token", token);
         if (!strncmp(token, "11", 2))
             crashlog_raise_infoerror(ERROREVENT, CRASHLOG_ERROR_DEAD);
    }
}

long get_sd_size()
{
    FILE *fd;
    long l_size=0;
    //first calculate SDSIZE folder with the result of a du command
    int status = system(SDSIZE_SYSTEM_CMD);
    if (status != 0){
        LOGE("get_sd_size status: %d.\n", status);
    }
    //then read the result
    fd = fopen(SDSIZE_CURRENT_LOG, "r");
    if (fd == NULL){
        LOGE("can not open file: %s\n", SDSIZE_CURRENT_LOG);
        //if size could not be calculated, we consider size at zero value
        return 0;
    }
    if (fscanf(fd, "%ld", &l_size)==EOF) {
        l_size = 0;
    }
    if (fd != NULL){
        fclose(fd);
    }
    return l_size;
}

int sdcard_allowed()
{
    //now check remain size on SD
    if (get_sd_size() > current_sd_size_limit){
        LOGE("SD not allowed - current_sd_size_limit reached: %ld.\n", current_sd_size_limit);
        return 0;
    }else{
        return 1;
    }
}

#define CRASH_MODE 0
#define CRASH_MODE_NOSD 1
#define STATS_MODE 2
#define APLOGS_MODE 3
#define BZ_MODE 4
static void sdcard_available(int mode)
{
    struct stat info;
    char value[PROPERTY_VALUE_MAX];
    DIR *d;

    CRASH_DIR = EMMC_CRASH_DIR;
    STATS_DIR = EMMC_STATS_DIR;
    APLOGS_DIR = EMMC_APLOGS_DIR;
    BZ_DIR = EMMC_BZ_DIR;

#ifndef FULL_REPORT
    return;
#else


    property_get(PROP_CRASH_MODE, value, "");
    if ((!strncmp(value, "lowmemory", 9)) || (mode == CRASH_MODE_NOSD) || !sdcard_allowed())
        return;

    if ((stat(SDCARD_LOGS_DIR, &info) == 0) && (d = opendir(SDCARD_LOGS_DIR))){
        CRASH_DIR = SDCARD_CRASH_DIR;
        STATS_DIR = SDCARD_STATS_DIR;
        APLOGS_DIR = SDCARD_APLOGS_DIR;
        BZ_DIR = SDCARD_BZ_DIR;
    } else {
        mkdir(SDCARD_LOGS_DIR, 0777);
        if ((stat(SDCARD_LOGS_DIR, &info) == 0) && (d = opendir(SDCARD_LOGS_DIR))){
            CRASH_DIR = SDCARD_CRASH_DIR;
            STATS_DIR = SDCARD_STATS_DIR;
            APLOGS_DIR = SDCARD_APLOGS_DIR;
            BZ_DIR = SDCARD_BZ_DIR;
        }
    }
    if (d) {
        closedir(d);
    }
    return;
#endif
}

static unsigned int find_dir(unsigned int max, int mode)
{
    struct stat sb;
    char path[PATHMAX];
    unsigned int i, oldest = 0;
    FILE *fd;
    DIR *d;
    struct dirent *de;
    struct stat st;
    char *dir;

    sdcard_available(mode);


    switch(mode){
    case CRASH_MODE:
    case CRASH_MODE_NOSD:
        snprintf(path, sizeof(path), CRASH_CURRENT_LOG);
        break;
    case APLOGS_MODE:
        snprintf(path, sizeof(path), APLOGS_CURRENT_LOG);
        break;
    case BZ_MODE:
        snprintf(path, sizeof(path), BZ_CURRENT_LOG);
        break;
    default:
        snprintf(path, sizeof(path), STATS_CURRENT_LOG);
        break;
    }

    if ((!stat(path, &sb))) {
        fd = fopen(path, "r");
        if (fd == NULL){
            LOGE("can not open file: %s\n", path);
            goto out_err;
        }
        if (fscanf(fd, "%4d", &i)==EOF) {
            i = 0;
        }
        fclose(fd);
        i = i % MAX_DIR;
        oldest = i++;
        fd = fopen(path, "w");
        if (fd == NULL){
            LOGE("can not open file: %s\n", path);
             goto out_err;
        }
        fprintf(fd, "%4d", (i % max));
        fclose(fd);
    } else {
        if (errno == ENOENT){
            LOGE("File %s does not exist, returning to crashlog folder 0.\n",path);
        } else {
            LOGE("Other error : %d.\n", errno);
            //need to return -1 to avoid overwrite old log folder
            goto out_err;
        }

        fd = fopen(path, "w");
        if (fd == NULL){
            LOGE("can not open file: %s\n", path);
            goto out_err;
        }
        oldest = 0;
        fprintf(fd, "%4d", 1);
        fclose(fd);
    }

    /* we didn't find an available file, so we clobber the oldest one */
    switch(mode){
    case CRASH_MODE_NOSD:
    case CRASH_MODE:
        dir = CRASH_DIR;
        break;
    case APLOGS_MODE:
        dir = APLOGS_DIR;
        break;
    case BZ_MODE:
        dir = BZ_DIR;
        break;
    default:
        dir = STATS_DIR;
        break;
    }
    snprintf(path, sizeof(path),  "%s%d", dir, oldest);
    if (stat(path, &st) < 0)
        mkdir(path, 0777);
    else{
        d = opendir(path);
        if (d == 0) {
            LOGE("opendir failed, %s\n", strerror(errno));
            goto out_err;
       }
        while ((de = readdir(d)) != 0) {
            if (!strcmp(de->d_name, ".") || !strcmp(de->d_name, ".."))
                continue;
            snprintf(path, sizeof(path),  "%s%d/%s", dir, oldest,
                    de->d_name);
            remove(path);
        }
        if (closedir(d) < 0){
            LOGE("closedir failed, %s\n", strerror(errno));
            goto out_err;
        }
        rmdir(path);
        snprintf(path, sizeof(path),  "%s%d", dir, oldest);
        mkdir(path, 0777);
    }
    if ((d = opendir(path)) == 0) {
       LOGE("Can not create dir %s", path);
       goto out_err;
    }
    closedir(d);

    if (!strstr(path, "sdcard"))
        do_chown(path, PERM_USER, PERM_GROUP);

    goto out;

out_err:
    oldest = -1;
    crashlog_raise_infoerror(ERROREVENT, CRASHLOG_ERROR_PATH);
out:
    return oldest;
}

static int ishex(char c)
{
   return ((c >= '0' && c <= '9') ||
      (c >= 'a' && c <= 'f') ||
      (c >= 'A' && c <= 'F'));
}

/*
* Name          : checkEvents
* Description   : This function checks the expected format for a list of events
*                 A list of events is 20 hex characters cut by a separator ';'
*                 if the size of the list exceeds the max, the command is not valid
* Parameters    :
*   char *list            -> string containing a list of events*/
static int checkEvents(char* list)
{
    unsigned int i, j;
    for(j=0; j < strlen(list) ; j+=21) {
        for (i=0; i < 20; i++) {
            if ((!ishex(list[i+j])))
                return 0;
        }
        if (list[j+i] != ';')
            return 0;
    }
   return 1;
}

static int do_screenshot_copy(char* bz_description, char* bzdir){
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
                    do_copy(screenshot,destion,0);
                }
            }
        }
    }

    if (fd1 != NULL)
        fclose(fd1);

    return 0;
}

static int find_str_in_file(char *file, char *keyword, char *tail)
{
    char buffer[4*1024];
    int rc = 0;
    FILE *fd1;
    struct stat info;
    int buflen;

    if (stat(file, &info) < 0)
        return -1;

    if (keyword == NULL)
        return -1;

    fd1 = fopen(file,"r");
    if(fd1 == NULL){
        LOGE("can not open file: %s\n", file);
        goto out_err;
    }
    while(!feof(fd1)){
        if (fgets(buffer, sizeof(buffer), fd1) != NULL){
            if (keyword && strstr(buffer,keyword)){
                if (!tail){
                    rc = 0;
                    goto out;
                } else{
                    int buflen = strlen(buffer);
                    int str2len = strlen(tail);
                    if ((buflen > str2len) && (!strncmp(&(buffer[buflen-str2len-1]), tail, strlen(tail)))){
                        rc = 0;
                        goto out;
                    }
                }
            }
        }
    }

out_err:
    rc = -1;
out:
    if (fd1 != NULL)
        fclose(fd1);

    return rc;
}

/*
* Name          : clean_crashlog_in_sd
* Description   : clean legacy crash log folder
* Parameters    :
*   char *dir_to_search       -> name of the folder
*   int max                   -> max number of folder to remove*/
static void clean_crashlog_in_sd(char *dir_to_search, int max)
{
    char path[PATHMAX];
    DIR *d;
    struct dirent* de;
    int i = 0;

    d = opendir(dir_to_search);
    if (d) {
        while ((de = readdir(d))) {
            const char *name = de->d_name;
            snprintf(path, sizeof(path)-1, "%s/%s", dir_to_search, name);
            if ((strstr(path, SDCARD_CRASH_DIR) ||
               (strstr(path, SDCARD_STATS_DIR)) ||
               (strstr(path, SDCARD_APLOGS_DIR)) ))
                if (find_str_in_file(HISTORY_FILE, path, NULL)) {
                    if  (remove_folder(path) < 0)
                        LOGE("failed to remove folder %s", path);
                    i++;
                    if (i >= max)
                       break;
                }
            }
            closedir(d);
    }
    // abort clean of legacy log folder when there is no more folders expect the ones in history_event
    abort_clean_sd = (i < max);
}

/*
* Name          : update_history_on_cmd_delete
* Description   : This function updates the history_event on a CMDDELETE command
*                 The line of the history event containing one of events list is updated:
*                 The CRASH keyword is replaced by DELETE keyword
*                 The crashlog folder is removed
* Parameters    :
*   char *events          -> list of events */
static int update_history_on_cmd_delete(char* events)
{
    char *data = NULL;
    int fd, sz;

    fd = open(HISTORY_FILE, O_RDWR);
    if (fd < 0){
        LOGE("can not open file: %s\n", HISTORY_FILE);
        return 0;
    }

    sz = lseek(fd, 0, SEEK_END);
    if (sz < 0) {
        close(fd);
        return 0;
    }

    if (lseek(fd, 0, SEEK_SET) != 0) {
        close(fd);
        return 0;
    }

    data = (char *)malloc(sz + 2);
    if (data == 0) {
        close(fd);
        return 0;
    }
    if (read(fd, data, sz) != sz) {
        close(fd);
        if (data != 0)
            free(data);
        return 0;
    }

    close(fd);

    data[sz] = '\n';
    data[sz + 1] = 0;

    // if the command is performed successfully, the history file is updated
    if (history_file_updated(data, events)) {
          fd = open(HISTORY_FILE, O_RDWR | O_TRUNC);
          if (fd < 0) {
              free(data);
              LOGE("can not open file: %s\n", HISTORY_FILE);
              return 0;
          }

          if (write(fd, &data[0], sz) != sz) {
              close(fd);
              free(data);
              return 0;
          }
          close(fd);
    }
    else {
        if (data != 0)
            free(data);
        return 0;
    }
    if (data != 0)
         free(data);
    return 1;
}

/*
* Name          : get_formated_times
* Description   : set given strings to current date/time under 2 differents formats
* Parameters    :
* char *format_1_time   -> set to format defined in TIME_FORMAT_1 (used for naming files )
* char *format_2_time   -> set to format defined in TIME_FORMAT_2 (used for displayed messages or be written in files)*/
void get_formated_times(char *format_1_time, char *format_2_time)
{
    time_t t;
    struct tm *time_tmp;
    //Define Epoch variable
    struct tm EPOCH = {0, 0, 0, 1, 0, 0, 1, 0, 0, 0, ""};

    time(&t);
    time_tmp = localtime((const time_t *)&t);

    if (time_tmp == NULL) {
        time_tmp = &EPOCH;
        LOGE("can't retrieve current time");
    }
    PRINT_TIME(format_1_time, TIME_FORMAT_1, time_tmp);
    PRINT_TIME(format_2_time, TIME_FORMAT_2, time_tmp);
}

/*
* Name          : process_hprof_event
* Description   : processes hprof events
* Parameters    :
*   char *filename        -> path of watched directory/file
*   char *name            -> name of the file inside the watched directory that has triggered the event
*   unsigned int files    -> nb max of logs destination directories (crashlog, aplogs, bz... )*/
void process_hprof_event(char *filename,  char *name, unsigned int files)
{
    char date_tmp[32];
    char date_tmp_2[32];
    char key[SHA1_DIGEST_LENGTH+1];
    int dir;
    char path[PATHMAX];
    char destion[PATHMAX];

    get_formated_times(date_tmp,date_tmp_2);
    compute_key(key, CRASHEVENT, HPROF);

    dir = find_dir(files,CRASH_MODE);
    if (dir == -1) {
        LOGE("find dir %d for hprof failed\n", files);
        return;
    }
    /*copy hprof file*/
    snprintf(path, sizeof(path),"%s/%s",filename,name);
    snprintf(destion,sizeof(destion),"%s%d/%s", CRASH_DIR,dir,name);
    do_copy(path, destion, 0);
    remove(path);
    snprintf(destion,sizeof(destion),"%s%d/",CRASH_DIR,dir);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, date_tmp_2, HPROF, destion);
    history_file_write(CRASHEVENT, HPROF, NULL, destion, NULL, key, date_tmp_2);
    del_file_more_lines(HISTORY_FILE);
    notify_crashreport();
}

/*
* Name          : process_modem_reset_event
* Description   : processes modem reset events
* Parameters    :
*   char *filename        -> path of watched directory/file
*   char *eventname       -> name of the watched event
*   char *name            -> name of the file inside the watched directory that has triggered the event
*   unsigned int files    -> nb max of logs destination directories (crashlog, aplogs, bz... )*/
void process_modem_reset_event(char *filename, char *eventname, char *name,  unsigned int files)
{
    char date_tmp[32];
    char date_tmp_2[32];
    char key[SHA1_DIGEST_LENGTH+1];
    int dir;
    char path[PATHMAX];
    char destion[PATHMAX];
    struct stat info;

    get_formated_times(date_tmp,date_tmp_2);
    compute_key(key, CRASHEVENT, eventname);

    dir = find_dir(files,CRASH_MODE);
    if (dir == -1) {
        LOGE("find dir %d for modem reset failed\n", files);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, date_tmp_2, eventname);
        history_file_write(CRASHEVENT, eventname, NULL, NULL, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        notify_crashreport();
        return;
    }

    snprintf(path, sizeof(path), "%s/%s", filename, name);
    if((stat(path, &info) == 0) && (info.st_size != 0)){
        snprintf(destion,sizeof(destion),"%s%d/%s", CRASH_DIR,dir, name);
        do_copy(path, destion, FILESIZE_MAX);
    }
    snprintf(destion,sizeof(destion),"%s%d/", CRASH_DIR,dir);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, date_tmp_2, eventname, destion);
    usleep(TIMEOUT_VALUE);
    do_log_copy(eventname,dir,date_tmp,APLOG_TYPE);
    do_log_copy(eventname,dir,date_tmp,BPLOG_TYPE);
    history_file_write(CRASHEVENT, eventname, NULL, destion, NULL, key, date_tmp_2);
    del_file_more_lines(HISTORY_FILE);
    notify_crashreport();
}


/*
* Name          : str_simple_replace
* Description   : replace the searched sequence by the replace sequence
*                   warning : it only works with sequence with the same size
* Parameters    :
*   char *str        -> full string to be processed
*   char *search            -> sequence to be replaced
*   char * replace    ->  string sequence used to replace searched sequence
* @return 0 on success, -1 on error or nothing to replace. */
int str_simple_replace(char *str, char *search, char *replace)
{
    char *f;
    //warning search and replace should have the same size
    if (strlen(search)!= strlen(replace)){
        LOGE("str_simple_replace : size error");
        return -1;
    }
    f = strstr(str, search);
    if (f != NULL) {
        strncpy(f,replace,strlen(replace));
        return 0;
    }else{
        //nothing to replace => error
        return -1;
    }
}


/*
* Name          : process_modem_generic
* Description   : processes modem generic events
* Parameters    :
*   char *filename        -> path of watched directory/file
*   char *eventname       -> name of the watched event
*   char *name            -> name of the file inside the watched directory that has triggered the event
*   unsigned int files    -> nb max of logs destination directories (crashlog, aplogs, bz... )
*   int fd                -> file descriptor referring to the inotify instance */
void process_modem_generic(char *filename, char *name,  unsigned int files, int fd) {

    char date_tmp[32];
    char date_tmp_2[32];
    char key[SHA1_DIGEST_LENGTH+1];
    int dir;
    char path[PATHMAX];
    char path_linked[PATHMAX];
    char name_linked[PATHMAX];
    char destion[PATHMAX];
    char event_class[PATHMAX];
    FILE *fp;
    char fullpath[PATHMAX];
    int event_mode;
    int wd;
    int ret = 0;
    int data_ready = 1;
    int generate_data = 0;
    pthread_t thread;
    pconfig linkedConfig=NULL;

    pconfig curConfig = get_generic_config(name,first_modem_config);
    if(!curConfig){
        LOGE("no generic configuration found\n");
        return;
    }
    //select event type
    if (curConfig->event_class == 0){
        strncpy(event_class,CRASHEVENT, sizeof(event_class));
        event_mode = CRASH_MODE;
    }else if (curConfig->event_class == 1){
        strncpy(event_class,ERROREVENT, sizeof(event_class));
        event_mode = STATS_MODE;
    }else if (curConfig->event_class == 2){
        strncpy(event_class,INFOEVENT, sizeof(event_class));
        event_mode = STATS_MODE;
    }else{
        //default mode = crash mode
        strncpy(event_class,CRASHEVENT, sizeof(event_class));
        event_mode = CRASH_MODE;
    }
    //adding security NULL character
    event_class[sizeof(event_class)-1] = '\0';
    get_formated_times(date_tmp,date_tmp_2);
    compute_key(key, event_class, curConfig->eventname);
    snprintf(path, sizeof(path),"%s/%s",filename,name);
    dir = find_dir(files,event_mode);
    if (dir == -1) {
        LOGE("find dir %d for modem generic crash failed\n", files);
        LOGE("%-8s%-22s%-20s%s\n", event_class, key, date_tmp_2, curConfig->eventname);
        history_file_write(event_class, curConfig->eventname, NULL, NULL, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        remove(path);
        notify_crashreport();
        return;
    }
    //update event_dir should be done after find_dir call
    if (event_mode == STATS_MODE){
        snprintf(destion,sizeof(destion),"%s%d/", STATS_DIR,dir);
    }else if (event_mode == CRASH_MODE) {
        snprintf(destion,sizeof(destion),"%s%d/", CRASH_DIR,dir);
    }

    LOGE("%-8s%-22s%-20s%s %s\n", event_class, key, date_tmp_2, curConfig->eventname, destion);
    usleep(TIMEOUT_VALUE);

    //massive copy of directory found for type "directory"
    do_log_copy(curConfig->eventname,dir,date_tmp,APLOG_TYPE);

    if (curConfig->type ==1){
        struct arg_copy * args =  malloc(sizeof(struct arg_copy));
        if(!args) {
            LOGE("%s:malloc failed\n", __FUNCTION__);
            return;
        }
        //time in seconds( should be less than phone doctor timer)
        args->time_val = 100 ;
        strncpy(args->orig,path,sizeof(args->orig));
        strncpy(args->dest,destion,sizeof(args->dest));
        ret = pthread_create(&thread, NULL, (void *)copy_dir, (void *)args);
        if (ret < 0) {
            LOGE("pthread_create copy dir error");
            free(args);
            //if ret >=0 free is done inside copy_dir
        }else{
            //backgroung thread is running. Event should be tagged not ready
            data_ready = 0;
        }

        if (strlen(curConfig->path_linked)>0){
            //now copy linked data
            linkedConfig = get_generic_config_by_path(curConfig->path_linked,first_modem_config);
            if (linkedConfig){
                strncpy(name_linked,name,sizeof(name_linked));
                //adding security NULL character
                name_linked[sizeof(name_linked)-1] = '\0';
                if (!str_simple_replace(name_linked,curConfig->matching_pattern,linkedConfig->matching_pattern)){
                    //only do the copy if name has been replaced
                    struct arg_copy * args_linked =  malloc(sizeof(struct arg_copy));
                    if(!args_linked) {
                        LOGE("%s:malloc failed\n", __FUNCTION__);
                        return;
                    }
                    //time in seconds( should be less than phone doctor timer)
                    args_linked->time_val = 100 ;
                    snprintf(path_linked, sizeof(path_linked),"%s/%s",curConfig->path_linked,name_linked);
                    strncpy(args_linked->orig,path_linked,sizeof(args_linked->orig));
                    strncpy(args_linked->dest,destion,sizeof(args_linked->dest));
                    ret = pthread_create(&thread, NULL, (void *)copy_dir, (void *)args_linked);
                    if (ret < 0) {
                        LOGE("pthread_create copy linked dir error");
                        free(args_linked);
                        //if ret >=0 free is done inside copy_dir
                    }
                }
            }
        }
        //generating DATAREADY (if required)
        if (strstr(event_class , ERROREVENT)) {
            snprintf(fullpath, sizeof(fullpath)-1, "%s/%s_errorevent", destion,curConfig->eventname );
            generate_data = 1;
        }else if (strstr(event_class , INFOEVENT)) {
            snprintf(fullpath, sizeof(fullpath)-1, "%s/%s_infoevent", destion,curConfig->eventname );
            generate_data = 1;
        }else{
            //we don't need to generate data
            generate_data = 0;
        }

        if (generate_data == 1){
            fp = fopen(fullpath,"w");
            if (fp == NULL){
                LOGE("can not create file: %s\n", fullpath);
            }else{
                //Fill DATAREADY field
                fprintf(fp,"DATAREADY=%d\n", data_ready);
                fprintf(fp,"_END\n");
                fclose(fp);
            }
        }
    }
    history_file_write_ex(event_class, curConfig->eventname, NULL, destion, NULL, key, date_tmp_2, data_ready);
    del_file_more_lines(HISTORY_FILE);
    //TO DO : define when path should be removed
    //remove(path);
    notify_crashreport();
}


/*
* Name          : process_modem_crash_event
* Description   : processes modem crash events
* Parameters    :
*   char *filename        -> path of watched directory/file
*   char *eventname       -> name of the watched event
*   char *name            -> name of the file inside the watched directory that has triggered the event
*   unsigned int files    -> nb max of logs destination directories (crashlog, aplogs, bz... )*/
void process_modem_crash_event(char *filename, char *eventname, char *name,  unsigned int files) {

    char date_tmp[32];
    char date_tmp_2[32];
    char key[SHA1_DIGEST_LENGTH+1];
    int dir;
    char path[PATHMAX];
    char destion[PATHMAX];

    get_formated_times(date_tmp,date_tmp_2);
    compute_key(key, CRASHEVENT, eventname);
    snprintf(path, sizeof(path),"%s/%s",filename,name);
    dir = find_dir(files,CRASH_MODE);
    if (dir == -1) {
        LOGE("find dir %d for modem crash failed\n", files);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, date_tmp_2, eventname);
        history_file_write(CRASHEVENT, eventname, NULL, NULL, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        remove(path);
        notify_crashreport();
        return;
    }

    snprintf(destion,sizeof(destion),"%s%d", CRASH_DIR,dir);
    int status = mv_modem_crash(filename, destion);
    if (status != 0)
        LOGE("backup modem core dump status: %d.\n", status);
    snprintf(destion,sizeof(destion),"%s%d/%s", CRASH_DIR,dir,name);
    do_copy(path, destion, 0);
    snprintf(destion,sizeof(destion),"%s%d/", CRASH_DIR,dir);

    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, date_tmp_2, eventname, destion);
    usleep(TIMEOUT_VALUE);
    do_log_copy(eventname,dir,date_tmp,APLOG_TYPE);
    do_log_copy(eventname,dir,date_tmp,BPLOG_TYPE);
    history_file_write(CRASHEVENT, eventname, NULL, destion, NULL, key, date_tmp_2);
    del_file_more_lines(HISTORY_FILE);
    remove(path);
    notify_crashreport();
}

/*
* Name          : process_full_dropbox_event
* Description   : processes anr, javacrash and watchdog full dropbox events
* Parameters    :
*   char *filename        -> path of watched directory
*   char *name            -> name of the file inside the watched directory that has triggered the event
*   unsigned int files    -> nb max of logs destination directories (crashlog, aplogs, bz... )*/
void process_full_dropbox_event(char *filename, char *name,  unsigned int files) {

    char date_tmp[32];
    char date_tmp_2[32];
    char key[SHA1_DIGEST_LENGTH+1];
    int dir;
    char destion[PATHMAX], path[PATHMAX];
    char lostevent[32] = { '\0', };
    char lostevent_subtype[32] = { '\0', };

    if (strstr(name, "anr"))
        strcpy(lostevent, ANR_CRASH);
    else if (strstr(name, "crash"))
        strcpy(lostevent, JAVA_CRASH);
    else if (strstr(name, "watchdog"))
        strcpy(lostevent, SYSSERVER_WDT);
    else
        //inconsistent full dropbox event
        return;

    snprintf(lostevent_subtype, sizeof(lostevent_subtype), "%s_%s", LOST, lostevent);

    get_formated_times(date_tmp,date_tmp_2);
    compute_key(key, CRASHEVENT, lostevent);

    dir = find_dir(files,CRASH_MODE_NOSD);
    if (dir == -1) {
        LOGE("find dir %d for lost dropbox failed\n", files);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, date_tmp_2, lostevent);
        history_file_write(CRASHEVENT, lostevent, lostevent_subtype, NULL, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        notify_crashreport();
        return;
    }
    /*copy the *.lost dropbox file*/
    snprintf(path, sizeof(path),"%s/%s",filename,name);
    snprintf(destion,sizeof(destion),"%s%d/%s", CRASH_DIR,dir,name);
    do_copy(path, destion, 0);
    snprintf(destion,sizeof(destion),"%s%d/",CRASH_DIR,dir);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, date_tmp_2, lostevent, destion);
    usleep(TIMEOUT_VALUE);
    do_log_copy(lostevent,dir,date_tmp,APLOG_TYPE);
    history_file_write(CRASHEVENT, lostevent, lostevent_subtype, destion, NULL, key, date_tmp_2);
    del_file_more_lines(HISTORY_FILE);
    notify_crashreport();
}

/*
* Name          : process_command
* Description   : This function manages treatment for commands.
*                 When a command file is detected, it manages the action and the list of events
* Parameters    :
*   char *filename        -> path of watched directory/file
*   char *name            -> name of the file inside the watched directory that has triggered the event*/
void process_command(char *filename, char *name) {

    char path[PATHMAX];
    struct stat info;
    char action[32] = {'\0',};
    char events[CMDSIZE_MAX] = {'\0',};

    //extract the action and list of events from the commad file
    snprintf(path, sizeof(path),"%s/%s", filename, name);
    if ((!stat(path, &info))) {
        if (!get_str_in_file(path,"ACTION=",action, sizeof(action)) && !get_str_in_file(path,"ARGS=",events, sizeof(events))) {
            if ((!strncmp(action, "DELETE", 6)) && checkEvents(events)) {
                if (!update_history_on_cmd_delete(events))
                    LOGE("Can't update history_event on delete cmd for events=%s", events);
            } else
                LOGE("Invalid command file %s : invalid action or/and arguments", path);
        } else
            LOGE("Invalid command file %s : invalid keywords", path);
        /*delete trigger file*/
        remove(path);
        return;
    }
    LOGE("Invalid command : can't open file %s", path);
}

/*
* Name          : process_aplog_and_bz_trigger
* Description   : This function manages treatment for aplog triggers and bz triggers.
*                 When an aplog or a bz trigger file is detected, it manages the packet(s) copy of aplogs file(s)
* Parameters    :
*   char *filename        -> path of watched directory/file
*   char *name            -> name of the file inside the watched directory that has triggered the event
*   unsigned int files    -> nb max of logs destination directories (crashlog, aplogs, bz... )*/
void process_aplog_and_bz_trigger(char *filename, char *name,  unsigned int files) {

    char date_tmp[32];
    char date_tmp_2[32];
    char key[SHA1_DIGEST_LENGTH+1];
    int dir;
    char path[PATHMAX];
    char destion[PATHMAX];
    struct stat info;
    char tmp[PATHMAX] = {'\0',};
    int nbPacket,aplogDepth;
    int aplogIsPresent;
    int bplogFlag = 0;
    struct stat sb;
    char value[PROPERTY_VALUE_MAX];

    //Get Aplog depth (number of aplogs per packet)
    property_get(PROP_APLOG_DEPTH, value, PROP_APLOG_DEPTH_DEF);
    aplogDepth = atoi(value);
    if (aplogDepth < 0)
        aplogDepth = 0;

    //Get nb of packets to copy
    property_get(PROP_APLOG_NB_PACKET, value, PROP_APLOG_NB_PACKET_DEF);
    nbPacket = atoi(value);
    if (nbPacket < 0)
        nbPacket = 0;

    if (filename) {
        //if a value is specified inside trigger file, it overrides property value
        snprintf(path, sizeof(path),"%s/%s", filename, name);
        if ((!stat(path, &sb))) {
            if (!get_str_in_file(path,"APLOG=", tmp, sizeof(tmp))) {
                aplogDepth = atoi(tmp);
                nbPacket = 1;
            }
            //Read bplog flag value specified inside trigger file
            tmp[0] = '\0';
            if (!get_str_in_file(path,"BPLOG=", tmp, sizeof(tmp))) {
                bplogFlag = atoi(tmp);
            }
        }
        LOGI("received trigger file %s for aplog or bz", path);
    }
    int j,k;
    aplogIsPresent = 0;
    dir = -1;
#ifndef FULL_REPORT
    flush_aplog();
#endif
    /*copy data file*/
    for( j=0; j < nbPacket ; j++){
        if(strstr(name, "aplog_trigger" ))
            dir=-1;
        for(k=0;k < aplogDepth ; k++) {

            aplogIsPresent = 1;
            if ((j == 0) && (k == 0))
                snprintf(path, sizeof(path),"%s",APLOG_FILE_0);
            else
                snprintf(path, sizeof(path),"%s.%d",APLOG_FILE_0,(j*aplogDepth)+k);

            //Check aplog file exists
            if(stat(path, &info) == 0){

                //aplog_trigger case : for each new packet to copy, create an aplogs destination directory
                if(k == 0 && strstr(name, "aplog_trigger" )){
                    dir = find_dir(files,APLOGS_MODE);
                    if (dir == -1) {
                        LOGE("find dir %d for aplog trigger failed\n", files);
                        //No need to write in the history event in this case
                        break;
                     }
                }
                //for 1st aplog in 1st packet :
                if ((j == 0) && (k == 0)) {
                    //bz_trigger case : create a new bz destination directory
                    if(!strncmp(name, BZTRIGGER, sizeof(BZTRIGGER))) {
                        dir = find_dir(files,BZ_MODE);
                        if (dir == -1) {
                            LOGE("find dir %d for BZ trigger failed\n", files);
                            //No need to write in the history event in this case
                            break;
                        }
                        snprintf(destion,sizeof(destion),"%s%d/aplog", BZ_DIR,dir);
                    }
                    //for aplog_trigger case
                    else if(strstr(name, "aplog_trigger" ))
                        snprintf(destion,sizeof(destion),"%s%d/aplog", APLOGS_DIR,dir);
                }
                else{
                    if(!strncmp(name, BZTRIGGER, sizeof(BZTRIGGER)))
                        snprintf(destion,sizeof(destion),"%s%d/aplog.%d", BZ_DIR,dir,(j*aplogDepth)+k);
                    else if(strstr(name, "aplog_trigger" ))
                        snprintf(destion,sizeof(destion),"%s%d/aplog.%d", APLOGS_DIR,dir,(j*aplogDepth)+k);
                }
                do_copy(path, destion, 0);
            }
            else {
                aplogIsPresent = 0;
                break;
            }
        }
        //for aplog_trigger, logs an APLOG event in history_event for each copied packet
        if((k != 0) && (dir != -1) && strstr(name, "aplog_trigger" )) {

            get_formated_times(date_tmp,date_tmp_2);
            compute_key(key, APLOGEVENT, APLOGTRIGGER);
            snprintf(destion,sizeof(destion),"%s%d/", APLOGS_DIR,dir);
            compress_aplog_folder(destion);
            LOGE("%-8s%-22s%-20s%s %s\n", APLOGEVENT, key, date_tmp_2, name, destion);
            history_file_write(APLOGEVENT, APLOGTRIGGER, NULL, destion, NULL, key, date_tmp_2);
            del_file_more_lines(HISTORY_FILE);
            notify_crashreport();
            if (filename)
                restart_profile2_srv();
        }
        if(aplogIsPresent == 0)
            break;
    }

    //for bz_trigger, treats bz_trigger file content and logs one BZEVENT event in history_event
    if(!strncmp(name, BZTRIGGER, sizeof(BZTRIGGER))) {
        //In case of bz_trigger with APLOG=0 which means bz type="enhancement" and so no logs needed.
        if((aplogDepth == 0) && (dir == -1)) {
            dir = find_dir(files,BZ_MODE);
            if (dir == -1) {
                LOGE("find dir %d for BZ trigger failed\n", files);
            }
        }

        if(-1 != dir) {

            //In case of bz_trigger with BPLOG=1, copy bplog file
            if((bplogFlag == 1)) {
                snprintf(destion,sizeof(destion),"%s%d/bplog", BZ_DIR,dir);
                do_copy(BPLOG_FILE_0, destion, FILESIZE_MAX);
            }

            snprintf(destion,sizeof(destion),"%s%d/", BZ_DIR,dir);
            compress_aplog_folder(destion);
            //copy bz_trigger file content
            snprintf(destion,sizeof(destion),"%s%d/bz_description", BZ_DIR,dir);
            snprintf(path, sizeof(path),"/logs/aplogs/%s",BZTRIGGER);
            do_copy(path,destion,0);
            get_formated_times(date_tmp,date_tmp_2);
            compute_key(key, BZEVENT, BZMANUAL);
            snprintf(destion,sizeof(destion),"%s%d", BZ_DIR,dir);
            //copy bz screenshot
            do_screenshot_copy(path,destion);
            snprintf(destion,sizeof(destion),"%s%d/", BZ_DIR,dir);
            LOGE("%-8s%-22s%-20s%s %s\n", BZEVENT, key, date_tmp_2, BZMANUAL, destion);
            history_file_write(BZEVENT, BZMANUAL, NULL, destion, NULL, key, date_tmp_2);
            del_file_more_lines(HISTORY_FILE);
            notify_crashreport();
            restart_profile2_srv();
        }
    }

#ifndef FULL_REPORT
    remove(APLOG_FILE_0);
#endif
    /*delete trigger file*/
    if (filename) {
        snprintf(path, sizeof(path),"%s/%s",filename,name);
        remove(path);
    }
    return;
}

/*
* Name          : process_stats_trigger
* Description   : This function manages treatment for stats triggers.
*                 When stats trigger file is detected, it copies data and trigger files
* Parameters    :
*   char *filename        -> path of watched directory/file
*   char *name            -> name of the file inside the watched directory that has triggered the event
*   unsigned int files    -> nb max of logs destination directories (crashlog, aplogs, bz... )*/
void process_stats_trigger(char *filename, char *name,  unsigned int files) {

    char date_tmp[32];
    char date_tmp_2[32];
    char key[SHA1_DIGEST_LENGTH+1];
    int dir;
    char path[PATHMAX];
    char destion[PATHMAX];
    unsigned int j = 0;
    char *p;
    char tmp[32];
    char type[20] = { '\0', };
    char tmp_data_name[PATHMAX];

    snprintf(tmp,sizeof(tmp),"%s",name);

    get_formated_times(date_tmp,date_tmp_2);

    dir = find_dir(files,STATS_MODE);
    if (dir == -1) {
        LOGE("find dir %d for stat trigger failed\n", files);
        p = strstr(tmp,"trigger");
        if ( p ){
            strcpy(p,"data");
        }
        compute_key(key, STATSEVENT, tmp);
        LOGE("%-8s%-22s%-20s%s\n", STATSEVENT, key, date_tmp_2, tmp);
        history_file_write(STATSEVENT, tmp, NULL, NULL, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        notify_crashreport();
        return;
    }
    /*copy data file*/
    p = strstr(tmp,"trigger");
    if ( p ){
        strcpy(p,"data");
        find_file_in_dir(tmp_data_name, filename, tmp);
        snprintf(path, sizeof(path),"%s/%s",filename,tmp_data_name);
        snprintf(destion,sizeof(destion),"%s%d/%s", STATS_DIR,dir,tmp_data_name);
        do_copy(path, destion, 0);
        remove(path);
    }
    /*copy trigger file*/
    snprintf(path, sizeof(path),"%s/%s",filename,name);
    snprintf(destion,sizeof(destion),"%s%d/%s", STATS_DIR,dir,name);
    do_copy(path, destion, 0);
    remove(path);
    snprintf(destion,sizeof(destion),"%s%d/", STATS_DIR,dir);
    compute_key(key, STATSEVENT, tmp);
    /*create type */
    snprintf(tmp,sizeof(tmp),"%s",name);
    p = strstr(tmp,"_trigger");
    if (p) {
        for (j=0;j<sizeof(type)-1;j++) {
            if (p == (tmp+j))
                break;
            type[j]=toupper(tmp[j]);
        }
    } else
        snprintf(type,sizeof(type),"%s",name);
    LOGE("%-8s%-22s%-20s%s %s\n", STATSEVENT, key, date_tmp_2, type, destion);
    /*for USBBOGUS case copy aplog file*/
    if (!strncmp(type, USBBOGUS, sizeof(USBBOGUS))) {
        usleep(TIMEOUT_VALUE);
        do_log_copy(type,dir,date_tmp,APLOG_STATS_TYPE);
    }
    history_file_write(STATSEVENT, type, NULL, destion, NULL, key, date_tmp_2);
    del_file_more_lines(HISTORY_FILE);
    notify_crashreport();
    return;
}

/*
* Name          : process_info_and_error
* Description   : This function manages treatment of error and info
*                 When event or error trigger file is detected, it copies data and trigger files
* Parameters    :
*   char *filename        -> path of watched directory/file
*   char *name            -> name of the file inside the watched directory that has triggered the event
*   unsigned int files    -> nb max of logs destination directories (crashlog, aplogs, bz... )*/
void process_info_and_error(char *filename, char *name,  unsigned int files) {

    char date_tmp[32];
    char date_tmp_2[32];
    char key[SHA1_DIGEST_LENGTH+1];
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

    get_formated_times(date_tmp,date_tmp_2);

    dir = find_dir(files,STATS_MODE);
    if (dir == -1) {
        LOGE("find dir %d for stat trigger failed\n", files);
        p = strstr(tmp,"trigger");
        if ( p ){
            strcpy(p,"data");
        }
        compute_key(key, name_event, tmp);
        LOGE("%-8s%-22s%-20s%s\n", name_event, key, date_tmp_2, tmp);
        history_file_write(name_event, tmp, NULL, NULL, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        notify_crashreport();
        return;
    }
    /*copy data file*/
    p = strstr(tmp,file_ext);
    if ( p ){
        strcpy(p,"_data");
        find_file_in_dir(tmp_data_name, filename,tmp);
        snprintf(path, sizeof(path),"%s/%s", filename,tmp_data_name);
        snprintf(destion,sizeof(destion),"%s%d/%s", STATS_DIR, dir, tmp_data_name);
        do_copy(path, destion, 0);
        remove(path);
    }
    /*copy trigger file*/
    snprintf(path, sizeof(path),"%s/%s", filename,name);
    snprintf(destion,sizeof(destion),"%s%d/%s", STATS_DIR,dir,name);
    do_copy(path, destion, 0);
    remove(path);
    snprintf(destion,sizeof(destion),"%s%d/", STATS_DIR,dir);
    compute_key(key, name_event, tmp);
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
    LOGE("%-8s%-22s%-20s%s %s\n", name_event, key, date_tmp_2, type, destion);
    history_file_write(name_event, type, NULL, destion, NULL, key, date_tmp_2);
    del_file_more_lines(HISTORY_FILE);
    notify_crashreport();
    return;
}

/*
* Name          : process_anr_and_uiwatchdog_events
* Description   : processes anr and uiwatchdog events
*                 For anr a dumpstate is launched if not already running
* Parameters    :
*   char *filename        -> path of watched directory/file
*   char *eventname       -> name of the watched event
*   char *name            -> name of the file inside the watched directory that has triggered the event
*   unsigned int files    -> nb max of logs destination directories (crashlog, aplogs, bz... )
*   int fd                -> file descriptor referring to the inotify instance
*   char *current_key     -> key of the current event to upload once the dumpstate is done*/
void process_anr_and_uiwatchdog_events(char *filename, char *eventname, char *name,  unsigned int files, int fd, char *current_key) {

    char date_tmp[32];
    char date_tmp_2[32];
    char key[SHA1_DIGEST_LENGTH+1];
    int dir;
    char path[PATHMAX];
    char destion[PATHMAX];
    struct stat info;
    int wd;

    current_key[0]='\0';

    get_formated_times(date_tmp,date_tmp_2);
    compute_key(key, CRASHEVENT, eventname);

    dir = find_dir(files,CRASH_MODE);
    if (dir == -1) {
        LOGE("find dir %d for anr and UIwatchdog failed\n", files);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, date_tmp_2, eventname);
        del_file_more_lines(HISTORY_FILE);
        history_file_write(CRASHEVENT, eventname, NULL, NULL, NULL, key, date_tmp_2);
        notify_crashreport();
        restart_profile1_srv();
        return;
    }

    snprintf(path, sizeof(path),"%s/%s",filename, name);
    if (stat(path, &info) == 0) {
        snprintf(destion,sizeof(destion),"%s%d/",CRASH_DIR,dir);
        LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, date_tmp_2, eventname, destion);
        snprintf(destion,sizeof(destion),"%s%d/%s",CRASH_DIR,dir, name);
        do_copy(path, destion, FILESIZE_MAX);
        prepare_anr_or_uiwdt(destion);
        history_file_write(CRASHEVENT, eventname, NULL, destion, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        usleep(TIMEOUT_VALUE);
        do_log_copy(eventname,dir,date_tmp,APLOG_TYPE);
        backtrace_anr_uiwdt(destion, dir);
        restart_profile1_srv();
#ifdef FULL_REPORT
        if (strstr(name, "anr")) {
            snprintf(destion,sizeof(destion),"%s%d",CRASH_DIR,dir);
            char value[PROPERTY_VALUE_MAX];
            property_get(PROP_LOGSYSTEMSTATE, value, "stopped");
            //Check if a dumpstate is already running
            if(strcmp(value,"running") == 0){
                LOGE("Can't launch dumpstate for %s.\n", destion);
            }else{
                //add a watcher on the destination directory to notify the crash upload once dumpstate is done
                wd = inotify_add_watch(fd, destion, IN_CLOSE_WRITE);
                if (wd < 0) {
                    LOGE("Can't add watch for %s.\n", destion);
                    notify_crashreport();
                    return;
                }
                //return key of current anr for consecutive anr dumpstate management
                strcpy(current_key,key);
                start_dumpstate_srv(CRASH_DIR, dir);
            }
        }
#endif
        notify_crashreport();
    }
    return;
}

/*
* Name          : process_generic_events
* Description   : processes apcoredump, javacrash and tombstones events and
*                 anr and UIwatchdog events when trigger file is detected elsewhere than the default watched directory
* Parameters    :
*   char *filename        -> path of watched directory/file
*   char *eventname       -> name of the watched event
*   char *name            -> name of the file inside the watched directory that has triggered the event
*   unsigned int files    -> nb max of logs destination directories (crashlog, aplogs, bz... )
*   int fd                -> file descriptor referring to the inotify instance
*   char *current_key     -> key of the current event to upload once the dumpstate is done*/
void process_generic_events(char *filename, char *eventname, char *name,  unsigned int files, int fd, char *current_key) {

    char date_tmp[32];
    char date_tmp_2[32];
    char key[SHA1_DIGEST_LENGTH+1];
    int dir;
    char path[PATHMAX];
    char destion[PATHMAX];
    struct stat info;
    int wd;

    current_key[0]='\0';

    get_formated_times(date_tmp,date_tmp_2);
    compute_key(key, CRASHEVENT, eventname);

    dir = find_dir(files,CRASH_MODE);
    if (dir == -1) {
        LOGE("find dir %d for other crashes failed\n", files);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, date_tmp_2, eventname);
        history_file_write(CRASHEVENT, eventname, NULL, NULL, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        if (strstr(name, "anr") || strstr(name, "system_server_watchdog"))
            restart_profile1_srv();
        notify_crashreport();
        return;
    }

    snprintf(path, sizeof(path),"%s/%s",filename,name);
    if (stat(path, &info) == 0) {
        snprintf(destion,sizeof(destion),"%s%d/",CRASH_DIR,dir);
        LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, date_tmp_2, eventname, destion);
        snprintf(destion,sizeof(destion),"%s%d/%s",CRASH_DIR,dir,name);
        do_copy(path, destion, FILESIZE_MAX);
        if (strstr(name, ".core" ))
            backup_apcoredump(dir, name, path);
        if (strstr(name, "anr") || strstr(name, "system_server_watchdog"))
            prepare_anr_or_uiwdt(destion);
        usleep(TIMEOUT_VALUE);
        do_log_copy(eventname,dir,date_tmp,APLOG_TYPE);
        history_file_write(CRASHEVENT, eventname, NULL, destion, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        if (strstr(name, "anr") || strstr(name, "system_server_watchdog")){
            backtrace_anr_uiwdt(destion, dir);
            restart_profile1_srv();
        }
#ifdef FULL_REPORT
        if(strstr(name, "anr") || strstr(name, "crash") || strstr(name, "tombstone")){
            snprintf(destion,sizeof(destion),"%s%d",CRASH_DIR,dir);
            char value[PROPERTY_VALUE_MAX];
            property_get(PROP_LOGSYSTEMSTATE, value, "stopped");
            if(strcmp(value,"running") == 0){
                LOGE("Can't launch dumpstate for %s.\n", destion);
            }else{
                wd = inotify_add_watch(fd, destion, IN_CLOSE_WRITE);
                if (wd < 0) {
                    LOGE("Can't add watch for %s.\n", destion);
                    notify_crashreport();
                    return;
                }
                //return key of current event for consecutive dumpstates management
                strcpy(current_key,key);
                start_dumpstate_srv(CRASH_DIR, dir);
            }
        }
#endif
        notify_crashreport();
    }
    return;
}

/*
* Name          : process_uptime
* Description   : processes uptime events
* Parameters    :
*   char *eventname       -> name of the watched event*/
void process_uptime(char *eventname) {

    long long tm;
    int hours, seconds, minutes;
    char date_tmp[32];
    char date_tmp_2[32];
    char destion[PATHMAX];
    int fd1;
    time_t t;
    struct tm *time_tmp;
    char key[SHA1_DIGEST_LENGTH+1];

    if (!get_uptime(&tm)) {

        hours = (int) (tm / 1000000000LL);
        seconds = hours % 60; hours /= 60;
        minutes = hours % 60; hours /= 60;
        snprintf(date_tmp,sizeof(date_tmp),"%04d:%02d:%02d",hours, minutes,seconds);
        snprintf(destion,sizeof(destion),"#V1.0 %-16s%-24s", eventname, date_tmp);
        // Create HISTORY_FILE
        fd1 = open(HISTORY_FILE,O_RDWR);
        if (fd1 > 0) {
            write(fd1,destion,strlen(destion));
            close(fd1);
        }
        if (!abort_clean_sd)
            clean_crashlog_in_sd(SDCARD_LOGS_DIR, 10);
        /*Update event every 12 hours*/
        if ((hours / current_uptime_hour_frequency) >= loop_uptime_event) {

            time(&t);
            time_tmp = localtime((const time_t *)&t);
            PRINT_TIME(date_tmp_2, TIME_FORMAT_2, time_tmp);
            compute_key(key, PER_UPTIME, NULL);
            LOGE("%-8s%-22s%-20s%s\n", PER_UPTIME, key, date_tmp_2, date_tmp);
            history_file_write(PER_UPTIME, NULL, NULL, NULL, date_tmp, key, date_tmp_2);
            del_file_more_lines(HISTORY_FILE);
            loop_uptime_event = (hours / current_uptime_hour_frequency) + 1;
            notify_crashreport();
            restart_profile2_srv();
            check_running_power_service();
        }
    }
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
static void create_infoevent(char* filename, char* data0, char* data1, char* data2, unsigned int files)
{
    FILE *fp;
    char fullpath[PATHMAX];

    snprintf(fullpath, sizeof(fullpath)-1, "%s/%s", LOGS_DIR, filename);

    fp = fopen(fullpath,"w");
    if (fp == NULL)
    {
        LOGE("can not create file: %s\n", fullpath);
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

    process_info_and_error(LOGS_DIR, filename, files);
}

/**
 * @brief Extract the timestamp from a dropbox log filename
 *
 * Extract the timestamp value contained in the name of a log file generated by the
 * dropbox manager. The timestamp max value that could be extracted is equal to max value for
 * 'long' type (2 147 483 647 => 19 Jan 2038)
 *
 * @param[in] filename is the name of the log file containing the timestamp to extract
 *
 * @return a long equal to the extracted timestamp value. -1 in case of failure.
 */
static long extract_dropbox_timestamp(char* filename)
{
    char *ptr_timestamp_start,*ptr_timestamp_end;
    char timestamp[TIMESTAMP_MAX_SIZE+1] = { '\0', };
    unsigned int i;
    //DropBox log filename format is : 'error/log type' + '@' + 'timestamp' + '.' + 'file suffix'
    //Examples : system_app_anr@1350992829414.txt or system_app_crash@1266667499976.txt
    //So we look for '@' then we look for '.' then extract characters between those two characters
    //and finally we strip the 3 last characters corresponding to millisecs to have an UNIX style timestamp
    ptr_timestamp_start = strchr(filename, '@');
    if (ptr_timestamp_start) {
        //Point to timestamp 1st char
        ptr_timestamp_start++;
        ptr_timestamp_end = strchr(ptr_timestamp_start, '.');
        if (ptr_timestamp_end) {
            //checks timestamp size
            if ( ((ptr_timestamp_end - ptr_timestamp_start - 3) <= TIMESTAMP_MAX_SIZE ) && ((ptr_timestamp_end - ptr_timestamp_start - 3) >= 0 )) {
                strncpy(timestamp, ptr_timestamp_start, ptr_timestamp_end - ptr_timestamp_start - 3);
                //checks timestamp consistency
                for(i=0; i <strlen(timestamp); i++) {
                    if (!isdigit(timestamp[i]))
                        return -1;
                }
                //checks timestamp value compatibility with 'long' type
                if ( atoll(timestamp) > LONG_MAX )
                    return -1;
                return atol(timestamp);
            }
        }
    }
    return -1;
}

 /**
 * @brief allows to avoid processing a duplicate dropbox event as a real event
 *
 * This function allows to not process twice a same dropbox log file that
 * has been only renamed (notably because of the DropBoxManager service)
 *
 * @param[in] event : current inotify event detected by the file system watcher
 * @param[in] files : nb max of logs destination directories
 *
 * @retval -1 the input event is DUPLICATE and shall NOT be processed
 * @retval 0 the input event shall be processed as usual
 */
static int manage_duplicate_dropbox_events(struct inotify_event *event, unsigned int files)
{
    static uint32_t previous_event_cookie = 0;
    static char previous_filename[PATHMAX] = { '\0', };
    char info_filename[PATHMAX] = { '\0',};
    char destination[PATHMAX] = { '\0', };
    char origin[PATHMAX] = { '\0', };
    struct stat info;
    long timestamp_value;
    char human_readable_date[32] = "timestamp_extract_failed"; //initialized to default value

    //If a file is moved from the dropbox directory, it shall not be processed as an event
    //but the inotify event cookie and name shall be saved
    if (event->mask & IN_MOVED_FROM) {
        previous_event_cookie = event->cookie;
        strncpy(previous_filename, event->name, event->len);
        return -1;
    }
    //If a log file is moved to the dropbox directory, it could be the log file previously moved from the dropbox so we check it
    //note : we can reasonably assume kernel behaviour is reliable and then move events are always emitted as contiguous pairs
    //with IN_MOVED_FROM immediately followed by IN_MOVED_TO
    if ( (event->mask & IN_MOVED_TO) && (previous_event_cookie != 0) && (previous_event_cookie == event->cookie) && event->len) {

        //the log file is temporaly copied from dropbox directory to /logs and is renamed
        //so it could be detected and processed as an infoevent data
        if (strstr(event->name, "anr")) {
            snprintf(destination,sizeof(destination),"%s/%s", LOGS_DIR, ANR_DUPLICATE_DATA);
            strcpy(info_filename, ANR_DUPLICATE_INFOERROR);
        }
        else if ( strstr(event->name, "system_server_watchdog") ) {
            snprintf(destination,sizeof(destination),"%s/%s", LOGS_DIR, UIWDT_DUPLICATE_DATA);
            strcpy(info_filename, UIWDT_DUPLICATE_INFOERROR);
        }
        else { //event->name contains "crash"
            snprintf(destination,sizeof(destination),"%s/%s", LOGS_DIR, JAVACRASH_DUPLICATE_DATA);
            strcpy(info_filename, JAVACRASH_DUPLICATE_INFOERROR);
        }
        snprintf(origin,sizeof(origin),"%s/%s", DROPBOX_DIR, event->name);

        //manages compressed log file
        if ( !strcmp(".gz", &origin[strlen(origin) - 3]) )
            strcat(destination,".gz");

        if((stat(origin, &info) == 0) && (info.st_size != 0))
            do_copy(origin, destination, FILESIZE_MAX);

        //Fetch the timestamp from the original log filename and write it in infoevent as a human readable date
        timestamp_value = extract_dropbox_timestamp(previous_filename);

        if (timestamp_value != -1) {
            struct tm *time;
            memset(&time, 0, sizeof(time));
            time = localtime(&timestamp_value);
            PRINT_TIME(human_readable_date, TIME_FORMAT_2, time);
        }

        //Generates the INFO event with the previous and the new filename as DATA0 and DATA1 and with the date previously fetched set in DATA2
        create_infoevent(info_filename, previous_filename, event->name, human_readable_date, files);

        //re-initialize variables for next event
        previous_event_cookie = 0;
        strcpy(previous_filename, "");
        return -1;
    }
    return 0;
}

/**
 * File monitor module file descriptor
 */
static int file_monitor_fd = -1;

/**
 * @brief File monitor module file descriptor getter
 *
 * Export FD which expose new events from File Monitor module events
 * source.
 *
 * @return Initialized File Monitor module file descriptor.
 */
int file_monitor_get_fd()
{
    return file_monitor_fd;
}

/**
 * @brief File Monitor module init function
 *
 * Initialize File Monitor module by adding all watched
 * files/directories to the inotify mechanism and expose it in the
 * module FD. The list of watched files/directories is defined by the
 * wd_array global array.
 *
 * @return 0 on success, -1 on failure.
 */
static int file_monitor_init()
{
    file_monitor_fd = inotify_init();
    if (file_monitor_fd < 0) {
        LOGE("inotify_init failed, %s\n", strerror(errno));
        return -1;
    }

    int i, wd;
    for (i = WDCOUNT_START; i < WDCOUNT; i++) {
        wd = inotify_add_watch(file_monitor_fd, wd_array[i].filename,
                wd_array[i].mask);
        if (wd < 0) {
            LOGE("Can't add watch for %si, remove all existing watches.\n", wd_array[i].filename);
            for (--i ; i >= 0 ; i--) {
                inotify_rm_watch(file_monitor_fd, wd_array[i].wd);
            }
            return -1;
        }
        wd_array[i].wd = wd;
        LOGI("%s has been snooped\n", wd_array[i].filename);
    }
    //add generic watch here
    generic_add_watch(first_modem_config,file_monitor_fd);

    return 0;
}

/**
 * @brief Show the contents of an array of inotify_events
 *
 * Called when a problem occurred during the parsing of
 * the array to ease the debug
 *
 * @param buffer: buffer containing the inotify events
 * @param len: length of the buffer
 */
void dump_inotify_events(char *buffer, unsigned int len,
    char *lastevent) {

    struct inotify_event *event;
    int i;

    LOGI("%s: Dump the wd_array:\n", __FUNCTION__);
    for (i = WDCOUNT_START; i < WDCOUNT; i++) {
        LOGI("%s: wd_array[%d]: filename=%s, wd=%d\n", __FUNCTION__, i, wd_array[i].filename, wd_array[i].wd);
    }

    while (1) {
        if (len == 0) {
            /* End of the buffer */
            return;
        }
        event = (struct inotify_event*)buffer;
        if (len < sizeof(struct inotify_event) ||
            len < (sizeof(struct inotify_event) + event->len)) {
            /* Not enought room the last event,
             * get it from the lastevent */
            event = (struct inotify_event*)lastevent;
            len = 0;
            if (!event->wd && !event->mask && !event->cookie && !event->len)
                // no last event
                return;
        } else {
            buffer += sizeof(struct inotify_event) + event->len;
            len -= sizeof(struct inotify_event) + event->len;
        }
        LOGI("%s: event received (name=%s, wd=%d, mask=0x%x, len=%d)\n", __FUNCTION__, event->name, event->wd, event->mask, event->len);
    }
 }

/**
 * @brief Handle File Monitor processing of events
 *
 * Called when a file/directory has changed according to the global
 * wd_array array. Compare the current event with the list to
 * determine the source and call the good processing function.
 *
 * @param files nb max of logs destination directories (crashlog,
 * aplogs, bz... )
 *
 * @return 0 on success, -1 on error.
 */
static int file_monitor_handle(unsigned int files)
{
    int wd;
    int len = 0, orig_len;
    char orig_buffer[sizeof(struct inotify_event)+PATHMAX], *buffer, lastevent[sizeof(struct inotify_event)+PATHMAX];
    struct inotify_event *event;
    int i = 0;
    char key[SHA1_DIGEST_LENGTH+1];
    int missing_bytes;

    len = read(file_monitor_fd, orig_buffer, sizeof(orig_buffer));
    if (len < 0) {
        LOGE("%s: Cannot read file_monitor_fd, error is %s\n", __FUNCTION__, strerror(errno));
        return -1;
    }

    buffer = &orig_buffer[0];
    event = (struct inotify_event *)buffer;
    orig_len = len;

    /* Preinitialize lastevent (in case it was not used so it is not dumped) */
    ((struct inotify_event *)lastevent)->wd = 0;
    ((struct inotify_event *)lastevent)->mask = 0;
    ((struct inotify_event *)lastevent)->cookie = 0;
    ((struct inotify_event *)lastevent)->len = 0;

#ifdef FULL_REPORT
    static char current_key[2][SHA1_DIGEST_LENGTH+1] = {{0,},{0,}};
    static int index_prod = 0;
#endif

    /* clean children to avoid zombie processes */
    while(waitpid(-1, NULL, WNOHANG) > 0){};

    while (1) {
        if (len == 0) {
            /* End of the events to read */
            return 0;
        }
        if ((unsigned int)len < sizeof(struct inotify_event)) {
            /* Not enought room for an empty event */
            LOGI("%s: incomplete inotify_event received (%d bytes), complete it\n", __FUNCTION__, len);
            /* copy the last bytes received */
            if(len <= (int)sizeof(lastevent))
                memcpy(lastevent, buffer, len);
            else {
                LOGE("%s: Cannot copy buffer\n", __FUNCTION__);
                return -1;
            }
            /* read the missing bytes to get the full length */
            missing_bytes = (int)sizeof(struct inotify_event)-len;
            if(((int) len + missing_bytes) < ((int)sizeof(lastevent))) {
                if (read(file_monitor_fd, &lastevent[len], missing_bytes) != missing_bytes ){
                    LOGE("%s: Cannot complete the last inotify_event received (structure part) - %s\n",
                    __FUNCTION__, strerror(errno));
                    return -1;
                }
            }
            else {
                LOGE("%s: Cannot read missing bytes, not enought space in lastevent\n",
                    __FUNCTION__);
                return -1;
            }
            event = (struct inotify_event*)lastevent;
            /* now, reads the full last event, including its name field */
            if ( read(file_monitor_fd, &lastevent[sizeof(struct inotify_event)],
                event->len) != (int)event->len) {
                LOGE("%s: Cannot complete the last inotify_event received (name part) - %s\n",
                    __FUNCTION__, strerror(errno));
                return -1;
            }
            len = 0;
            /* now, the last event is complete, we can continue the parsing */
        } else if ( (unsigned int)len < sizeof(struct inotify_event) + event->len ) {
            int res, missing_bytes = (int)sizeof(struct inotify_event) + event->len - len;
            event = (struct inotify_event*)lastevent;
            /* The event was truncated */
            LOGI("%s: truncated inotify_event received (%d bytes missing), complete it\n", __FUNCTION__, missing_bytes);

            if( len > (int)sizeof(lastevent)) {
                LOGE("%s: not enough space on array lastevent.\n", __FUNCTION__);
                return -1;
            }
            /* copy the last bytes received */
            memcpy(lastevent, buffer, len);
            /* now, reads the full last event, including its name field */
            res = read(file_monitor_fd, &lastevent[len], missing_bytes);
            if ( res != missing_bytes ) {
                LOGE("%s: Cannot complete the last inotify_event received (name part2); received %d bytes, expected %d bytes - %s\n",
                    __FUNCTION__, res, missing_bytes, strerror(errno));
                return -1;
            }
            len = 0;
            /* now, the last event is complete, we can continue the parsing */
        } else {
            event = (struct inotify_event *)buffer;
            buffer += sizeof(struct inotify_event) + event->len;
            len -= sizeof(struct inotify_event) + event->len;
        }

        /* for dir to be delete */
        if((event->mask & IN_DELETE_SELF) ||(event->mask & IN_MOVE_SELF)) {
            for (i = WDCOUNT_START; i < WDCOUNT; i++) {
                if (event->wd != wd_array[i].wd)
                    continue;
                mkdir(wd_array[i].filename, 0777);
                inotify_rm_watch(file_monitor_fd, event->wd);
                wd = inotify_add_watch(file_monitor_fd, wd_array[i].filename, wd_array[i].mask);
                if (wd < 0) {
                    LOGE("Can't add watch for %s.\n", wd_array[i].filename);
                    return -1;
                }
                wd_array[i].wd = wd;
                event->wd = -1;
                LOGW("%s has been deleted or moved, we watch it again.\n", wd_array[i].filename);
            }
        }
        //Check subject of this event is a not directory
        if (!(event->mask & IN_ISDIR)) {
            for (i = WDCOUNT_START; i < WDCOUNT; i++) {
                if (event->wd != wd_array[i].wd)
                    continue;
                if(!event->len){
                    /* event concerns a watched file */
                    if (!memcmp(wd_array[i].filename,HISTORY_UPTIME,strlen(HISTORY_UPTIME))) {

                        process_uptime(wd_array[i].eventname);
                    }
                    break;
                }
                /* event concerns a file inside a watched directory */
                else{
                    /* for duplicate dropbox events : first managed before continuing nominal events processing*/
                    if ( (event->wd == get_watched_directory_wd(DROPBOX_DIR)) && ( strstr(event->name, "anr") || strstr(event->name, "system_server_watchdog") || strstr(event->name, "crash"))) {
                        //Check if this event shall be processed
                        if (manage_duplicate_dropbox_events(event, files)!=0)
                            break;
                    }
                    /* for modem reset */
                    if(strstr(event->name, wd_array[i].cmp) && (strstr(event->name, "apimr.txt" ) ||strstr(event->name, "mreset.txt" ) )){

                        process_modem_reset_event(wd_array[i].filename, wd_array[i].eventname, event->name, files);
                        break;
                    }
                    /* for modem crash */
                    else if(strstr(event->name, wd_array[i].cmp) && strstr(event->name, "mpanic.txt" )){

                        process_modem_crash_event(wd_array[i].filename, wd_array[i].eventname, event->name, files);
                        break;
                    }
                    /* for full dropbox */
                    else if(strstr(event->name, wd_array[i].cmp) && (strstr(event->name, ".lost" ))){

                        process_full_dropbox_event(wd_array[i].filename, event->name, files);
                        break;
                    }
                    /* for hprof */
                    else if(strstr(event->name, wd_array[i].cmp) && (strstr(event->name, ".hprof" ))){

                        process_hprof_event(wd_array[i].filename, event->name, files);
                        break;
                    }
                    /* for cmd */
#ifdef FULL_REPORT
                    else if((strcmp(wd_array[i].eventname, CMDTRIGGER)==0) && (strstr(event->name, "_cmd" ))){

                        process_command(wd_array[i].filename, event->name);
                        break;
                    }
#endif
                    /* for aplog and bz trigger */
                    else if((strcmp(wd_array[i].eventname, APLOGTRIGGER)==0) && (strstr(event->name, "_trigger" ))){

                        process_aplog_and_bz_trigger(wd_array[i].filename, event->name, files);
                        break;
                    }
                    /* for STATS trigger */
                    else if((strcmp(wd_array[i].eventname,STATSTRIGGER)==0) && (strstr(event->name, "_trigger" ))){

                        process_stats_trigger(wd_array[i].filename, event->name, files);
                        break;
                    }
                    /* for INFO & ERROR (using STATS watcher) */
                    else if((strcmp(wd_array[i].eventname,STATSTRIGGER)==0) && ((strstr(event->name, "_infoevent" )) || (strstr(event->name, "_errorevent" )))){

                        process_info_and_error(wd_array[i].filename, event->name, files);
                        break;
                    }
                    /* for anr and UIwatchdog */
                    else if (strstr(event->name, wd_array[i].cmp) && ( strstr(event->name, "anr") || strstr(event->name, "system_server_watchdog"))) {

                        process_anr_and_uiwatchdog_events(wd_array[i].filename, wd_array[i].eventname, event->name, files, file_monitor_fd, key);
#ifdef FULL_REPORT
                        //manage consecutive anr dumpstates: not null returned event key means an anr dumpstate has been launched
                        if (key [0] != '\0' ) {
                            strncpy(current_key[index_prod],key,sizeof(key));
                            index_prod = (index_prod + 1) % 2;
                        }
#endif
                        break;
                    }
                    /* for other case */
                    else if (strstr(event->name, wd_array[i].cmp)) {

                        process_generic_events(wd_array[i].filename, wd_array[i].eventname, event->name, files, file_monitor_fd, key);
#ifdef FULL_REPORT
                        //manage consecutive dumpstates: not null returned event key means a dumpstate has been launched
                        if (key [0] != '\0' ) {
                            strncpy(current_key[index_prod],key,sizeof(key));
                            index_prod = (index_prod + 1) % 2;
                        }
#endif
                        break;
                    }
                }
            }
#ifdef FULL_REPORT
            if(event->len && !strncmp(event->name, "dropbox-", 8) && (i==WDCOUNT) && event->wd != -1) {
                if (current_key[index_cons][0] == 0) {
                    LOGE("%s: Dropbox event received but event_id has not been set properly\n", __FUNCTION__);
                    dump_inotify_events(orig_buffer, orig_len, lastevent);
                    return -1;
                }
                //dumpstate is done so remove the watcher
                inotify_rm_watch(file_monitor_fd, event->wd);
                notify_crash_to_upload(current_key[index_cons]);
                current_key[index_cons][0] = 0;
                index_cons = (index_cons + 1) % 2;
            }
#endif
        }else {
            //directory case
            for (i = WDCOUNT_START; i < WDCOUNT; i++) {
                if (event->wd != wd_array[i].wd)
                    continue;
                /* for modem generic */
                //TO IMPROVE : change flag management and put this in main loop
                if(strstr("/logs/modemcrash", wd_array[i].filename) && (generic_match(event->name,first_modem_config))){
                    process_modem_generic(wd_array[i].filename, event->name, files, file_monitor_fd);
                    break;
                }
            }
            pconfig check_config = generic_match_by_wd(event->name,first_modem_config,event->wd);
            if(check_config){
                    process_modem_generic(check_config->wd_config.filename, event->name, files, file_monitor_fd);
            }else{
                LOGE("Directory not catched %s.\n", event->name);
            }
        }
    }
    return 0;
}

/**
 * @brief Compute mmgr parameter
 *
 * Called when parameter are needed for a mmgr callback
 *
 * @param parameters needed to create event (logd, name,...)
 *
 * @return 0 on success, -1 on error.
 */
int compute_mmgr_param(char *type, int *mode, char *name, int *aplog, int *bplog, int *log_mode){

        if (strstr(type, "MODEMOFF" )){
            //CASE MODEMOFF
            *mode = STATS_MODE;
            sprintf(name, "%s", INFOEVENT);
        }else if (strstr(type, "MSHUTDOWN" )){
            //CASE MSHUTDOWN
            *mode = CRASH_MODE;
            sprintf(name, "%s", CRASHEVENT);
            *aplog = 1;
            *log_mode = APLOG_TYPE;
        }else if (strstr(type, "MOUTOFSERVICE" )){
            //CASE MOUTOFSERVICE
            *mode = CRASH_MODE;
            sprintf(name, "%s", CRASHEVENT);
            *aplog = 1;
            *log_mode = APLOG_TYPE;
        }else if (strstr(type, "MPANIC" )){
            //CASE MPANIC
            *mode = CRASH_MODE;
            sprintf(name, "%s",CRASHEVENT);
            *aplog = 1;
            *log_mode = APLOG_TYPE;
            *bplog = 1;
        }else if (strstr(type, "APIMR" )){
            //CASE APIMR
            *mode = CRASH_MODE;
            sprintf(name, "%s", CRASHEVENT);
            *aplog = 1;
            *log_mode = APLOG_TYPE;
        }else if (strstr(type, "MRESET" )){
            //CASE MRESET
            *mode = CRASH_MODE;
            sprintf(name, "%s",CRASHEVENT);
            *log_mode = APLOG_TYPE;
            *aplog = 1;
        }else if (strstr(type, "TELEPHONY" )){
            //CASE TEL_ERROR
            *mode = STATS_MODE;
            sprintf(name, "%s", ERROREVENT);
            *aplog = 1;
            *log_mode = APLOG_STATS_TYPE;
        }else{
            //unknown event name
            LOGW("wrong type found in mmgr_get_fd : %s.\n",type);
            return -1;
        }
        return 0;
}

/**
 * @brief Handle mmgr call back
 *
 * Called when a call back function is triggered
 * depending on init subscription
 *
 * @param files nb max of logs destination directories (crashlog,
 * aplogs, bz... )
 *
 * @return 0 on success, -1 on error.
 */
static int mmgr_handle(unsigned int files)
{
    char date_tmp_2[32];
    char key[SHA1_DIGEST_LENGTH+1];
    int dir;
    int event_mode;
    int aplog_mode;
    char event_name[MMGRMAXDATA]= { '\0', };
    char * event_dir;
    char data0[MMGRMAXDATA]= { '\0', };
    char data1[MMGRMAXDATA]= { '\0', };
    char data2[MMGRMAXDATA]= { '\0', };
    char data3[MMGRMAXDATA]= { '\0', };
    char cd_path[MMGRMAXDATA]= { '\0', };
    char destion[PATHMAX];
    char destion2[PATHMAX];
    char curChar[1];
    int iChar;
    char type[20] = { '\0', };
    char date_tmp[32];
    int nbytes;
    int copy_aplog = 0;
    int copy_bplog = 0;
    struct mmgr_data cur_data;

    // get data from mmgr pipe
    nbytes = read(mmgr_get_fd(), &cur_data, sizeof( struct mmgr_data));
    strcpy(type,cur_data.string);

    if (nbytes > 0){
        //find_dir should be done before event_dir is set
        LOGD("Received string from mmgr: %s  - %d bytes", type,nbytes);
        get_formated_times(date_tmp,date_tmp_2);
        if (compute_mmgr_param (type, &event_mode,event_name, &copy_aplog, &copy_bplog, &aplog_mode) < 0){
            return -1;
        }
        //set DATA0/1 value
        if (strstr(type, "MPANIC" )){
            LOGD("Extra int value : %d ",cur_data.extra_int);
            if (cur_data.extra_int >= 0){
                snprintf(data0,sizeof(data0),"%d", cur_data.extra_int);
            }else if (cur_data.extra_int == -1){
                snprintf(data0,sizeof(data0),"%s", "CD_FAILED");
            }else if (cur_data.extra_int == -2){
                snprintf(data0,sizeof(data0),"%s", "NO_PANIC_ID");
            }else if (cur_data.extra_int == -3){
                snprintf(data0,sizeof(data0),"%s", "UNKNOWN");
            }
            LOGD("Extra string value : %s ",cur_data.extra_string);
            snprintf(cd_path,sizeof(cd_path),"%s", cur_data.extra_string);
        }else if (strstr(type, "APIMR" )){
            LOGD("Extra string value : %s ",cur_data.extra_string);
            //need to put it in DATA3 to avoid conflict with parser
            snprintf(data3,sizeof(data3),"%s", cur_data.extra_string);
        }else if (strstr(type, "TELEPHONY" )){
            LOGD("Extra int value : %d ",cur_data.extra_int);
            snprintf(data0,sizeof(data0),"%d", cur_data.extra_int);
            LOGD("Extra string value : %s ",cur_data.extra_string);
            snprintf(data1,sizeof(data1),"%s", cur_data.extra_string);
        }

        dir = find_dir(files,event_mode);
        if (dir == -1) {
            LOGE("find dir %d for mmgr trigger failed\n", files);
            compute_key(key, event_name, type);
            LOGE("%-8s%-22s%-20s%s\n", event_name, key, date_tmp_2, type);
            history_file_write(event_name, type, NULL, NULL, NULL, key, date_tmp_2);
            del_file_more_lines(HISTORY_FILE);
            notify_crashreport();
            return 0;
        }
        //update event_dir should be done after find_dir call
        if (event_mode == STATS_MODE){
            event_dir = STATS_DIR;
        }else if (event_mode == CRASH_MODE) {
            event_dir = CRASH_DIR;
        }else{
            //unknown event name
            LOGW("wrong event_mode found in mmgr_get_fd - event_dir : %d.\n",event_mode);
            return -1;
        }

        if (copy_aplog > 0){
            do_log_copy(type,dir,date_tmp,aplog_mode);
        }
        if (copy_bplog > 0){
            do_log_copy(type,dir,date_tmp,BPLOG_TYPE);
        }
        snprintf(destion,sizeof(destion),"%s%d/", event_dir,dir);
        compute_key(key, event_name, type);
        LOGE("%-8s%-22s%-20s%s %s\n", event_name, key, date_tmp_2, type, destion);
        //copying file if required
        if (strlen(cd_path) > 0){
            char *basename;
            basename = strrchr ( cd_path, '/' );
            //needed to remove '/'
            basename++;
            if (basename){
                snprintf(destion2,sizeof(destion2),"%s/%s", destion,basename);
            }else{
                snprintf(destion2,sizeof(destion2),"%s/%s", destion, "core_dump");
            }
            do_copy(cd_path, destion2, 0);
            remove(cd_path);
        }
        //generating extra DATA (if required)
        if ((strlen(data0) > 0) || (strlen(data1) > 0) || (strlen(data2) > 0) || (strlen(data3) > 0)){
            FILE *fp;
            char fullpath[PATHMAX];
            if (strstr(event_name , ERROREVENT)) {
                snprintf(fullpath, sizeof(fullpath)-1, "%s/%s_errorevent", destion,type );
            }else if (strstr(event_name , INFOEVENT)) {
                snprintf(fullpath, sizeof(fullpath)-1, "%s/%s_infoevent", destion,type );
            }else{
                snprintf(fullpath, sizeof(fullpath)-1, "%s/%s_crashdata", destion,type );
            }

            fp = fopen(fullpath,"w");
            if (fp == NULL){
                LOGE("can not create file: %s\n", fullpath);
            }else{
                //Fill DATA fields
                if (strlen(data0) > 0)
                    fprintf(fp,"DATA0=%s\n", data0);
                if (strlen(data1) > 0)
                    fprintf(fp,"DATA1=%s\n", data1);
                if (strlen(data2) > 0)
                    fprintf(fp,"DATA2=%s\n", data2);
                if (strlen(data3) > 0)
                    fprintf(fp,"DATA3=%s\n", data3);
                fprintf(fp,"_END\n");
                fclose(fp);
            }
        }

        history_file_write(event_name, type, NULL, destion, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        notify_crashreport();
    }else{
        LOGW("No data found in mmgr_get_fd.\n");
        return -1;
    }
    return 0;
}

/**
 * @brief crashlogd monitor loop function which wait for events.
 *
 * It intializes the different event sources, register them
 * (FD_SET()), wait for events (select()) and finally handle the
 * processing of each source (FD_ISSET()).
 *
 * Each source of event should provide 3 functions to be monitored :
 *  @li <src>_init() initialize the source
 *  @li <src>_get_fd() return a file descriptor which represent the new event trigger
 *  @li <src>_handle() to call when an event on the source is detected
 *
 * @param files nb max of logs destination directories (crashlog,
 * aplogs, bz... )
 * @return -1 if the loop exit, meaning that something went wrong before
 */
static int do_monitor(unsigned int files)
{
    fd_set read_fds; /**< file descriptor set watching data availability from sources */
    int max = 0; /**< select max fd value +1 {@see man select(2) nfds} */
    int select_result; /**< select result */

    file_monitor_init();
    init_mmgr_cli();

    for(;;) {

        // Clear fd set
        FD_ZERO(&read_fds);

        // File monitor fd setup
        if (file_monitor_get_fd() > 0) {
            FD_SET(file_monitor_get_fd(), &read_fds);
            if (file_monitor_get_fd() > max)
                max = file_monitor_get_fd();
        }

        //mmgr fd setup
        if (mmgr_get_fd() > 0) {
            FD_SET(mmgr_get_fd(), &read_fds);
            if (mmgr_get_fd() > max)
                max = mmgr_get_fd();
        }

        // Wait for events
        select_result = select(max+1, &read_fds, NULL, NULL, NULL);

        if (select_result == -1 && errno == EINTR) // Interrupted, need to recycle
            continue;

        // Result processing
        if (select_result > 0) {

            // File monitor
            if (FD_ISSET(file_monitor_get_fd(), &read_fds)) {
                file_monitor_handle(files);
            }
            // mmgr monitor
            if (FD_ISSET(mmgr_get_fd(), &read_fds)) {
                LOGD("mmgr fd set");
                mmgr_handle(files);
            }
        }

    }
    close_mmgr_cli_source();
    free_config(first_modem_config);
    LOGE("Exiting main monitor loop");
    return -1;
}

void check_running_logs_service()
{
    char logservice[PROPERTY_VALUE_MAX];
    char logenable[PROPERTY_VALUE_MAX];

    property_get("init.svc.apk_logfs", logservice, "");
    property_get("persist.service.apklogfs.enable", logenable, "");
    if (strcmp(logservice, "running") && !strcmp(logenable, "1")) {
        LOGE("log service stopped whereas property is set .. restarting\n");
        property_set("ctl.start", "apk_logfs");
    }
}

void do_timeup()
{
    int fd;

    while (1) {
        check_running_logs_service();
        sleep(UPTIME_FREQUENCY);
        fd = open(HISTORY_UPTIME, O_RDWR | O_CREAT, 0666);
        if (fd < 0)
            LOGE("can not open file: %s\n", HISTORY_UPTIME);
        else
            close(fd);
    }
}

/*
* Name          : check_aplogs_tobackup
* Description   : backup a number of aplogs if a patten is found in a file
* Parameters    :
*   char *filename        -> filename where a pattern is searched
*   unsigned int files    -> nb max of logs destination directories (crashlog, aplogs, bz... )*/

static void check_aplogs_tobackup(char *filename, unsigned int files)
{
    int size;
    char *p, *p1;
    char filter[64]= "EIP is at ";
    char pattern[PROPERTY_VALUE_MAX] = "0";

    if (property_get(PROP_IPANIC_PATTERN, pattern, "") <= 0) {
          strncpy(pattern, "SGXInitialise", sizeof(pattern));
    }
    strncat(pattern, ";", PROPERTY_VALUE_MAX);

    p = pattern;

    do {
        p1 = strchr(p, ';');
        size = 0;
        if (p1) {
           filter[10] = '\0';
           size = p1 - p;
           if ((size > 0) && ((unsigned int) size < (sizeof(filter)-10))) {
                memcpy(filter + 10, p, size);
                filter[10 + size] = '\0';
                if (!find_str_in_file(filename, filter, NULL)) {
                  process_aplog_and_bz_trigger(NULL, "aplog_trigger", files);
                  break;
                }
           }
           p = ++p1;
        }
    } while (size > 0);
    return;
}

struct fabric_type {
    char *keyword;
    char *tail;
    char *name;
};

struct fabric_type ft_array[] = {
    {"DW0:", "f501", "MEMERR"},
    {"DW0:", "f502", "INSTERR"},
    {"DW0:", "f504", "SRAMECCERR"},
    {"DW0:", "00dd", "HWWDTLOGERR"},
    {"DW3:", "0000e101", "MEMERR"},
    {"DW3:", "0000e102", "INSTERR"},
    {"DW3:", "0000e104", "SRAMECCERR"},
    {"DW3:", "0000e107", "SCUWDT"},
    {"DW3:", "0000e108", "PLLLOCKERR"},
    {"DW3:", "0000e10a", "KERNELHANG"},
    {"DW3:", "0000e10b", "CHAABIHANG"},
};
struct fabric_type fake[] = {
    {"DW2:", "02608002", "FABRIC_FAKE"},
    {"DW3:", "ffd04100", "FABRIC_FAKE"},
};

enum {
    F_INFORMATIVE_MSG
};

static const struct fabric_type fabric_event[] = {
    [ F_INFORMATIVE_MSG ] = {"DW0:", "f506", "FIRMWARE"},
};

static int crashlog_check_fabric(char *reason, unsigned int files)
{
    char date_tmp[32];
    char date_tmp_2[32];
    struct stat info;
    time_t t;
    char destion[PATHMAX];
    char crashtype[32] = {'\0'};
    char event_name[10] = CRASHEVENT;
    int dir;
    unsigned int i = 0;
    char key[SHA1_DIGEST_LENGTH+1];
    struct tm *time_tmp;

    if ((stat(CURRENT_PROC_FABRIC_ERROR_NAME, &info) == 0)  || (test_flag == 1)) {

        strcpy(crashtype, FABRIC_ERROR);
        for (i = 0; i < sizeof(ft_array)/sizeof(struct fabric_type); i++)
            if (!find_str_in_file(CURRENT_PROC_FABRIC_ERROR_NAME, ft_array[i].keyword, ft_array[i].tail)) {
                   strncpy(crashtype, ft_array[i].name, sizeof(crashtype)-1);
                   if (strstr(crashtype, "HANG"))
                       strncpy(event_name, INFOEVENT, sizeof(event_name)-1);
        }
        if ((!find_str_in_file(CURRENT_PROC_FABRIC_ERROR_NAME, fake[0].keyword, fake[0].tail)) &&
          (!find_str_in_file(CURRENT_PROC_FABRIC_ERROR_NAME, fake[1].keyword, fake[1].tail))) {
                   strncpy(crashtype, fake[0].name, sizeof(crashtype)-1);
                   if (!strncmp(reason, "HWWDT_RESET", 11))
                       strcat(reason,"_FAKE");
        }
        if (!find_str_in_file(CURRENT_PROC_FABRIC_ERROR_NAME,
                              fabric_event[F_INFORMATIVE_MSG].keyword,
                              fabric_event[F_INFORMATIVE_MSG].tail)) {
            /* Informative_Msg from fabric -> info event */
            strncpy(event_name, INFOEVENT, sizeof(event_name)-1);
            strncpy(crashtype, fabric_event[F_INFORMATIVE_MSG].name,
                    sizeof(crashtype)-1);
        }

        time(&t);
        time_tmp = localtime((const time_t *)&t);
        PRINT_TIME(date_tmp, TIME_FORMAT_1, time_tmp);
        PRINT_TIME(date_tmp_2, TIME_FORMAT_2, time_tmp);
        dir = find_dir(files,CRASH_MODE);

        if (dir == -1) {
            LOGE("find dir %d for check fabric failed\n", files);
            compute_key(key, event_name, crashtype);
            LOGE("%-8s%-22s%-20s%s\n", event_name, key, date_tmp_2, crashtype);
            history_file_write(event_name, crashtype, NULL, NULL, NULL, key, date_tmp_2);
            del_file_more_lines(HISTORY_FILE);
            //Need to return 0 to avoid closing crashlogd
            return 0;
        }

        destion[0] = '\0';
        snprintf(destion, sizeof(destion), "%s%d/%s_%s.txt", CRASH_DIR, dir,
                FABRIC_ERROR_NAME, date_tmp);
        do_copy_eof(CURRENT_PROC_FABRIC_ERROR_NAME, destion);
        do_last_kmsg_copy(dir);

        destion[0] = '\0';
        snprintf(destion,sizeof(destion),"%s%d/",CRASH_DIR,dir);
        compute_key(key, event_name, crashtype);
        LOGE("%-8s%-22s%-20s%s %s\n", event_name, key, date_tmp_2, crashtype, destion);
        history_file_write(event_name, crashtype, NULL, destion, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        process_aplog_and_bz_trigger(NULL, "aplog_trigger", files);
    }
    return 0;
}

static int crashlog_check_panic(char *reason, unsigned int files)
{
    char date_tmp[32];
    char date_tmp_2[32];
    struct stat info;
    time_t t;
    char destion[PATHMAX];
    char crashtype[32] = {'\0'};
    int dir;
    char key[SHA1_DIGEST_LENGTH+1];
    struct tm *time_tmp;

    if ((stat(CURRENT_PANIC_CONSOLE_NAME, &info) == 0) || (test_flag == 1)) {

        if (!find_str_in_file(SAVED_CONSOLE_NAME, "Kernel panic - not syncing: Kernel Watchdog", NULL))
            strcpy(crashtype, KERNEL_SWWDT_CRASH);
        else if  (!find_str_in_file(SAVED_CONSOLE_NAME, "EIP is at pmu_sc_irq", NULL))
            // This panic is triggered by a fabric error
            // It is marked as a kernel panic linked to a HW watdchog
            // to create a link between these 2 critical crashes
            strcpy(crashtype, KERNEL_HWWDT_CRASH);
        else if (!find_str_in_file(SAVED_CONSOLE_NAME, "EIP is at panic_dbg_set", NULL)  || !find_str_in_file(SAVED_CONSOLE_NAME, "EIP is at kwd_trigger_open", NULL))
            strcpy(crashtype, KERNEL_FAKE_CRASH);
        else
            strcpy(crashtype, KERNEL_CRASH);

        if (find_str_in_file(SAVED_CONSOLE_NAME, "sdhci_pci_power_up_host: host controller power up is done", NULL))
             // An error is raised when the panic console file does not end normally
             crashlog_raise_infoerror(ERROREVENT, CRASHLOG_IPANIC_CORRUPTED);

        if (!strncmp(crashtype, KERNEL_FAKE_CRASH, sizeof(KERNEL_FAKE_CRASH)))
             strcat(reason,"_FAKE");
        else if (!strncmp(reason, "HWWDT_RESET_FAKE", 16))
             strcpy(crashtype, KERNEL_FAKE_CRASH);
        else if (!strncmp(reason,"HWWDT_RESET", 11))
             strcpy(crashtype, KERNEL_HWWDT_CRASH);
         else if (strncmp(reason,"SWWDT_RESET", 11) != 0)
             // In some corner cases, the startupreason is not correctly set
             // In this case, an ERROR is sent to have correct SWWDT metrics
             crashlog_raise_infoerror(ERROREVENT, CRASHLOG_SWWDT_MISSING);

        time(&t);
        time_tmp = localtime((const time_t *)&t);
        PRINT_TIME(date_tmp, TIME_FORMAT_1, time_tmp);
        PRINT_TIME(date_tmp_2, TIME_FORMAT_2, time_tmp);
        dir = find_dir(files,CRASH_MODE);

        if (dir == -1) {
            LOGE("find dir %d for check panic failed\n", files);
            compute_key(key, CRASHEVENT, crashtype);
            LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, date_tmp_2, crashtype);
            history_file_write(CRASHEVENT, crashtype, NULL, NULL, NULL, key, date_tmp_2);
            del_file_more_lines(HISTORY_FILE);
            //Need to return 0 to avoid closing crashlogd
            return 0;
        }

        destion[0] = '\0';
        snprintf(destion, sizeof(destion), "%s%d/%s_%s.txt", CRASH_DIR, dir,
                THREAD_NAME, date_tmp);
        do_copy(SAVED_THREAD_NAME, destion, FILESIZE_MAX);
        snprintf(destion,sizeof(destion),"%s%d/",CRASH_DIR,dir);

        destion[0] = '\0';
        snprintf(destion, sizeof(destion), "%s%d/%s_%s.txt", CRASH_DIR, dir,
                CONSOLE_NAME, date_tmp);
        do_copy(SAVED_CONSOLE_NAME, destion, FILESIZE_MAX);

        destion[0] = '\0';
        snprintf(destion, sizeof(destion), "%s%d/%s_%s.txt", CRASH_DIR, dir,
                LOGCAT_NAME, date_tmp);
        do_copy(SAVED_LOGCAT_NAME, destion, FILESIZE_MAX);
        do_last_kmsg_copy(dir);

        write_file(CURRENT_PANIC_CONSOLE_NAME, "1");

        destion[0] = '\0';
        snprintf(destion, sizeof(destion), "%s%d/", CRASH_DIR, dir);
        compute_key(key, CRASHEVENT, crashtype);
        LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, date_tmp_2, crashtype, destion);
        history_file_write(CRASHEVENT, crashtype, NULL, destion, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);

        // if a pattern is found in the console file, upload a large number of aplogs
        // property persist.crashlogd.panic.pattern is used to fill the list of pattern
        // Each pattern is split by a semicolon in the property
        check_aplogs_tobackup(SAVED_CONSOLE_NAME, files);
       }
    return 0;
}

static int crashlog_check_modem_shutdown(char *reason, unsigned int files)
{
    char date_tmp[32];
    char date_tmp_2[32];
    struct stat info;
    time_t t;
    char destion[PATHMAX];
    int dir;
    struct tm *time_tmp;
    char key[SHA1_DIGEST_LENGTH+1];

    if (stat(MODEM_SHUTDOWN_TRIGGER, &info) == 0) {

        time(&t);
        time_tmp = localtime((const time_t *)&t);
        PRINT_TIME(date_tmp, TIME_FORMAT_1, time_tmp);
        PRINT_TIME(date_tmp_2, TIME_FORMAT_2, time_tmp);
        compute_key(key, CRASHEVENT, MODEM_SHUTDOWN);

        dir = find_dir(files,CRASH_MODE);
        if (dir == -1) {
            LOGE("find dir %d for check modem shutdown failed\n", files);
            LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, date_tmp_2, MODEM_SHUTDOWN);
            history_file_write(CRASHEVENT, MODEM_SHUTDOWN, NULL, NULL, NULL, key, date_tmp_2);
            del_file_more_lines(HISTORY_FILE);
            remove(MODEM_SHUTDOWN_TRIGGER);
            //Need to return 0 to avoid closing crashlogd
            return 0;
        }

        destion[0] = '\0';
        snprintf(destion, sizeof(destion), "%s%d/", CRASH_DIR, dir);

        LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, date_tmp_2, MODEM_SHUTDOWN, destion);
        usleep(TIMEOUT_VALUE);
        do_log_copy(MODEM_SHUTDOWN, dir, date_tmp, APLOG_TYPE);
        do_last_kmsg_copy(dir);
        history_file_write(CRASHEVENT, MODEM_SHUTDOWN, NULL, destion, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        remove(MODEM_SHUTDOWN_TRIGGER);
    }
    return 0;
}



//this function requires a init_config_file before to work properly
void store_config(char *section, struct config_handle a_conf_handle){
    //for the moment, only modem is supported
    pchar module;
    pchar tmp;

    module = get_value_def(section,"module","UNDEFINED",&a_conf_handle);
    if (strcmp(module,"modem")){
        //for the moment, only modem is valid, code to removed when more modules are managed
        LOGE("extra configuration not supported for : %s\n",module);
        return;
    }else{
        LOGI("storing configuration : %s\n",section);
        //pconfig INIT
        pconfig newconf = malloc(sizeof(struct config));
        if(!newconf) {
            LOGE("%s:malloc failed\n", __FUNCTION__);
            return;
        }
        newconf->next   = NULL;
        newconf->eventname = NULL;
        newconf->matching_pattern = NULL;
        //TO IMPROVE replace harcoded value with array in parameter
        //Event name
        tmp = get_value(section,"eventname",&a_conf_handle);
        if (tmp){
            newconf->eventname = malloc(strlen(tmp)+1);/* add 1 for \0 */
            if(!newconf->eventname) {
                LOGE("%s:malloc failed\n", __FUNCTION__);
                free_config(newconf);
                return;
            }
            strncpy(newconf->eventname,tmp,strlen(tmp));
            newconf->eventname[strlen(tmp)]= '\0';
        }else{
            LOGE("wrong configuration for %s on %s \n",section,"eventname");
            free_config(newconf);
            return;
        }
        //matching_pattern
        tmp = get_value(section,"matching_pattern",&a_conf_handle);
        if (tmp){
            newconf->matching_pattern = malloc(strlen(tmp)+1);/* add 1 for \0 */
            if(!newconf->matching_pattern) {
                LOGE("%s:malloc failed\n", __FUNCTION__);
                free_config(newconf);
                return;
            }
            strncpy(newconf->matching_pattern,tmp,strlen(tmp));
            newconf->matching_pattern[strlen(tmp)]= '\0';
        }else{
            LOGE("wrong configuration for %s on %s \n",section,"matching_pattern");
            free_config(newconf);
            return;
        }
        //type
        tmp = get_value_def(section,"type","file",&a_conf_handle);
        if (tmp){
            if (!strcmp(tmp,"dir")){
                newconf->type = 1;
            }else{
                newconf->type = 0;
            }
        }else{
            LOGE("wrong configuration for %s on %s \n",section,"type");
            free_config(newconf);
            return;
        }
        //path
        tmp = get_value(section,"path_trigger",&a_conf_handle);
        if (tmp){
            snprintf(newconf->path, sizeof(newconf->path), "%s", tmp);
            LOGI("path loaded :  %s \n",newconf->path);
        }else{
            LOGW("missing configuration for %s on %s \n",section,"path_trigger");
            //path is not mandatory, config is still valid
        }
        //path_linked
        tmp = get_value(section,"path_linked",&a_conf_handle);
        if (tmp){
            snprintf(newconf->path_linked, sizeof(newconf->path_linked), "%s", tmp);
            LOGI("path_linked loaded :  %s \n",newconf->path_linked);
        }else{
        //path_linked is not mandatory, config is still valid
            newconf->path_linked[0] = '\0';
        }

        //event_class
        tmp = get_value_def(section,"event_class","CRASH",&a_conf_handle);
        if (tmp){
            if (!strcmp(tmp,"CRASH")){
                newconf->event_class = 0;
            }else if (!strcmp(tmp,"ERROR")){
                newconf->event_class = 1;
            }else if (!strcmp(tmp,"INFO")){
                newconf->event_class = 2;
            }else{
                newconf->event_class = 0;
            }
        }else{
            //default value is CRASH
            newconf->event_class = 0;
        }

        if (first_modem_config==NULL){
            first_modem_config = newconf;
        }else{
            current_modem_config->next = newconf;
        }
        current_modem_config = newconf;
    }
}

void load_config_by_pattern(char *section_pattern, char *key_pattern, struct config_handle a_conf_handle){
    pchar cur_section_name;
    LOGI("checking : %s\n",section_pattern );

    cur_section_name = get_first_section_name( section_pattern,&a_conf_handle);
    while (cur_section_name &&(sk_exists(cur_section_name, key_pattern, &a_conf_handle))){
        LOGI("storing config for :%s\n", cur_section_name);
        store_config(cur_section_name, a_conf_handle);
        cur_section_name = get_next_section_name( section_pattern,&a_conf_handle);
    }
}

void load_config(){
    struct stat info;
    char cur_extra_section[PATHMAX];
    struct config_handle my_conf_handle;
    int i_tmp;
    long l_tmp;
    //Check if config file exists
    if (stat(CRASHLOG_CONF_PATH, &info) == 0) {
        LOGI("Loading specific crashlog config\n");

        my_conf_handle.first=NULL;
        my_conf_handle.current=NULL;
        if (init_config_file(CRASHLOG_CONF_PATH, &my_conf_handle)>=0){
            //General config - uptime
            //TO IMPROVE : general config strategy to define properly
            if (sk_exists(GENERAL_CONF_PATTERN,"uptime_frequency",&my_conf_handle)){
                pchar tmp = get_value(GENERAL_CONF_PATTERN,"uptime_frequency",&my_conf_handle);
                if (tmp){
                    i_tmp = atoi(tmp);
                    if (i_tmp > 0){
                        current_uptime_hour_frequency = i_tmp;
                    }
                }
            }

            if (sk_exists(GENERAL_CONF_PATTERN,"sd_size_limit",&my_conf_handle)){
                pchar tmp = get_value(GENERAL_CONF_PATTERN,"sd_size_limit",&my_conf_handle);
                if (tmp){
                    l_tmp = atol(tmp);
                    if (l_tmp > 0){
                        current_sd_size_limit = l_tmp;
                    }
                }
            }

            if (sk_exists(GENERAL_CONF_PATTERN,"serial_device_id",&my_conf_handle)){
                pchar tmp = get_value(GENERAL_CONF_PATTERN,"serial_device_id",&my_conf_handle);
                if (tmp){
                    i_tmp = atoi(tmp);
                    if (i_tmp > 0){
                        current_serial_device_id = 1;
                    }
                }
            }
            load_config_by_pattern(NOTIFY_CONF_PATTERN,"matching_pattern",my_conf_handle);
            //ADD other config pattern HERE
            free_config_file(&my_conf_handle);
        }else{
            LOGI("specific crashlog config not found\n");
        }
    }
}

static int crashlog_check_recovery(unsigned int files)
{
    char date_tmp[32];
    char date_tmp_2[32];
    struct stat info;
    time_t t;
    char destion[PATHMAX];
    char destion2[PATHMAX];
    int dir;
    struct tm *time_tmp;
    char key[SHA1_DIGEST_LENGTH+1];

    //Check if trigger file exists
    if (stat(RECOVERY_ERROR_TRIGGER, &info) == 0) {
        // compute dates
        time(&t);
        time_tmp = localtime((const time_t *)&t);
        PRINT_TIME(date_tmp, TIME_FORMAT_1, time_tmp);
        PRINT_TIME(date_tmp_2, TIME_FORMAT_2, time_tmp);
        // compute crash id
        compute_key(key, CRASHEVENT, RECOVERY_ERROR);

        // get output crash dir
        dir = find_dir(files,CRASH_MODE);

        if (dir == -1) {
            LOGE("find dir %d for check recovery failed\n", files);
            LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, date_tmp_2, RECOVERY_ERROR);
            history_file_write(CRASHEVENT, RECOVERY_ERROR, NULL, NULL, NULL, key, date_tmp_2);
            del_file_more_lines(HISTORY_FILE);
            // remove trigger file
            remove(RECOVERY_ERROR_TRIGGER);
            //Need to return 0 to avoid closing crashlogd
            return 0;
        }

        destion[0] = '\0';
        snprintf(destion, sizeof(destion), "%s%d/", CRASH_DIR, dir);

        LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, date_tmp_2, RECOVERY_ERROR, destion);
        //copy log
        destion2[0] = '\0';
        snprintf(destion2, sizeof(destion2), "%s%s", destion, "recovery_last_log");
        do_copy(RECOVERY_ERROR_LOG, destion2, FILESIZE_MAX);
        do_last_kmsg_copy(dir);
        //Write event in history_event
        history_file_write(CRASHEVENT, RECOVERY_ERROR, NULL, destion, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
        // remove trigger file
        remove(RECOVERY_ERROR_TRIGGER);
    }
    return 0;
}

/**
 * @brief generate WDT crash event
 *
 * If startup reason contains HWWDT or SWWDT, generate a WDT crash event.
 * Copy aplogs and /proc/last_kmsg files in the event.
 *
 * @param reason - string containing the translated startup reason
 */
static int crashlog_check_startupreason(char *reason, unsigned int files)
{
    char date_tmp[32];
    char date_tmp_2[32];
    time_t t;
    char destion[PATHMAX];
    int dir;
    char key[SHA1_DIGEST_LENGTH+1];
    struct tm *time_tmp;

    if ((strstr(reason, "HWWDT_") || strstr(reason, "SWWDT_") || strstr(reason, "WDT_")) &&
        !strstr(reason, "FAKE")) {

        time(&t);
        time_tmp = localtime((const time_t *)&t);
        PRINT_TIME(date_tmp, TIME_FORMAT_1, time_tmp);
        PRINT_TIME(date_tmp_2, TIME_FORMAT_2, time_tmp);
        compute_key(key, CRASHEVENT, "WDT");

        dir = find_dir(files,CRASH_MODE);
        if (dir == -1) {
            LOGE("find dir %d for check startup reason failed\n", files);
            LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, date_tmp_2, "WDT");
            history_file_write(CRASHEVENT, "WDT", reason, NULL, NULL, key, date_tmp_2);
            del_file_more_lines(HISTORY_FILE);
            //Need to return 0 to avoid closing crashlogd
            return 0;
        }

        destion[0] = '\0';
        snprintf(destion, sizeof(destion), "%s%d/", CRASH_DIR, dir);
        LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, date_tmp_2, "WDT", destion);
        flush_aplog_atboot("WDT", dir, date_tmp);
        usleep(TIMEOUT_VALUE);
        do_log_copy("WDT", dir, date_tmp, APLOG_TYPE);
        do_last_kmsg_copy(dir);
        history_file_write(CRASHEVENT, "WDT", reason, destion, NULL, key, date_tmp_2);
        del_file_more_lines(HISTORY_FILE);
    }
    return 0;
}

static void crashlog_check_fs(char *filesys, char *type)
{
    struct stat info;

    strcpy(type, "UNKNOWN");

    if (stat(filesys, &info) == -1)
        strcpy(type, UNALIGNED_BLK_FS);
}

static int swupdated(char *buildname)
{
    struct stat info;
    FILE *fd;
    char currentbuild[PROPERTY_VALUE_MAX];

    if (stat(LOG_BUILDID, &info) == 0) {

        fd = fopen(LOG_BUILDID, "r");
        if (fd == NULL){
            LOGE("can not open file: %s\n", LOG_BUILDID);
            return 0;
        }
        fscanf(fd, "%s", currentbuild);
        fclose(fd);

        if (strcmp(currentbuild, buildname)) {
            fd = fopen(LOG_BUILDID, "w");
            if (fd == NULL){
                LOGE("can not open file: %s\n", LOG_BUILDID);
                return 0;
            }
            do_chown(LOG_BUILDID, "root", "log");
            fprintf(fd, "%s", buildname);
            fclose(fd);
            LOGI("Reset history after build update -> %s\n", buildname);
            return 1;
        }
    } else {
        fd = fopen(LOG_BUILDID, "w");
        if (fd == NULL){
            LOGE("can not open file: %s\n", LOG_BUILDID);
            return 0;
        }
        do_chown(LOG_BUILDID, "root", "log");
        fprintf(fd, "%s", buildname);
        fclose(fd);
        LOGI("Reset history after blank device update -> %s\n", buildname);
        return 1;

    }
    return 0;
}


static void reset_history(void)
{
    FILE *to;
    int fd;

    to = fopen(HISTORY_FILE, "w");
    if (to == NULL){
        LOGE("can not open file: %s\n", HISTORY_FILE);
        return;
    }
    do_chown(HISTORY_FILE, PERM_USER, PERM_GROUP);
    fprintf(to, "#V1.0 %-16s%-24s\n", CURRENT_UPTIME, "0000:00:00");
    fprintf(to, "#EVENT  ID                    DATE                 TYPE\n");
    fclose(to);

    fd = open(HISTORY_UPTIME, O_RDWR | O_CREAT, 0666);
    if (fd < 0){
        LOGE("open HISTORY_UPTIME error\n");
        return;
    }
    close(fd);
}

static void reset_crashlog(void)
{
    char path[PATHMAX];
    FILE *fd;

    snprintf(path, sizeof(path), CRASH_CURRENT_LOG);
    fd = fopen(path, "w");
    if (fd == NULL){
        LOGE("can not open file: %s\n", path);
        return;
    }
    fprintf(fd, "%4d", 0);
    fclose(fd);
}

static void reset_logs(char *path, int remove_dir)
{
    unsigned char isFile =0x8;
    char file[PATHMAX];
    DIR *d;
    struct dirent* de;
    d = opendir(path);

    if (d == 0)
        return;

    while ((de = readdir(d)) != 0) {
        if (!strcmp(de->d_name, ".") || !strcmp(de->d_name, ".."))
            continue;
        else {
            if ( de->d_type == isFile) {
                snprintf(file, sizeof(file), "%s/%s", path, de->d_name);
                remove(file);
            }else if (remove_dir==1){
                //TO improve : delete folder content recursively?
                //with current implementation remove will fail on folder with content
                snprintf(file, sizeof(file), "%s/%s", path, de->d_name);
                remove(file);
            }
        }
    }
    closedir(d);
}

static void reset_statslog(void)
{
    char path[PATHMAX];
    FILE *fd;
    snprintf(path, sizeof(path), STATS_CURRENT_LOG);
    fd = fopen(path, "w");
    if (fd == NULL){
        LOGE("can not open file: %s\n", path);
        return;
    }
    fprintf(fd, "%4d", 0);
    fclose(fd);
}

static void reset_aplogslog(void)
{
    char path[PATHMAX];
    FILE *fd;
    snprintf(path, sizeof(path), APLOGS_CURRENT_LOG);
    fd = fopen(path, "w");
    if (fd == NULL){
        LOGE("can not open file: %s\n", path);
        return;
    }
    fprintf(fd, "%4d", 0);
    fclose(fd);
}

static void reset_bzlog(void)
{
    char path[PATHMAX];
    FILE *fd;
    snprintf(path, sizeof(path), BZ_CURRENT_LOG);
    fd = fopen(path, "w");
    if (fd == NULL){
        LOGE("can not open file: %s\n", path);
        return;
    }
    fprintf(fd, "%4d", 0);
    fclose(fd);
}
static void reset_swupdate(void)
{
    reset_logs(HISTORY_CORE_DIR, 1);
    //don't remove folder for modemcrash
    reset_logs(LOGS_MODEM_DIR, 0);
    reset_logs(LOGS_GPS_DIR, 1);
    remove(MODEM_UUID);
    reset_crashlog();
    reset_statslog();
    reset_aplogslog();
    reset_history();
}

static void uptime_history(char *lastuptime)
{
    FILE *to;

    char name[32];
    char date_tmp[32];
    struct tm *time_tmp;
    time_t t;

    to = fopen(HISTORY_FILE, "r");
    if (to == NULL){
        LOGE("can not open file: %s\n", HISTORY_FILE);
        return;
    }
    fscanf(to, "#V1.0 %16s%24s\n", name, lastuptime);
    fclose(to);
    if (!memcmp(name, CURRENT_UPTIME, sizeof(CURRENT_UPTIME))) {

        to = fopen(HISTORY_FILE, "r+");
        if (to == NULL){
            LOGE("can not open file: %s\n", HISTORY_FILE);
            return;
        }
        fprintf(to, "#V1.0 %-16s%-24s\n", CURRENT_UPTIME, "0000:00:00");
        strcpy(name, PER_UPTIME);
        fseek(to, 0, SEEK_END);
        time(&t);
        time_tmp = localtime((const time_t *)&t);
        PRINT_TIME(date_tmp, TIME_FORMAT_2, time_tmp);
        fprintf(to, "%-8s00000000000000000000  %-20s%s\n", name, date_tmp, lastuptime);
        fclose(to);
    }
}

/*
* Name          : read_startupreason
* Description   : This function returns the decoded startup reason by reading
*                 the wake source from the command line. The wake src is translated
*                 to a crashtool startup reason.
*                 In case of a HW watchdog, the wake sources are translated to a HWWDT
*                 List of wake sources :
*    0x00: WAKE_BATT_INSERT
*    0x01: WAKE_PWR_BUTTON_PRESS
*    0x02: WAKE_RTC_TIMER
*    0x03: WAKE_USB_CHRG_INSERT
*    0x04: Reserved
*    0x05: WAKE_REAL_RESET -> COLD_RESET
*    0x06: WAKE_COLD_BOOT
*    0x07: WAKE_UNKNOWN
*    0x08: WAKE_KERNEL_WATCHDOG_RESET -> SWWDT_RESET
*    0x09: WAKE_SECURITY_WATCHDOG_RESET (Chaabi ou TXE/SEC) -> HWWDT_RESET_SECURITY
*    0x0A: WAKE_WATCHDOG_COUNTER_EXCEEDED -> WDT_COUNTER_EXCEEDED
*    0x0B: WAKE_POWER_SUPPLY_DETECTED
*    0x0C: WAKE_FASTBOOT_BUTTONS_COMBO
*    0x0D: WAKE_NO_MATCHING_OSIP_ENTRY
*    0x0E: WAKE_CRITICAL_BATTERY
*    0x0F: WAKE_INVALID_CHECKSUM
*    0x10: WAKE_FORCED_RESET
*    0x11: WAKE_ACDC_CHGR_INSERT
*    0x12: WAKE_PMIC_WATCHDOG_RESET (PMIC/EC) -> HWWDT_RESET_PMIC
*    0x13: WAKE_PLATFORM_WATCHDOG_RESET -> HWWDT_RESET_PLATFORM
*    0x14: WAKE_SC_WATCHDOG_RESET (SCU/PMC) -> HWWDT_RESET_SC
*
* Parameters    :
*   char *startupreason   -> string containing the translated startup reason */

static void read_startupreason(char *startupreason)
{
    char cmdline[512] = { '\0', };
    char *p, *endptr;
    unsigned long reason;
    static const char *bootmode_reason[] = {
        "BATT_INSERT",
        "PWR_BUTTON_PRESS",
        "RTC_TIMER",
        "USB_CHRG_INSERT",
        "Reserved",
        "COLD_RESET",
        "COLD_BOOT",
        "UNKNOWN",
        "SWWDT_RESET",
        "HWWDT_RESET_SECURITY",
        "WDT_COUNTER_EXCEEDED",
        "POWER_SUPPLY_DETECTED",
        "FASTBOOT_BUTTONS_COMBO",
        "NO_MATCHING_OSIP_ENTRY",
        "CRITICAL_BATTERY",
        "INVALID_CHECKSUM",
        "FORCED_RESET",
        "ACDC_CHGR_INSERT",
        "HWWDT_RESET_PMIC",
        "HWWDT_RESET_PLATFORM",
        "HWWDT_RESET_SC"};

    struct stat info;
    FILE *fd;

    strcpy(startupreason, bootmode_reason[7]);

    if (stat(CURRENT_KERNEL_CMDLINE, &info) == 0) {
        fd = fopen(CURRENT_KERNEL_CMDLINE, "r");
        if (fd == NULL){
            LOGE("can not open file: %s\n", CURRENT_KERNEL_CMDLINE);
            return;
        }
        fread(cmdline, 1, sizeof(cmdline)-1, fd);
        fclose(fd);
        p = strstr(cmdline, STARTUP_STR);
        if(p) {
            p += strlen(STARTUP_STR);
            if (!isspace(*p)) {
                errno = 0;
                reason=strtoul(p, &endptr, 16);
                if ((errno != ERANGE) &&
                    (endptr != p) &&
                    (reason >= 0) &&
                    (reason < (sizeof(bootmode_reason)/sizeof(char*)))) {
                    strcpy(startupreason, bootmode_reason[reason]);
                }else {
                    strcpy(startupreason, "UNKNOWN");
                }
            }
        }
    }
}
#ifdef FULL_REPORT
static void update_logs_permission(void)
{
    char value[PROPERTY_VALUE_MAX] = "0";

    if (property_get(PROP_COREDUMP, value, "") <= 0) {
        LOGE("Property %s not readable - core dump capture is disabled\n", PROP_COREDUMP);
    }

    if (!strncmp(value, "1", 1)) {
        LOGI("Folders /logs and /logs/core set to 0777\n");
        chmod(LOGS_DIR,0777);
        chmod(HISTORY_CORE_DIR,0777);
    }
}
#else
static void update_logs_permission(void) {}
#endif

//This function manages the path containing files triggering IPANIC, FABRICERR and WDT events treatment
//with a value read from a debug propertie
static void manage_ipanic_fabricerr_wdt_trigger_path(void)
{
    char debug_proc_path[PROPERTY_VALUE_MAX];

    if( property_get("crashlogd.debug.proc_path", debug_proc_path, NULL)!= 0)
    {
        snprintf(CURRENT_PROC_FABRIC_ERROR_NAME, sizeof(CURRENT_PROC_FABRIC_ERROR_NAME), "%s/%s", debug_proc_path, FABRIC_ERROR_NAME);
        snprintf(CURRENT_PANIC_CONSOLE_NAME, sizeof(CURRENT_PANIC_CONSOLE_NAME), "%s/%s", debug_proc_path, CONSOLE_NAME);
        snprintf(CURRENT_KERNEL_CMDLINE, sizeof(CURRENT_KERNEL_CMDLINE), "%s/%s", debug_proc_path, CMDLINE_NAME);
        LOGI("Test Mode : ipanic, fabricerr and wdt trigger path is %s\n", debug_proc_path);
    }
}

int main(int argc, char **argv)
{

    int i;
    int ret = 0;
    unsigned int files = 0xFFFFFFFF;
    char date_tmp[32];
    time_t t;
    char destion[PATHMAX];
    pid_t pid;
    unsigned int dir;
    char key[SHA1_DIGEST_LENGTH+1];
    struct tm *time_tmp;

    pthread_t thread;
    char value[PROPERTY_VALUE_MAX];

    if (argc > 2) {
        LOGE("USAGE: %s [number] \n", argv[0]);
        return -1;
    }

    if (argc == 2) {
        if(!memcmp(argv[1], "-modem", 6)){
#ifdef FULL_REPORT
            WDCOUNT_START = 10;
#else
            WDCOUNT_START = 7;
#endif
            WDCOUNT = WDCOUNT_START + 4;
            LOGI(" crashlogd only snoop modem \n");
        }
        else if(!memcmp(argv[1], "-test", 5)){
            test_flag = 1;
        }
        else if(!memcmp(argv[1], "-nomodem", 8)){
            WDCOUNT = (WDCOUNT - 3);
            LOGI(" crashlogd only snoop AP \n");
        }
        else{
            errno = 0;
            files = (unsigned int)strtoul(argv[1], 0, 0);

            if (errno) {
                LOGE(" saved files number must be digital \n");
                return -1;
            }
        }
    }
    //first thing to do : load configuration
    load_config();

    //Manage the path containing files triggering IPANIC and FABRICERR and WDT events
    manage_ipanic_fabricerr_wdt_trigger_path();

    if (property_get(BUILD_FIELD, buildVersion, "") <=0){
        get_version_info(SYS_PROP, BUILD_FIELD, buildVersion);
    }

    if (property_get(BOARD_FIELD, boardVersion, "") <=0){
        get_version_info(SYS_PROP, BOARD_FIELD, boardVersion);
    }
    if (current_serial_device_id == 1){
        property_get("ro.serialno", uuid, "empty_serial");
        write_uid(LOG_UUID, uuid);
    }else{
        read_proc_uid(PROC_UUID, LOG_UUID, uuid, "Medfield");
    }
    read_sys_spid(LOG_SPID);

    sdcard_available(CRASH_MODE);

    // check startup reason and sw update
    char startupreason[32] = { '\0', };
    char flashtype[32] = { '\0', };
    char encryptstate[16] = { '\0', };
    struct stat st;
    char lastuptime[32];
    char crypt_state[PROPERTY_VALUE_MAX];
    char encrypt_progress[PROPERTY_VALUE_MAX];
    char decrypt[PROPERTY_VALUE_MAX];
    char token[PROPERTY_VALUE_MAX];

    strcpy(encryptstate,"DECRYPTED");

    property_get("ro.crypto.state", crypt_state, "unencrypted");
    property_get("vold.encrypt_progress",encrypt_progress,"");
    property_get("vold.decrypt", decrypt, "");
    property_get("crashlogd.token", token, "");

    if ((!strcmp(crypt_state, "unencrypted")) && ( !encrypt_progress[0])){
        if (strcmp(token, ""))
            goto next2;
        LOGI("phone enter state: normal start.\n");
        if (swupdated(buildVersion)) {
            strcpy(lastuptime, "0000:00:00");
            strcpy(startupreason,"SWUPDATE");
            crashlog_check_fs(BLANKPHONE_FILE, flashtype);
            reset_swupdate();
        }
        else {
            read_startupreason(startupreason);
            uptime_history(lastuptime);
        }
        goto next;
    }

    if (encrypt_progress[0]){
        LOGI("phone enter state: encrypting.\n");
        strcpy(encryptstate,"DECRYPTED");
        goto next2;
    }

   if ((!strcmp(crypt_state, "encrypted")) && !strcmp(decrypt, "trigger_restart_framework")){
        if (strcmp(token, ""))
            goto next2;
        LOGI("phone enter state: phone encrypted.\n");
        strcpy(encryptstate,"ENCRYPTED");
        if (swupdated(buildVersion)) {
            strcpy(lastuptime, "0000:00:00");
            strcpy(startupreason,"SWUPDATE");
            crashlog_check_fs(BLANKPHONE_FILE, flashtype);
            reset_swupdate();
        }
        else {
            read_startupreason(startupreason);
            uptime_history(lastuptime);
        }
        goto next;
    }

next:
    crashlog_check_fabric(startupreason, files);
    crashlog_check_panic(startupreason, files);
    crashlog_check_modem_shutdown(startupreason, files);
    crashlog_check_startupreason(startupreason, files);
    crashlog_check_recovery(files);

    time(&t);
    time_tmp = localtime((const time_t *)&t);
    PRINT_TIME(date_tmp, TIME_FORMAT_2, time_tmp);
    compute_key(key, SYS_REBOOT, startupreason);
    LOGE("%-8s%-22s%-20s%s\n", SYS_REBOOT, key, date_tmp, startupreason);
    history_file_write(SYS_REBOOT, startupreason, NULL, NULL, lastuptime, key, date_tmp);

    if (!strncmp(flashtype, UNALIGNED_BLK_FS, sizeof(UNALIGNED_BLK_FS))) {
        compute_key(key, INFOEVENT, flashtype);
        LOGE("%-8s%-22s%-20s%s\n", INFOEVENT, key, date_tmp, flashtype);
        history_file_write(INFOEVENT, flashtype, NULL, NULL, NULL, key, date_tmp);
    }

    compute_key(key, STATEEVENT, encryptstate);
    LOGE("%-8s%-22s%-20s%s\n", STATEEVENT, key, date_tmp, encryptstate);
    history_file_write(STATEEVENT, encryptstate, NULL, NULL, NULL, key, date_tmp);

    del_file_more_lines(HISTORY_FILE);
    notify_crashreport();

next2:
    update_logs_permission();
    ret = pthread_create(&thread, NULL, (void *)do_timeup, NULL);
    if (ret < 0) {
        LOGE("pthread_create error");
        return -1;
    }

    monitor_crashenv();
    check_crashlog_dead();

    return do_monitor(files);
}
