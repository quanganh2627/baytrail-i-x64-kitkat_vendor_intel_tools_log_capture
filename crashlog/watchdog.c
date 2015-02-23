/* Copyright (C) Intel 2015
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

#include "log.h"

#include <signal.h>
#include <time.h>
#include <errno.h>

#define CRASHLOG_WD_SIGNAL   SIGRTMIN

static timer_t *crashlog_wd_timer = NULL;
static unsigned int crashlog_wd_timeout = 0;

static int update_crashlog_watchdog_timeout(int timeout) {
    struct itimerspec new_value, old_value;
    new_value.it_interval.tv_sec = new_value.it_value.tv_sec = timeout;
    new_value.it_interval.tv_nsec = new_value.it_value.tv_nsec = 0;

    if (!crashlog_wd_timer) {
        return 0;
    }

    if (timer_settime(*crashlog_wd_timer, 0, &new_value, &old_value) == 0) {
        return 1;
    }
    return 0;
}

static void stop_crashlog_watchdog() {
    update_crashlog_watchdog_timeout(0);
}

static void crashlog_wd_handler(int signal, siginfo_t *info __unused, void *context __unused) {
    if (signal == CRASHLOG_WD_SIGNAL) {

        if (!crashlog_wd_timer)
            return;

        stop_crashlog_watchdog();

         // for some reason, comit suicide
        LOGE("%s - Crashlog watchdog timeout. Killing self ...\n", __FUNCTION__);
        raise(SIGKILL);
    }
}

int enable_watchdog(unsigned int timeout) {
    struct sigaction sigact;
    struct sigevent sevp;
    struct itimerspec new_value, old_value;

    if (timeout == 0)
        return -EINVAL;

    if (crashlog_wd_timer) {
        LOGI("%s - Crashlog watchdog is already enabled\n", __FUNCTION__);
        return -EPERM;
    }

    crashlog_wd_timeout = timeout;

    crashlog_wd_timer = (timer_t *)malloc(sizeof(timer_t));
    if (!crashlog_wd_timer) {
        LOGE("%s - Could not allocate memory for wd timer\n", __FUNCTION__);
        return -ENOMEM;
    }

    // update signal handler
    sigemptyset(&sigact.sa_mask);
    sigact.sa_flags = SA_SIGINFO;
    sigact.sa_sigaction = crashlog_wd_handler;

    if (sigaction(CRASHLOG_WD_SIGNAL, &sigact, NULL) == -1) {
        LOGE("%s - Error while updating signal handler\n", __FUNCTION__);
        free(crashlog_wd_timer);
        crashlog_wd_timer = NULL;
        return -1;
    }

    // set up timer
    sevp.sigev_notify = SIGEV_SIGNAL;
    sevp.sigev_signo = CRASHLOG_WD_SIGNAL;
    sevp.sigev_value.sival_ptr = crashlog_wd_timer;

    if (timer_create(CLOCK_REALTIME, &sevp, crashlog_wd_timer) == 0) {
        if (update_crashlog_watchdog_timeout(crashlog_wd_timeout)) {
            LOGI("%s - Crashlog watchdog enabled!\n", __FUNCTION__);
            return 0;
        }
    }

    LOGE("%s - Could not set up a watchdog timer\n", __FUNCTION__);
    free(crashlog_wd_timer);
    crashlog_wd_timer = NULL;
    return -1;
}

void kick_watchdog() {
    if (crashlog_wd_timer)
        update_crashlog_watchdog_timeout(crashlog_wd_timeout);
    // else, the watchdog not started
}
