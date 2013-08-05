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
 * @file startupreason.c
 * @brief File containing functions to read startup reason and to detect and
 * process wdt event.
 */

#include "crashutils.h"
#include "fsutils.h"
#include "privconfig.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <fcntl.h>
#include <stdio.h>

static void flush_aplog_atboot(char *mode, int dir, const char* ts)
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
*   char *startupreason   -> string containing the translated startup reason
*   */
void read_startupreason(char *startupreason)
{
    char cmdline[512] = { '\0', };
    char *p, *endptr;
    unsigned long reason;
    FILE *fd;
    int res;
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

    strcpy(startupreason, "UNKNOWN");

    fd = fopen(CURRENT_KERNEL_CMDLINE, "r");
    if ( fd == NULL ) {
        LOGE("%s: Cannot open file %s - %s\n", __FUNCTION__,
            CURRENT_KERNEL_CMDLINE, strerror(errno));
        return;
    }
    res = fread(cmdline, 1, sizeof(cmdline)-1, fd);
    fclose(fd);
    if (res <= 0) {
        LOGE("%s: Cannot read file %s - %s\n", __FUNCTION__,
            CURRENT_KERNEL_CMDLINE, strerror(errno));
        return;
    }
    p = strstr(cmdline, STARTUP_STR);
    if(!p) {
        /* No reason in the command line */
        return;
    }

    if (strlen(p) <= strlen(STARTUP_STR)) {
        /* the pattern is found but is incomplete... */
        LOGE("%s: Incomplete startup reason found in cmdline \"%s\"\n",
            __FUNCTION__, cmdline);
        return;
    }
    p += strlen(STARTUP_STR);
    if (isspace(*p)) {
        /* the pattern is found but starting with a space... */
        LOGE("%s: Incorrect startup reason found in cmdline \"%s\"\n",
            __FUNCTION__, cmdline);
        return;
    }

    /* All is fine, decode the reason */
    errno = 0;
    reason = strtoul(p, &endptr, 16);
    if ((errno != ERANGE) &&
        (endptr != p) &&
        (reason < (sizeof(bootmode_reason)/sizeof(char*)))) {
        strcpy(startupreason, bootmode_reason[reason]);
    } else {
        /* Hmm! bad value... */
        LOGE("%s: Invalid startup reason found in cmdline \"%s\"\n",
            __FUNCTION__, cmdline);
    }
}


/**
 * @brief generate WDT crash event
 *
 * If startup reason contains HWWDT or SWWDT, generate a WDT crash event.
 * Copy aplogs and /proc/last_kmsg files in the event.
 *
 * @param reason - string containing the translated startup reason
 */
int crashlog_check_startupreason(char *reason) {
    const char *dateshort = get_current_time_short(1);
    char destination[PATHMAX];
    int dir;
    char *key;

    /* Nothing to do if the reason :
     *  - doesn't contains "HWWDT" or "SWWDT" or "WDT" */
    /*  - contains "HWWDT" or "SWWDT" or "WDT" but with 'FAKE' suffix */
    if ( !( strstr(reason, "HWWDT_") || strstr(reason, "SWWDT_") || strstr(reason, "WDT_") ) || strstr(reason, "FAKE") ) {
        /* Nothing to do */
        return 0;
    }

    dir = find_new_crashlog_dir(CRASH_MODE);
    if (dir < 0) {
        LOGE("%s: find_new_crashlog_dir failed\n", __FUNCTION__);
        key = raise_event(CRASHEVENT, "WDT", NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", CRASHEVENT, key, get_current_time_long(0), "WDT");
        free(key);
        return -1;
    }

    destination[0] = '\0';
    snprintf(destination, sizeof(destination), "%s%d/", CRASH_DIR, dir);
    key = raise_event(CRASHEVENT, "WDT", reason, destination);
    LOGE("%-8s%-22s%-20s%s %s\n", CRASHEVENT, key, get_current_time_long(0), "WDT", destination);
    flush_aplog_atboot("WDT", dir, dateshort);
    usleep(TIMEOUT_VALUE);
    do_log_copy("WDT", dir, dateshort, APLOG_TYPE);
    do_last_kmsg_copy(dir);
    free(key);
    return 0;
}
