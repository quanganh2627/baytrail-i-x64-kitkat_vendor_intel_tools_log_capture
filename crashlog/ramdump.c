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
 * @file ramdump.c
 * @brief File containing functions for ram dump mode processing.
 */

#include "crashutils.h"
#include "fsutils.h"
#include "privconfig.h"
#include "startupreason.h"
#include "history.h"
#include "panic.h"

#include <stdlib.h>

/* buffer content exposed by LM_DUMP module */
#define LM_DUMP_FILE          DEBUGFS_DIR "/lm_dump/lkm_buf1"
#define LBR_DUMP_FILE         DEBUGFS_DIR "/lbr_dump/lbr_dump"

#define SAVED_LM_BUFFER_NAME  "lkm_buffer_dump"
#define SAVED_LBR_BUFFER_NAME "lbr_buffer_dump"

/**
* @brief performs a copy of the virtual file exposed by the LM_DUMP kernel
* module and raises a 'FWDUMP' event.
*
* @retval returns -1 if a problem occurs (no LM_DUMP file..). 0 otherwise.
*/
int crashlog_check_fwdump()
{
    char destination[PATHMAX] = {'\0'};
    char *crashtype = FWDUMP_EVENT;
    int dir;
    const char *dateshort = get_current_time_short(1);
    char *key;

    dir = find_new_crashlog_dir(CRASH_MODE);
    if (dir < 0) {
        LOGE("%s: Cannot get a valid new crash directory...\n", __FUNCTION__);
        key = raise_event(CRASHEVENT, crashtype, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), crashtype);
        free(key);
        return -1;
    }
    /* Copy */
    if( !file_exists(LM_DUMP_FILE) )
        LOGE("%s: can't find file %s - error is %s.\n",
             __FUNCTION__, LM_DUMP_FILE, strerror(errno) );
    else {
        snprintf(destination, sizeof(destination), "%s%d/%s_%s.txt",
                 CRASH_DIR, dir, SAVED_LM_BUFFER_NAME, dateshort);
        do_copy_eof(LM_DUMP_FILE, destination);
    }

    if ( !file_exists(LBR_DUMP_FILE) )
        LOGE("%s: can't find file %s - error is %s.\n",
             __FUNCTION__, LBR_DUMP_FILE, strerror(errno) );
    else {
       destination[0] = '\0';
       snprintf(destination, sizeof(destination), "%s%d/%s_%s.txt",
                CRASH_DIR, dir, SAVED_LBR_BUFFER_NAME, dateshort);
       do_copy_eof(LBR_DUMP_FILE, destination);
    }

    do_last_kmsg_copy(dir);

    destination[0] = '\0';
    snprintf(destination, sizeof(destination), "%s%d/", CRASH_DIR, dir);
    key = raise_event(CRASHEVENT, crashtype, NULL, destination);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), crashtype, destination);
    free(key);

    return 0;
}

/**
* @brief trigger a global reset of the plateform by writing in specific registers.
*
* @retval return 0.
*/
int request_global_reset() {

#define CMD_SET_RESET_TO_GLOBAL "peeknpoke w fed0304a 8 33" // ETR register, bit cf9gr
#define CMD_REBOOT "reboot"

    /**
     * "androidboot.crashlogd=noreboot" in cmdline will prevent reboot
     */
    char ro_boot_crashlogd[PROPERTY_VALUE_MAX];
    property_get("ro.boot.crashlogd", ro_boot_crashlogd, "");
    if (!strcmp(ro_boot_crashlogd, "noreboot")) {
        LOGI("ro.boot.crashlogd=noreboot global reset aborted\n");
        return -1;
    }

    LOGI("Requesting a platform global reset...\n");

    system(CMD_SET_RESET_TO_GLOBAL);
    system(CMD_REBOOT);

    return 0;
}

/**
* @brief performs all checks required in RAMDUMP mode.
* In this mode, numerous checks are bypassed.
*
* @param[in] test : test mode flag
*
* @retval returns -1 if a problem occurs (no LM_DUMP file..). 0 otherwise.
*/
int do_ramdump_checks(int test) {

    char startupreason[32] = { '\0', };
    char watchdog[16] = { '\0', };
    char lastuptime[32] = { '\0', };
    char *key;

    strcpy(watchdog,"WDT");

    read_startupreason(startupreason);
    uptime_history(lastuptime);
    update_logs_permission();

    /* Checks for panic */
    crashlog_check_panic_events(startupreason, watchdog, test);
    crashlog_check_startupreason(startupreason, watchdog);
    /* Dump Lakemore file and raises FWDUMP event */
    crashlog_check_fwdump();

    /* Raise REBOOT event*/
    key = raise_event(SYS_REBOOT, startupreason, NULL, NULL);
    LOGE("%-8s%-22s%-20s%s\n", SYS_REBOOT, key, get_current_time_long(0), startupreason);
    free(key);

    request_global_reset();

    return 0;
}
