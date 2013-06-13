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
        "HWWDT_RESET",
        "WATCHDOG_COUNTER_EXCEEDED",
        "POWER_SUPPLY_DETECTED",
        "FASTBOOT_BUTTONS_COMBO",
        "NO_MATCHING_OSIP_ENTRY",
        "CRITICAL_BATTERY",
        "INVALID_CHECKSUM"};

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

int crashlog_check_startupreason(char *reason) {
    const char *dateshort = get_current_time_short(1);
    char destination[PATHMAX];
    int dir;
    char *key;

    if ( !strstr(reason, "WDT_RESET") || strstr(reason, "FAKE") ) {
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
