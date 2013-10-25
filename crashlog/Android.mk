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
    mmgr_source.c \
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
    ramdump.c \
    tcs_wrapper.c \
    kct_netlink.c \
    iptrak.c

LOCAL_CFLAGS += -DFULL_REPORT=1

LOCAL_C_INCLUDES += \
  $(TARGET_OUT_HEADERS)/IFX-modem \
  vendor/intel/tools/log_capture/backtrace

LOCAL_MODULE_TAGS := eng debug
LOCAL_MODULE:= crashlogd

LOCAL_SHARED_LIBRARIES:= libparse_stack libc libcutils libmmgrcli libtcs
include $(BUILD_EXECUTABLE)
