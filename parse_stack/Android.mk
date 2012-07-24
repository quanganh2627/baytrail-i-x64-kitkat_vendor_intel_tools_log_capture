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

LOCAL_SRC_FILES:= \
	parse_stack.c

LOCAL_C_INCLUDES += \
  device/intel/log_capture/backtrace

LOCAL_MODULE_TAGS := eng debug
LOCAL_MODULE:= parse_stack

LOCAL_UNSTRIPPED_PATH := $(TARGET_ROOT_OUT_UNSTRIPPED)

LOCAL_SHARED_LIBRARIES:= libc libcutils libparse_stack

#LOCAL_SHARED_LIBRARIES:= libparse_stack

#include $(BUILD_SHARED_LIBRARY)
include $(BUILD_EXECUTABLE)
