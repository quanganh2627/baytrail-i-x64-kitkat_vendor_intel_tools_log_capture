/* Copyright (C) Intel 2013
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

/**
 * @file privconfig.h
 * @brief File containing every constants definitions used in crashlogd source
 * code such as properties name, files name, directories name, various fields...
 */

#ifndef __CRASHLOGD_H__
#define __CRASHLOGD_H__

#define LOG_TAG                 "CRASHLOG"
#include <cutils/log.h>

#ifndef LOGE
#define LOGE ALOGE
#define LOGI ALOGI
#endif

/* CONSTRAINTS */
#define KB                      (1024)
#define MB                      (KB*KB)
#define MAX_RECORDS             5000
#define MAX_DIRS                1000
#define PATHMAX                 512
#define UPTIME_HOUR_FREQUENCY   12
#define TIMESTAMP_MAX_SIZE      10 /* Unix style timestamp on 10 characters max.*/
#define MAXFILESIZE             (10*MB)
#define MAXPATHSIZE             (512)
/* find_str_in_file was implemented with 4KB*/
#define MAXLINESIZE             MAX((2 * PROPERTY_VALUE_MAX), (4 * KB))
#define CPBUFFERSIZE            (4*KB)
#define SIZE_FOOTPRINT_MAX      ((PROPERTY_VALUE_MAX + 1) * 11)
#define TIMEOUT_VALUE           (20*1000)
#define MAX_WAIT_MMGR_CONNECT_SECONDS  5
#define MMGR_CONNECT_RETRY_TIME_MS     200
#define CMDSIZE_MAX             ((21*20) + 1)
#define UPTIME_FREQUENCY        (5 * 60)
#define MMGRMAXSTRING           (20)
#define MMGRMAXEXTRA            (512)

/* FIELDS DEFINITIONS */
#define PERM_USER               "system"
#define PERM_GROUP              "log"
#define STARTUP_STR             "androidboot.wakesrc="
#define PER_UPTIME              "UPTIME"
#define USBBOGUS                "USBBOGUS"
#define ERROREVENT              "ERROR"
#define CRASHEVENT              "CRASH"
#define STATSEVENT              "STATS"
#define INFOEVENT               "INFO"
#define STATEEVENT              "STATE"
#define APLOGEVENT              "APLOG"
#define BZEVENT                 "BZ"
#define KERNEL_CRASH            "IPANIC"
#define KERNEL_SWWDT_CRASH      "IPANIC_SWWDT"
#define KERNEL_HWWDT_CRASH      "IPANIC_HWWDT"
#define FABRIC_FAKE_CRASH       "FABRIC_FAKE"
#define KERNEL_FAKE_CRASH       "IPANIC_FAKE"
#define IPANIC_CORRUPTED        "IPANIC_CORRUPTED"
#define KDUMP_CRASH             "KDUMP"
#define MODEM_SHUTDOWN          "MSHUTDOWN"
#define BZTRIGGER               "bz_trigger"
#define SCREENSHOT_PATTERN      "SCREENSHOT="
#define APLOG_DEPTH_DEF         "5"
#define APLOG_NB_PACKET_DEF     "1"
#define BZMANUAL                "MANUAL"
#define UNALIGNED_BLK_FS        "NO_BLANKPHONE"
#define SYS_REBOOT              "REBOOT"
#define FABRIC_ERROR            "FABRICERR"
#define RECOVERY_ERROR          "RECOVERY_ERROR"
#define CRASHLOG_ERROR_DEAD     "CRASHLOG_DEAD"
#define CRASHLOG_ERROR_PATH     "CRASHLOG_PATH"
#define CRASHLOG_SWWDT_MISSING  "SWWDT_MISSING"
#define SYSSERVER_EVNAME        "UIWDT"
#define ANR_EVNAME              "ANR"
#define TOMBSTONE_EVNAME        "TOMBSTONE"
#define JAVACRASH_EVNAME        "JAVACRASH"
#define APCORE_EVNAME           "APCOREDUMP"
#define LOST_EVNAME             "LOST_DROPBOX"
#define HPROF_EVNAME            "HPROF"
#define STATSTRIG_EVNAME        "STTRIG"
#define APLOGTRIG_EVNAME        "APLOGTRIG"
#define INFOTRIG_EVNAME         "INFOTRIG"
#define ERRORTRIG_EVNAME        "ERRORTRIG"
#define CMDTRIG_EVNAME          "CMDTRIG"
#define UPTIME_EVNAME           "CURRENTUPTIME"
#define MDMCRASH_EVNAME         "MPANIC"
#define APIMR_EVNAME            "APIMR"
#define MRST_EVNAME             "MRESET"
#define EXTRA_NAME              "EXTRA"
#define NOTIFY_CONF_PATTERN     "INOTIFY"
#define GENERAL_CONF_PATTERN    "GENERAL"
#define MPANIC_ABORT            "COREDUMP_ABORTED_BY_PLATFORM_SHUTDOWN"
#define CRASHLOG_WATCHER_ERROR  "CRASHLOG_WATCHER"

/*
 * Take care that this enum is ALWAYS aligned with
 * the array declared in inotify_handler.c
 * The type value is identical to the index in the table
 */
enum {
    LOST_TYPE = 0,
    SYSSERVER_TYPE,
    ANR_TYPE,
    TOMBSTONE_TYPE,
    JAVACRASH_TYPE,
    APCORE_TYPE,
    HPROF_TYPE,
    STATTRIG_TYPE,
    INFOTRIG_TYPE,
    ERRORTRIG_TYPE,
    APLOGTRIG_TYPE,
    CMDTRIG_TYPE,
    UPTIME_TYPE,
    MDMCRASH_TYPE,
    APIMR_TYPE,
    MRST_TYPE,
};

enum {
    CRASH_MODE = 0,
    CRASH_MODE_NOSD,
    STATS_MODE,
    APLOGS_MODE,
    BZ_MODE,
    KDUMP_MODE,
};

enum {
    APLOG_TYPE = 0,
    BPLOG_TYPE,
    APLOG_STATS_TYPE,
    KDUMP_TYPE,
    BPLOG_TYPE_OLD,
};
/*Define uptime mode for raise_event function */
enum {
    NO_UPTIME = 0,
    UPTIME,
    UPTIME_BOOT,
};

/* DIM returns the dimention of an array */
#define DIM(array)              ( sizeof(array) / sizeof(array[0]) )

/* PROPERTIES */
#define PROP_CRASH_MODE         "persist.sys.crashlogd.mode"
#define PROP_PROFILE            "persist.service.profile.enable"
#define PROP_BOOT_STATUS        "sys.boot_completed"
#define PROP_BUILD_FIELD        "ro.build.version.incremental"
#define PROP_BOARD_FIELD        "ro.product.model"
#define KERNEL_FIELD            "sys.kernel.version"
#define USER_FIELD              "ro.build.user"
#define HOST_FIELD              "ro.build.host"
#define IFWI_FIELD              "sys.ifwi.version"
#define SCUFW_VERSION           "sys.scu.version"
#define PUNIT_VERSION           "sys.punit.version"
#define IAFW_VERSION            "sys.ia32.version"
#define VALHOOKS_VERSION        "sys.valhooks.version"
#define FINGERPRINT_FIELD       "ro.build.fingerprint"
#define MODEM_FIELD             "gsm.version.baseband"
#define IMEI_FIELD              "persist.radio.device.imei"
#define PROP_CRASH              "persist.service.crashlog.enable"
#define PROP_COREDUMP           "persist.core.enabled"
#define PROP_CRASH_MODE         "persist.sys.crashlogd.mode"
#define PROP_ANR_USERSTACK      "persist.anr.userstack.disabled"
#define PROP_IPANIC_PATTERN     "persist.crashlogd.panic.pattern"
#define PROP_APLOG_DEPTH        "persist.crashreport.aplogdepth"
#define PROP_APLOG_NB_PACKET    "persist.crashreport.packet"
#define PROP_LOGSYSTEMSTATE     "init.svc.logsystemstate"

/* DIRECTORIES */
#ifndef __LINUX__
#define RESDIR                  ""
#else
#define RESDIR                  "res"
#endif
#define LOGS_DIR                RESDIR "/logs"
#define SDCARD_DIR              RESDIR "/mnt/sdcard"
#define PROC_DIR                RESDIR "/proc"
#define DATA_DIR                RESDIR "/data"
#define SYS_DIR                 RESDIR "/system"
#define CACHE_DIR               RESDIR "/cache"
#define PANIC_DIR               DATA_DIR "/dontpanic"
#define SDCARD_LOGS_DIR         SDCARD_DIR "/logs"
#define SDCARD_CRASH_DIR        SDCARD_LOGS_DIR "/crashlog"
#define EMMC_CRASH_DIR          LOGS_DIR "/crashlog"
#define SDCARD_STATS_DIR        SDCARD_LOGS_DIR "/stats"
#define EMMC_STATS_DIR          LOGS_DIR "/stats"
#define SDCARD_APLOGS_DIR       SDCARD_LOGS_DIR "/aplogs"
#define EMMC_APLOGS_DIR         LOGS_DIR "/aplogs"
#define SDCARD_BZ_DIR           SDCARD_LOGS_DIR "/bz"
#define EMMC_BZ_DIR             LOGS_DIR "/bz"
#define LOGINFO_DIR             LOGS_DIR "/info"
#define STAT_DIR                LOGS_DIR "/stats"
#define APLOG_DIR               LOGS_DIR "/aplogs"
#define DROPBOX_DIR             DATA_DIR "/system/dropbox"
#define TOMBSTONE_DIR           DATA_DIR "/tombstones"
#define SYS_SPID_DIR            RESDIR "/sys/spid"
#define LOGS_MEDIA_DIR          DATA_DIR "/media/logs"
#define KDUMP_DIR               LOGS_MEDIA_DIR "/kdump"
#define SDSIZE_CURRENT_LOG      LOGS_DIR "/currentsdsize"
#define REBOOT_DIR              RESDIR "/sys/kernel/debug/intel_scu_osnib"
#define EVENTS_DIR              LOGS_DIR "/events"

/* FILES */
#define SYS_PROP                SYS_DIR "/build.prop"
#define HISTORY_FILE            LOGS_DIR "/history_event"
#define UPTIME_FILE             LOGS_DIR "/uptime"
#define BZ_CURRENT_LOG          LOGS_DIR "/currentbzlog"
#define CRASH_CURRENT_LOG       LOGS_DIR "/currentcrashlog"
#define STATS_CURRENT_LOG       LOGS_DIR "/currentstatslog"
#define APLOGS_CURRENT_LOG      LOGS_DIR "/currentaplogslog"
#define LOG_UUID                LOGS_DIR "/uuid.txt"
#define LOG_BUILDID             LOGS_DIR "/buildid.txt"
#define MODEM_UUID              LOGS_DIR "/modemid.txt"
#define APLOG_FILE_0            LOGS_DIR "/aplog"
#define APLOG_FILE_1            LOGS_DIR "/aplog.1"
#define BPLOG_FILE_0            LOGS_DIR "/bplog"
#define BPLOG_FILE_1            LOGS_DIR "/bplog.1"
#define BPLOG_FILE_1_OLD        LOGS_DIR "/bplog.1.istp"
#define BPLOG_FILE_2_OLD        LOGS_DIR "/bplog.2.istp"
#define LOGRESERVED             LOGS_DIR "/reserved"
#define HISTORY_UPTIME          LOGS_DIR "/uptime"
#define HISTORY_CORE_DIR        LOGS_DIR "/core"
#define LOGS_MODEM_DIR          LOGS_DIR "/modemcrash"
#define LOGS_GPS_DIR            LOGS_DIR "/gps"
#define APLOG_FILE_BOOT         LOGS_DIR "/aplog_boot"
#define BLANKPHONE_FILE         LOGS_DIR "/flashing/blankphone_file"
#define MODEM_SHUTDOWN_TRIGGER  LOGS_DIR "/modemcrash/mshutdown.txt"
#define LOG_SPID                LOGS_DIR "/spid.txt"
#define LAST_KMSG_FILE          "last_kmsg"
#define CONSOLE_NAME            "emmc_ipanic_console"
#define CONSOLE_RAM             "ram_ipanic_console"
#define THREAD_NAME             "emmc_ipanic_threads"
#define LOGCAT_NAME             "emmc_ipanic_logcat"
#define FABRIC_ERROR_NAME       "ipanic_fabric_err"
#define CMDLINE_NAME            "cmdline"
#define CRASHFILE_NAME          "crashfile"
#define BPLOG_NAME              "bplog"
#define LAST_KMSG               PROC_DIR "/" LAST_KMSG_FILE
#define PANIC_CONSOLE_NAME      PROC_DIR "/" CONSOLE_NAME
#define PANIC_THREAD_NAME       PROC_DIR "/" THREAD_NAME
#define PROC_FABRIC_ERROR_NAME  PROC_DIR "/" FABRIC_ERROR_NAME
#define KERNEL_CMDLINE          PROC_DIR "/" CMDLINE_NAME
#define PROC_UUID               PROC_DIR "/emmc0_id_entry"
#define SAVED_CONSOLE_NAME      PANIC_DIR "/" CONSOLE_NAME
#define SAVED_THREAD_NAME       PANIC_DIR "/" THREAD_NAME
#define SAVED_LOGCAT_NAME       PANIC_DIR "/" LOGCAT_NAME
#define SAVED_FABRIC_ERROR_NAME PANIC_DIR "/" FABRIC_ERROR_NAME
#define SAVED_PANIC_RAM         PANIC_DIR "/" CONSOLE_RAM
#define RECOVERY_ERROR_TRIGGER  CACHE_DIR "/recovery/recoveryfail"
#define RECOVERY_ERROR_LOG      CACHE_DIR "/recovery/last_log"
#define SYS_SPID_1              SYS_SPID_DIR "/vendor_id"
#define SYS_SPID_2              SYS_SPID_DIR "/manufacturer_id"
#define SYS_SPID_3              SYS_SPID_DIR "/customer_id"
#define SYS_SPID_4              SYS_SPID_DIR "/platform_family_id"
#define SYS_SPID_5              SYS_SPID_DIR "/product_line_id"
#define SYS_SPID_6              SYS_SPID_DIR "/hardware_id"
#define KDUMP_START_FLAG        KDUMP_DIR "/kdumphappened"
#define KDUMP_FILE_NAME         KDUMP_DIR "/kdumpfile.core"
#define KDUMP_FINISH_FLAG       KDUMP_DIR "/kdumpfinished"
#define KDUMP_CRASH_DIR         LOGS_MEDIA_DIR "/crashlog"
#define ANR_DUPLICATE_INFOERROR         "anr_duplicate_infoevent"
#define ANR_DUPLICATE_DATA              "anr_duplicate_data.txt"
#define UIWDT_DUPLICATE_DATA            "uiwdt_duplicate_data.txt"
#define JAVACRASH_DUPLICATE_DATA        "javacrash_duplicate_data.txt"
#define JAVACRASH_DUPLICATE_INFOERROR   "javacrash_duplicate_infoevent"
#define UIWDT_DUPLICATE_INFOERROR       "uiwdt_duplicate_infoevent"
#define CRASHLOG_WATCHER_INFOEVENT      "crashlog_watcher_infoevent"
#define CRASHLOG_CONF_PATH              "/system/etc/crashlog.conf"
#define MCD_PROCESSING          LOGS_DIR "/mcd_processing"
#define RESET_SOURCE_0          REBOOT_DIR "/RESETSRC0"
#define RESET_SOURCE_1          REBOOT_DIR "/RESETSRC1"
#define RESET_IRQ_1             REBOOT_DIR "/RESETIRQ1"
#define RESET_IRQ_2             REBOOT_DIR "/RESETIRQ2"
#define EVENTFILE_NAME          "eventfile"

/* MACROS */
#define MIN(a,b)                ((a) < (b) ? (a) : (b))
#define MAX(a,b)                ((a) > (b) ? (a) : (b))

/* SYSTEM COMMANDS */
#define SDSIZE_SYSTEM_CMD "du -sk /mnt/sdcard/logs/ > /logs/currentsdsize"

extern char *CRASH_DIR;
extern char *STATS_DIR;
extern char *APLOGS_DIR;
extern char *BZ_DIR;
extern char CURRENT_PANIC_CONSOLE_NAME[PATHMAX];
extern char CURRENT_PROC_FABRIC_ERROR_NAME[PATHMAX];
extern char CURRENT_KERNEL_CMDLINE[PATHMAX];
extern int gmaxfiles;

#endif /* __CRASHLOGD_H__ */

