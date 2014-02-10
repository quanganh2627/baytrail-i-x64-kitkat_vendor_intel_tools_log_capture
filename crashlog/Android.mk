#
# Copyright (C) Intel 2010
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= main.c \
    config.c \
    inotify_handler.c \
    startupreason.c \
    crashutils.c \
    usercrash.c \
    anruiwdt.c \
    recovery.c \
    history.c \
    trigger.c \
    dropbox.c \
    fsutils.c \
    fabric.c \
    modem.c \
    panic.c \
    config_handler.c \
    ramdump.c

ifneq ($(CRASH_REPORT_PARTIAL_REPORT),true)
LOCAL_CFLAGS += -DFULL_REPORT=1
endif
LOCAL_C_INCLUDES += \
  vendor/intel/tools/log_capture/backtrace

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE:= crashlogd

LOCAL_SHARED_LIBRARIES:= libparse_stack libc libcutils

# Modem crash report
# Outside should use $(CRASH_REPORT_MODEM_SUPPORT)
# to enable it.
ifeq ($(CRASH_REPORT_MODEM_SUPPORT),true)
LOCAL_C_INCLUDES += \
  $(TARGET_OUT_HEADERS)/IFX-modem \

LOCAL_SRC_FILES += \
  mmgr_source.c \

LOCAL_SHARED_LIBRARIES += \
  libmmgrcli \

LOCAL_CFLAGS += \
  -DMODEM_CRASH_SUPPORT \

endif

include $(BUILD_EXECUTABLE)
