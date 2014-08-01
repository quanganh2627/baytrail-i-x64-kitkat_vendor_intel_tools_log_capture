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

CRASHLOGD_MODULE_BACKTRACE := false
CRASHLOGD_MODULE_KCT := false
CRASHLOGD_MODULE_MODEM := false

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
    iptrak.c \
    uefivar.c \
    ct_eventintegrity.c \
    ingredients.c

LOCAL_SHARED_LIBRARIES := libcutils

LOCAL_CFLAGS := -DFULL_REPORT

ifeq ($(TARGET_BIOS_TYPE),"uefi")
LOCAL_CFLAGS += -DCONFIG_UEFI
LOCAL_STATIC_LIBRARIES += libdmi
endif

ifeq ($(CRASHLOGD_MODULE_BACKTRACE),true)
LOCAL_CFLAGS += -DCRASHLOGD_MODULE_BACKTRACE
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../backtrace
LOCAL_SHARED_LIBRARIES += libparse_stack
endif

ifeq ($(CRASHLOGD_MODULE_KCT),true)
LOCAL_CFLAGS += -DCRASHLOGD_MODULE_KCT
LOCAL_SRC_FILES += \
    ct_utils.c \
    kct_netlink.c \
    lct_link.c \
    ct_eventintegrity.c
endif

ifeq ($(CRASHLOGD_MODULE_MODEM),true)
ifeq ($(BOARD_HAVE_MODEM),true)

LOCAL_CFLAGS += -DBOARD_HAVE_MODEM

LOCAL_C_INCLUDES += \
    $(TARGET_OUT_HEADERS)/IFX-modem \
    $(TARGET_OUT_HEADERS)/libtcs

LOCAL_SHARED_LIBRARIES += \
    libmmgrcli \
    libtcs

LOCAL_SRC_FILES += \
    tcs_wrapper.c \
    mmgr_source.c

endif
endif

include $(BUILD_EXECUTABLE)
