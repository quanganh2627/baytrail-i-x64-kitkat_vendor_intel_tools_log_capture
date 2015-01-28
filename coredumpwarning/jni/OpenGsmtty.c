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
#include <cutils/log.h>

#include "OpenGsmtty.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define LOG_TAG "TelephonyEventsNotifier"
#include <stdio.h>
#define LOG(_p, ...) \
      fprintf(stderr, _p "/" LOG_TAG ": " __VA_ARGS__)
#define LOGV(...)   LOG("V", __VA_ARGS__)
#define LOGD(...)   LOG("D", __VA_ARGS__)
#define LOGI(...)   LOG("I", __VA_ARGS__)
#define LOGW(...)   LOG("W", __VA_ARGS__)
#define LOGE(...)   LOG("E", __VA_ARGS__)

#define TTY_CLOSED -1

JNIEXPORT jint JNICALL Java_com_intel_internal_telephony_TelephonyEventsNotifier_Gsmtty_OpenSerial(JNIEnv *env, jobject obj,
        jstring jtty_name, jint baudrate)
{
    int fd = TTY_CLOSED;
    const char *tty_name = (*env)->GetStringUTFChars(env, jtty_name, 0);

    struct termios tio;
    LOGI("OpenSerial: opening %s", tty_name);

    fd = open(tty_name, O_RDWR | CLOCAL | O_NOCTTY);
    if (fd < 0) {
        LOGE("OpenSerial: %s (%d)", strerror(errno), errno);
        goto open_serial_failure;
    }

    struct termios terminalParameters;
    if (tcgetattr(fd, &terminalParameters)) {
        LOGE("OpenSerial: %s (%d)", strerror(errno), errno);
        goto open_serial_failure;
    }

    cfmakeraw(&terminalParameters);
    if (tcsetattr(fd, TCSANOW, &terminalParameters)) {
        LOGE("OpenSerial: %s (%d)", strerror(errno), errno);
        goto open_serial_failure;
    }

    if (fcntl(fd, F_SETFL, O_NONBLOCK) < 0 ) {
        LOGE("OpenSerial: %s (%d)", strerror(errno), errno);
        goto open_serial_failure;
    }

    memset(&tio, 0, sizeof(tio));
    tio.c_cflag = B115200;
    tio.c_cflag |= CS8 | CLOCAL | CREAD;
    tio.c_iflag &= ~(INPCK | IGNPAR | PARMRK | ISTRIP | IXANY | ICRNL);
    tio.c_oflag &= ~OPOST;
    tio.c_cc[VMIN] = 1;
    tio.c_cc[VTIME] = 10;

    tcflush(fd, TCIFLUSH);
    cfsetispeed(&tio, baudrate);
    tcsetattr(fd, TCSANOW, &tio);

    goto open_serial_success;

open_serial_failure:
    if (fd >= 0) {
        close(fd);
        fd = TTY_CLOSED;
    }

open_serial_success:
    if (fd != TTY_CLOSED)
       LOGI("OpenSerial: %s opened (%d)", tty_name, fd);
    (*env)->ReleaseStringUTFChars(env, jtty_name, tty_name);
    return fd;
}

JNIEXPORT jint JNICALL Java_com_intel_internal_telephony_TelephonyEventsNotifier_Gsmtty_CloseSerial(JNIEnv *env,
        jobject obj, jint fd)
{
    LOGI("CloseSerial: closing file descriptor (%d)", fd);
    if (fd >= 0) {
        close(fd);
        fd = TTY_CLOSED;
        LOGD("CloseSerial: closed");
    }
    else {
        LOGD("CloseSerial: already closed");
    }
    return 0;
}
