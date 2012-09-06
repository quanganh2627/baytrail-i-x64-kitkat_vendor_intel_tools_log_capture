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

LOCAL_SRC_FILES:= crashlogd.c

LOCAL_C_INCLUDES += \
  device/intel/log_capture/backtrace

LOCAL_MODULE_TAGS := eng debug
LOCAL_MODULE:= crashlogd

LOCAL_UNSTRIPPED_PATH := $(TARGET_ROOT_OUT_UNSTRIPPED)

#LOCAL_STATIC_LIBRARIES:= libc libcutils

LOCAL_SHARED_LIBRARIES:= libparse_stack libc libcutils
include $(BUILD_EXECUTABLE)

$(info WARNING: PRODUCT_COPY_FILES disabled in $(LOCAL_PATH))
#PRODUCT_COPY_FILES += \
#        $(LOCAL_PATH)/analyze_crash:system/bin/analyze_crash \
#        $(LOCAL_PATH)/del_hist.sh:system/bin/del_hist.sh \
#        $(LOCAL_PATH)/del_log.sh:system/bin/del_log.sh