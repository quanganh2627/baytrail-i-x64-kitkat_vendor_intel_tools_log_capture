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

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := crashlogd
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    main.c \
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
    ramdump.c \
    fw_update.c \
    ct_utils.c \
    kct_netlink.c \
    lct_link.c \
    iptrak.c \
    uefivar.c \
    ct_eventintegrity.c \
    ingredients.c

LOCAL_CFLAGS := -DFULL_REPORT=1

ifeq ($(TARGET_BIOS_TYPE),"uefi")
    LOCAL_CFLAGS += -DCONFIG_UEFI
endif

LOCAL_STATIC_LIBRARIES += \
  libdmi \
  libuefivar

LOCAL_C_INCLUDES := \
  $(LOCAL_PATH)/../backtrace \
  $(TARGET_OUT_HEADERS)/libtcs

LOCAL_SHARED_LIBRARIES := \
    libparse_stack \
    libcutils

ifeq ($(BOARD_HAVE_MODEM),true)
LOCAL_CFLAGS += -DBOARD_HAVE_MODEM

LOCAL_C_INCLUDES += \
  $(TARGET_OUT_HEADERS)/IFX-modem

LOCAL_SHARED_LIBRARIES += \
    libmmgrcli \
    libtcs

LOCAL_SRC_FILES += \
    tcs_wrapper.c \
    mmgr_source.c
endif

include $(BUILD_EXECUTABLE)
