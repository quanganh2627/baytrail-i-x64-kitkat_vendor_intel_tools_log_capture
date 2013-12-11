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
 * @file main.c
 * @brief File containing the entry point of crashlogd where are performed the
 * earlier checks, the initialization of the various events sources and the
 * launch of the main loop monitoring those events (file system watcher, modem
 * manager monitor, log reader...).
 *
 */

#include "inotify_handler.h"
#include "startupreason.h"
#include "mmgr_source.h"
#include "crashutils.h"
#include "privconfig.h"
#include "usercrash.h"
#include "anruiwdt.h"
#include "recovery.h"
#include "dropbox.h"
#include "fsutils.h"
#include "history.h"
#include "trigger.h"
#include "fabric.h"
#include "modem.h"
#include "panic.h"
#include "config_handler.h"
#include "ramdump.h"
#include "fw_update.h"
#include "tcs_wrapper.h"
#include "kct_netlink.h"
#include "iptrak.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <pthread.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <stdio.h>
#include <errno.h>
#include <ctype.h>

#include <cutils/properties.h>
#include <cutils/log.h>

extern char gbuildversion[PROPERTY_VALUE_MAX];
extern char gboardversion[PROPERTY_VALUE_MAX];
extern char guuid[256];

extern pconfig g_first_modem_config;
extern int g_current_serial_device_id;

/* global flag indicating crashlogd mode */
enum  crashlog_mode g_crashlog_mode;

int gmaxfiles = MAX_DIRS;
char *CRASH_DIR = NULL;
char *STATS_DIR = NULL;
char *APLOGS_DIR = NULL;
char *BZ_DIR = NULL;

//Variables containing paths of files triggering IPANIC & FABRICERR & WDT treatment
char CURRENT_PANIC_CONSOLE_NAME[PATHMAX]={PANIC_CONSOLE_NAME};
char CURRENT_PANIC_HEADER_NAME[PATHMAX]={PANIC_HEADER_NAME};
char CURRENT_PROC_FABRIC_ERROR_NAME[PATHMAX]={PROC_FABRIC_ERROR_NAME};
char CURRENT_PROC_OFFLINE_SCU_LOG_NAME[PATHMAX]={PROC_OFFLINE_SCU_LOG_NAME};
char CURRENT_KERNEL_CMDLINE[PATHMAX]={KERNEL_CMDLINE};

int process_command_event(struct watch_entry *entry, struct inotify_event *event) {
    char path[PATHMAX];
    char action[MAXLINESIZE];
    char args[MAXLINESIZE];
    char line[MAXLINESIZE];
    FILE *fd;

    snprintf(path, sizeof(path),"%s/%s", entry->eventpath, event->name);
    if ( (fd = fopen(path, "r")) == NULL) {
        LOGE("%s: Cannot open %s - %s\n", __FUNCTION__, path, strerror(errno));
        /* Tries to remove it in case of an improbable error */
        rmfr(path);
        return -errno;
    }

    /* now, read the file and get the last action/args found */
    action[0] = 0;
    args[0] = 0;
    while ( freadline(fd, line) > 0 ) {
        sscanf(line, "ACTION=%s", action);
        sscanf(line, "ARGS=%s", args);
    }
    fclose(fd);
    rmfr(path);
    if (!action[0] || !args[0]) {
        LOGE("%s: Cannot find action/args in %s\n", __FUNCTION__, path);
        return -1;
    }

    /* here is the delete action */
    if (!strcmp(action, "DELETE")) {
        LOGI("%s: Handles delete %s\n", __FUNCTION__, args);
        update_history_on_cmd_delete(args);
        return 0;
    }
    LOGE("%s: Unknown action found in %s: %s %s\n", __FUNCTION__,
        path, action, args);
    return -1;
}

/**
 * @brief Check that the modem_version file is up-to-date
 * and (re-)writes it otherwise.
 *
 * @return int
 *  - < 0: if an error occured
 *  - = 0: if the file was up-to-date
 *  - > 0: if the file was updated successfully
 */
static int update_modem_name() {
    FILE *fd = NULL;
    int res = 0;
    char modem_name[PROPERTY_VALUE_MAX] = "";
    char previous_modem_name[PROPERTY_VALUE_MAX] = "";

    /*
     * Retrieve modem name
     */
    res = get_modem_name(modem_name);
    if(res < 0) {
        LOGE("%s: Could not retrieve modem name, file %s will not be written.\n", __FUNCTION__, LOG_MODEM_VERSION);
        return res;
    }

    /*
     * Check the modem version file.
     */
    if ( (fd = fopen(LOG_MODEM_VERSION, "r")) != NULL) {
        res = fscanf(fd, "%s", previous_modem_name);
        fclose(fd);
        /* Check whether there is something to do or not */
        if ( res == 1 && !strncmp(previous_modem_name, modem_name, PROPERTY_VALUE_MAX) ) {
            /* Modem version has not changed we can stop here */
            return 0;
        }
    }

    /*
     * Update file
     */
    if ( (fd = fopen(LOG_MODEM_VERSION, "w")) == NULL) {
        LOGE("%s: Could not open file %s for writing.\n",
            __FUNCTION__,
            LOG_MODEM_VERSION);
        return -1;
    }
    fprintf(fd, "%s", modem_name);
    fclose(fd);

    /* Change file ownership and permissions */
    if(chmod(LOG_MODEM_VERSION, S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH) < 0) {
        LOGE("%s: Cannot change %s file permissions %s\n",
            __FUNCTION__,
            LOG_MODEM_VERSION,
            strerror(errno));
        return -1;
    }
    do_chown(LOG_MODEM_VERSION, "root", "log");

    /*
     * Return the result
     */
    return res;
}

static int swupdated(char *buildname) {
    FILE *fd;
    int res;
    char currentbuild[PROPERTY_VALUE_MAX];

    if ( (fd = fopen(LOG_BUILDID, "r")) != NULL) {
        res = fscanf(fd, "%s", currentbuild);
        fclose(fd);
        if ( res == 1 && !strcmp(currentbuild, buildname) ) {
            /* buildid is the same */
            return 0;
        }
    }

    /* build changed or file not found, overwrite it */
    if ( (fd = fopen(LOG_BUILDID, "w")) == NULL) {
        LOGE("%s: Cannot open or create %s - %s\n", __FUNCTION__,
            LOG_BUILDID, strerror(errno));
        return 0;
    }
    do_chown(LOG_BUILDID, "root", "log");
    fprintf(fd, "%s", buildname);
    fclose(fd);
    LOGI("Reset history after build update -> %s\n", buildname);
    return 1;
}

/**
 * @brief Remove directory content. Only files in directory if remove_dir = 0.
 * Otherwise, all directory content is removed recursively.
 *
 * @return void.
 */
static void reset_logdir(char *path, int remove_dir) {
    struct stat info;

    if (stat(path,&info)) {
        LOGE("%s: Cannot reset logdir %s - %s\n", __FUNCTION__,
            path, strerror(errno));
        return;
    }
    rmfr_specific(path, remove_dir);
    if (remove_dir) {
        mkdir(path, info.st_mode);
        chown(path, info.st_uid, info.st_gid);
    }
}

static void reset_after_swupdate(void)
{
    reset_logdir(HISTORY_CORE_DIR, 1);
    /* don't remove folder for modemcrash */
    reset_logdir(LOGS_MODEM_DIR, 0);
    reset_logdir(LOGS_GPS_DIR, 1);
    remove(MODEM_UUID);
    reset_file(CRASH_CURRENT_LOG);
    reset_file(STATS_CURRENT_LOG);
    reset_file(APLOGS_CURRENT_LOG);
    reset_uptime_history();
}

static void timeup_thread_mainloop()
{
    int fd;
    char logservice[PROPERTY_VALUE_MAX];
    char logenable[PROPERTY_VALUE_MAX];

    while (1) {
        /*
         * checks the logging services are still alive...
         * restart it if necessary
         */
        property_get("init.svc.apk_logfs", logservice, "");
        property_get("persist.service.apklogfs.enable", logenable, "");
        if (strcmp(logservice, "running") && !strcmp(logenable, "1")) {
            LOGE("log service stopped whereas property is set .. restarting\n");
            start_daemon("apk_logfs");
        }
#ifdef __TEST__
        sleep(5);
#else
        sleep(UPTIME_FREQUENCY);
#endif
        fd = open(HISTORY_UPTIME, O_RDWR | O_CREAT, 0666);
        if (fd < 0)
            LOGE("%s: can not open file: %s\n", __FUNCTION__, HISTORY_UPTIME);
        else
            close(fd);
    }
}

static void early_check_nomain(char *boot_mode, int test) {

    char startupreason[32] = { '\0', };
    const char *datelong;
    char *key;

    read_startupreason(startupreason);

    key = raise_event_nouptime(SYS_REBOOT, startupreason, NULL, NULL);
    datelong = get_current_time_long(0);
    LOGE("%-8s%-22s%-20s%s\n", SYS_REBOOT, key, datelong, startupreason);
    free(key);

    key = raise_event_nouptime(STATEEVENT, boot_mode, NULL, NULL);
    LOGE("%-8s%-22s%-20s%s\n", STATEEVENT, key, datelong, boot_mode);
    free(key);
}


static void early_check(char *encryptstate, int test) {

    char startupreason[32] = { '\0', };
    char flashtype[32] = { '\0', };
    char watchdog[16] = { '\0', };
    int modem_name_check_result = 0;
    const char *datelong;
    char *key;
    struct stat info;

    if (swupdated(gbuildversion) == 1) {
        strcpy(startupreason,"SWUPDATE");
        if (stat(BLANKPHONE_FILE, &info) == -1)
            strcpy(flashtype, UNALIGNED_BLK_FS);
        else strcpy(flashtype, "UNKNOWN");
        reset_after_swupdate();
    }
    else {
        read_startupreason(startupreason);
        uptime_history();
    }

    strcpy(watchdog,"WDT");

    crashlog_check_fabric_events(startupreason, watchdog, test);
    crashlog_check_panic_events(startupreason, watchdog, test);
    crashlog_check_kdump(startupreason, test);
    crashlog_check_modem_shutdown();
    crashlog_check_mpanic_abort();
    crashlog_check_startupreason(startupreason, watchdog);
    crashlog_check_recovery();

    key = raise_event_bootuptime(SYS_REBOOT, startupreason, NULL, NULL);
    datelong = get_current_time_long(0);
    LOGE("%-8s%-22s%-20s%s\n", SYS_REBOOT, key, datelong, startupreason);
    free(key);

    if (!strncmp(flashtype, UNALIGNED_BLK_FS, sizeof(UNALIGNED_BLK_FS))) {
        key = raise_event(INFOEVENT, flashtype, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", INFOEVENT, key, datelong, flashtype);
        free(key);
    }

    crashlog_check_fw_update_status();

    key = raise_event_nouptime(STATEEVENT, encryptstate, NULL, NULL);
    LOGE("%-8s%-22s%-20s%s\n", STATEEVENT, key, datelong, encryptstate);
    free(key);

    if(cfg_check_modem_version()) {
        modem_name_check_result = update_modem_name();
        if(modem_name_check_result < 0) {
            LOGI("%s: An error occurred when read/writing %s.", __FUNCTION__, LOG_MODEM_VERSION);
        } else if (modem_name_check_result == 0) {
            LOGI("%s: The file %s was up-to-date.", __FUNCTION__, LOG_MODEM_VERSION);
        } else {
            LOGI("%s: File %s updated successfully.", __FUNCTION__, LOG_MODEM_VERSION);
        }
    }
    /* Update the iptrak file */
    check_iptrak_file(RETRY_ONCE);
}

void spid_read_concat(const char *path, char *complete_value)
{
    FILE *fd;
    char temp_spid[5]="XXXX";

    fd = fopen(path, "r");
    if (fd != NULL && fscanf(fd, "%s", temp_spid) == 1)
        fclose(fd);
    else
        LOGE("%s: Cannot read %s - %s\n", __FUNCTION__, path, strerror(errno));

    strncat(complete_value,"-",1);
    strncat(complete_value,temp_spid, sizeof(temp_spid));
}
/**
 * Read SPID data from file system, build it and write it into given file
 */
void read_sys_spid(char *filename)
{
    FILE *fd;
    char complete_spid[256];
    char temp_spid[5]="XXXX";

    if (filename == 0)
        return;

    fd = fopen(SYS_SPID_1, "r");
    if (fd != NULL && fscanf(fd, "%s", temp_spid) == 1)
        fclose(fd);
    else
        LOGE("%s: Cannot read SPID from %s - %s\n", __FUNCTION__, SYS_SPID_1, strerror(errno));

    snprintf(complete_spid, sizeof(complete_spid), "%s", temp_spid);

    spid_read_concat(SYS_SPID_2,complete_spid);
    spid_read_concat(SYS_SPID_3,complete_spid);
    spid_read_concat(SYS_SPID_4,complete_spid);
    spid_read_concat(SYS_SPID_5,complete_spid);
    spid_read_concat(SYS_SPID_6,complete_spid);

    fd = fopen(filename, "w");
    if (!fd) {
        LOGE("%s: Cannot write SPID to %s - %s\n", __FUNCTION__, filename, strerror(errno));
    } else {
        fprintf(fd, "%s", complete_spid);
        fclose(fd);
    }
    do_chown(filename, PERM_USER, PERM_GROUP);
}

/**
 * Writes given UUID value into specified file
 */
void write_uuid(char* filename, char *uuid_value)
{
    FILE *fd;
    fd = fopen(filename, "w");
    if (!fd) {
        LOGE("%s: Cannot write uuid to %s - %s\n",
            __FUNCTION__, filename, strerror(errno));
        return;
    } else {
        fprintf(fd, "%s", uuid_value);
        fclose(fd);
        do_chown(filename, PERM_USER, PERM_GROUP);
    }
}

static void get_crash_env(char * boot_mode, char *crypt_state, char *encrypt_progress, char *decrypt, char *token) {

    char value[PROPERTY_VALUE_MAX];
    FILE *fd;

    if( property_get("crashlogd.debug.proc_path", value, NULL) > 0 )
    {
        snprintf(CURRENT_PROC_FABRIC_ERROR_NAME, sizeof(CURRENT_PROC_FABRIC_ERROR_NAME), "%s/%s", value, FABRIC_ERROR_NAME);
        snprintf(CURRENT_PROC_OFFLINE_SCU_LOG_NAME, sizeof(CURRENT_PROC_OFFLINE_SCU_LOG_NAME), "%s/%s", value, OFFLINE_SCU_LOG_NAME);
        snprintf(CURRENT_PANIC_CONSOLE_NAME, sizeof(CURRENT_PANIC_CONSOLE_NAME), "%s/%s", value, CONSOLE_NAME);
        snprintf(CURRENT_PANIC_HEADER_NAME, sizeof(CURRENT_PANIC_HEADER_NAME), "%s/%s", value, EMMC_HEADER_NAME);
        snprintf(CURRENT_KERNEL_CMDLINE, sizeof(CURRENT_KERNEL_CMDLINE), "%s/%s", value, CMDLINE_NAME);
        LOGI("Test Mode : ipanic, fabricerr and wdt trigger path is %s\n", value);
    }

    /*
     * Open SYS_PROP and get gbuildversion and gboardversion
     * if not got from properties
     */
    get_build_board_versions(SYS_PROP, gbuildversion, gboardversion);

     /* Set SDcard paths*/
    get_sdcard_paths(MODE_CRASH);

    property_get("ro.boot.mode", boot_mode, "");
    property_get("ro.crypto.state", crypt_state, "unencrypted");
    property_get("vold.encrypt_progress",encrypt_progress,"");
    property_get("vold.decrypt", decrypt, "");
    property_get("crashlogd.token", token, "");

   /* Read UUID */
    if (g_current_serial_device_id == 1) {
        /* Get serial ID from properties and updates UUID file */
        property_get("ro.serialno", guuid, "empty_serial");
        write_uuid(LOG_UUID, guuid);
    } else {
        /* Read UUID value from /proc (emmc) and set UUID file with retrieved value.
         * If reading fails set "Medfield" as default value */
        fd = fopen(PROC_UUID, "r");
        if (fd != NULL && fscanf(fd, "%s", guuid) == 1) {
            fclose(fd);
            write_uuid(LOG_UUID, guuid);
        } else {
            LOGE("%s: Cannot read uuid from %s - %s\n",
                __FUNCTION__, PROC_UUID, strerror(errno));
            write_uuid(LOG_UUID, "Medfield");
        }
    }
    /* Read SPID */
    read_sys_spid(LOG_SPID);

    /* Update rights of folder containing logs */
    update_logs_permission();
}

/**
 * @brief File monitor module file descriptor getter
 *
 * Export FD which expose new events from File Monitor module events
 * source.
 *
 * @return Initialized File Monitor module file descriptor.
 */
int get_inotify_fd() {
    static int file_monitor_fd = -1;

    if (file_monitor_fd < 0) {
        file_monitor_fd = init_inotify_handler();
    }
    return file_monitor_fd;
}

int do_monitor() {
    fd_set read_fds; /**< file descriptor set watching data availability from sources */
    int max = 0; /**< select max fd value +1 {@see man select(2) nfds} */
    int select_result; /**< select result */
    int file_monitor_fd = get_inotify_fd();
    dropbox_set_file_monitor_fd(file_monitor_fd);

    if ( file_monitor_fd < 0 ) {
        LOGE("%s: failed to initialize the inotify handler - %s\n",
            __FUNCTION__, strerror(-file_monitor_fd));
        return -1;
    } else if( get_missing_watched_dir_nb() ) {
        /* One or several directories couldn't have been added to inotify watcher */
        handle_missing_watched_dir(file_monitor_fd);
    }

    /* Set the inotify event callbacks */
    set_watch_entry_callback(SYSSERVER_TYPE,    process_anruiwdt_event);
    set_watch_entry_callback(ANR_TYPE,          process_anruiwdt_event);
    set_watch_entry_callback(TOMBSTONE_TYPE,    process_usercrash_event);
    set_watch_entry_callback(JAVATOMBSTONE_TYPE,    process_usercrash_event);
    set_watch_entry_callback(JAVACRASH_TYPE,    process_usercrash_event);
    set_watch_entry_callback(JAVACRASH_TYPE2,   process_usercrash_event);
#ifdef FULL_REPORT
    set_watch_entry_callback(HPROF_TYPE,        process_hprof_event);
    set_watch_entry_callback(STATTRIG_TYPE,     process_stat_event);
    set_watch_entry_callback(INFOTRIG_TYPE,     process_info_and_error_inotify_callback);
    set_watch_entry_callback(ERRORTRIG_TYPE,    process_info_and_error_inotify_callback);
    set_watch_entry_callback(CMDTRIG_TYPE,      process_command_event);
    set_watch_entry_callback(APCORE_TYPE,       process_apcore_event);
#endif
    set_watch_entry_callback(APLOGTRIG_TYPE,    process_aplog_event);
    set_watch_entry_callback(LOST_TYPE,         process_lost_event);
    set_watch_entry_callback(UPTIME_TYPE,       process_uptime_event);
    set_watch_entry_callback(MDMCRASH_TYPE,     process_modem_event);
    set_watch_entry_callback(APIMR_TYPE,        process_modem_event);
    set_watch_entry_callback(MRST_TYPE,         process_modem_event);

    init_mmgr_cli_source();

    kct_netlink_init_comm();

    for(;;) {
        // Clear fd set
        FD_ZERO(&read_fds);

        // File monitor fd setup
        if ( file_monitor_fd > 0 ) {
            FD_SET(file_monitor_fd, &read_fds);
            if (file_monitor_fd > max)
                max = file_monitor_fd;
        }

        //mmgr fd setup
        if (mmgr_get_fd() > 0) {
            FD_SET(mmgr_get_fd(), &read_fds);
            if (mmgr_get_fd() > max)
                max = mmgr_get_fd();
        }

        //kct fd setup
        if (kct_netlink_get_fd() > 0) {
            FD_SET(kct_netlink_get_fd(), &read_fds);
            if (kct_netlink_get_fd() > max)
                max = kct_netlink_get_fd();
        }

        // Wait for events
        select_result = select(max+1, &read_fds, NULL, NULL, NULL);

        if (select_result == -1 && errno == EINTR) // Interrupted, need to recycle
            continue;

        // Result processing
        if (select_result > 0) {
            /* clean children to avoid zombie processes */
            while(waitpid(-1, NULL, WNOHANG) > 0){};
            // File monitor
            if (FD_ISSET(file_monitor_fd, &read_fds)) {
                receive_inotify_events(file_monitor_fd);
            }
            // mmgr monitor
            if (FD_ISSET(mmgr_get_fd(), &read_fds)) {
                LOGD("mmgr fd set");
                mmgr_handle();
            }
            // kct monitor
            if (FD_ISSET(kct_netlink_get_fd(), &read_fds)) {
                LOGD("kct fd set");
                kct_netlink_handle_msg();
            }
        }
    }

    close_mmgr_cli_source();
    free_config(g_first_modem_config);
    LOGE("Exiting main monitor loop\n");
    return -1;
}

/**
 * @brief Computes the crashlogd mode depending on boot mode and
 * on ramdump input arguments
 *
 * @return 0 on success. -1 otherwise.
 */
int compute_crashlogd_mode(char *boot_mode, int ramdump_flag ) {

    if (!strcmp(boot_mode, "main")) {
        g_crashlog_mode = NOMINAL_MODE;
    } else if (!strcmp(boot_mode, "ramconsole")) {
        if (ramdump_flag)
            g_crashlog_mode = RAMDUMP_MODE;
        else {
            LOGI("Started as NOMINAL_MODE in ramconsole OS, quitting\n");
            return -1;
        }
    } else {
        g_crashlog_mode = MINIMAL_MODE;
    }

    LOGI("Current crashlogd mode is %s\n", CRASHLOG_MODE_NAME(g_crashlog_mode));
    return 0;
}

/**
 * "androidboot.crashlogd=wait" in cmdline will make crashlogd waiting
 * Set [crashlogd.debug.wait] property to [0] to continue
 */
void crashlogd_wait_for_user() {

#define PROP_RO_BOOT_CRASHLOGD "ro.boot.crashlogd"
#define PROP_CRASHLOGD_DEBUG_WAIT "crashlogd.debug.wait"

    char property_value[PROPERTY_VALUE_MAX];
    property_get(PROP_RO_BOOT_CRASHLOGD, property_value, "");
    if (strcmp(property_value, "wait"))
        return;

    LOGI("[%s]: [%s] waiting ...\n", PROP_RO_BOOT_CRASHLOGD, property_value);
    property_set(PROP_CRASHLOGD_DEBUG_WAIT, "1");
    do {
        LOGI("Set [%s] property to [0] to continue. Meanwhile sleeping 2s ...\n",
             PROP_CRASHLOGD_DEBUG_WAIT);
        sleep(2);
        property_get(PROP_CRASHLOGD_DEBUG_WAIT, property_value, "");
    } while (strcmp(property_value, "0"));

}

int main(int argc, char **argv) {

    int ret = 0, alreadyran = 0, test_flag = 0, ramdump_flag = 0;
    unsigned int i;
    pthread_t thread;
    char boot_mode[PROPERTY_VALUE_MAX];
    char crypt_state[PROPERTY_VALUE_MAX];
    char encrypt_progress[PROPERTY_VALUE_MAX];
    char decrypt[PROPERTY_VALUE_MAX];
    char token[PROPERTY_VALUE_MAX];
    char encryptstate[16] = { '\0', };

    crashlogd_wait_for_user();

    /* Check the args */
    if (argc > 2) {
        LOGE("USAGE: %s [-test|-ramdump|<max_nb_files>] \n", argv[0]);
        return -1;
    }

    if (argc == 2) {
        if(!strcmp(argv[1], "-test"))
            test_flag = 1;
        else if ( !strcmp(argv[1], "-ramdump") )
            ramdump_flag = 1;
        else {
            errno = 0;
            gmaxfiles = strtol(argv[1], NULL, 0);

            if (errno) {
                LOGE("%s - max_nb_files number must be a number (used %s)\n",
                     __FUNCTION__, argv[1]);
                return -1;
            }
            if (gmaxfiles > MAX_DIRS || gmaxfiles < 0) {
                LOGI("%s - max_nb_files shall be a positive number lesser than %d,"
                     " change it to this value.\n", __FUNCTION__, MAX_DIRS);
            }
        }
    }

    /* first thing to do : load configuration */
    load_config();

    /* Get the properties and read the local files to set properly the env variables */
    get_crash_env(boot_mode, crypt_state, encrypt_progress, decrypt, token);

    alreadyran = (token[0] != 0);

    if (compute_crashlogd_mode(boot_mode, ramdump_flag) < 0)
        return -1;

    switch (g_crashlog_mode) {

    case RAMDUMP_MODE :
        return do_ramdump_checks(test_flag);

    case MINIMAL_MODE :
        if (!alreadyran) {
            for (i=0; i<strlen(boot_mode); i++)
                boot_mode[i] = toupper(boot_mode[i]);
            early_check_nomain(boot_mode, test_flag);
        }
        check_crashlog_died();
        return do_monitor();

    case NOMINAL_MODE :
        /* DECRYPTED by default */
        strcpy(encryptstate,"DECRYPTED");

        if ( encrypt_progress[0]) {
            /* Encrypting unencrypted device... */
            LOGI("phone enter state: encrypting.\n");
        } else if (!strcmp(crypt_state, "unencrypted") && !alreadyran) {
            /* Unencrypted device */
            LOGI("phone enter state: normal start.\n");
            early_check(encryptstate, test_flag);
        } else if (!strcmp(crypt_state, "encrypted") &&
                   !strcmp(decrypt, "trigger_restart_framework") && !alreadyran) {
            /* Encrypted device */
            LOGI("phone enter state: phone encrypted.\n");
            strcpy(encryptstate,"ENCRYPTED");
            early_check(encryptstate, test_flag);
        }

        /* Starts the thread in charge of uptime check */
        ret = pthread_create(&thread, NULL, (void *)timeup_thread_mainloop, NULL);
        if (ret < 0) {
            LOGE("pthread_create error");
            return -1;
        }

#ifdef FULL_REPORT
        monitor_crashenv();
#endif
        check_crashlog_died();
        return do_monitor();

    default :
        /* Robustness : this case can't happen */
        return -1;
    }
}
