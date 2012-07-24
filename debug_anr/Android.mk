#
# Copyright (C) Intel 2010
#
#	Su Xuemin <xuemin.su@intel.com>
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
	com_android_server_am_DebugAnr.cpp

LOCAL_MODULE_TAGS := eng debug
LOCAL_MODULE:= libdebug_anr

LOCAL_UNSTRIPPED_PATH := $(TARGET_ROOT_OUT_UNSTRIPPED)

#LOCAL_STATIC_LIBRARIES:= libc libcutils libparse_stack
LOCAL_SHARED_LIBRARIES:= libnativehelper libc libcutils libparse_stack

include $(BUILD_SHARED_LIBRARY)
