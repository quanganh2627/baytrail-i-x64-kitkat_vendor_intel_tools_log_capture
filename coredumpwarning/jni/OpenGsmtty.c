/* Android Modem Traces and Logs
 *
 * Copyright (C) Intel 2012
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <termios.h>
#include <errno.h>
#include <strings.h>
#include <utils/Log.h>

#include "OpenGsmtty.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define LOG_TAG "TelephonyEventsNotifier"
#include <stdio.h>

#define TTY_CLOSED -1

JNIEXPORT jint JNICALL Java_com_intel_internal_telephony_TelephonyEventsNotifier_Gsmtty_OpenSerial(JNIEnv *env, jobject obj,
        jstring jtty_name, jint baudrate)
{
    int fd = TTY_CLOSED;
    const char *tty_name = (*env)->GetStringUTFChars(env, jtty_name, 0);

    struct termios tio;
    ALOGD("OpenSerial: opening %s", "atc");

    fd = open("/dev/mvpipe-atc" , O_RDWR);
    if (fd < 0) {
       ALOGD("OpenSerial: %s (%d)", strerror(errno), errno);
        goto open_serial_failure;
    }

    goto open_serial_success;

open_serial_failure:
    if (fd >= 0) {
        close(fd);
        fd = TTY_CLOSED;
    }

open_serial_success:
    if (fd != TTY_CLOSED)
       ALOGD("OpenSerial: %s opened (%d)", "atc", fd);
    (*env)->ReleaseStringUTFChars(env, jtty_name, tty_name);
    return fd;
}

JNIEXPORT jint JNICALL Java_com_intel_internal_telephony_TelephonyEventsNotifier_Gsmtty_CloseSerial(JNIEnv *env,
        jobject obj, jint fd)
{
    ALOGD("CloseSerial: closing file descriptor (%d)", fd);
    if (fd >= 0) {
        close(fd);
        fd = TTY_CLOSED;
        ALOGD("CloseSerial: closed");
    }
    else {
        ALOGD("CloseSerial: already closed");
    }
    return 0;
}
