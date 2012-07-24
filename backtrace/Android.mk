LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	backtrace.c \
	symbols.c \
	symbols_64.c \
	generate_tomb_file.c

LOCAL_MODULE_TAGS := eng debug
LOCAL_MODULE:= libparse_stack
#LOCAL_MODULE:= parse_tomb

LOCAL_UNSTRIPPED_PATH := $(TARGET_ROOT_OUT_UNSTRIPPED)

#LOCAL_STATIC_LIBRARIES:= libc libcutils
LOCAL_SHARED_LIBRARIES:= libc libcutils

include $(BUILD_SHARED_LIBRARY)
#include $(BUILD_STATIC_LIBRARY)
#include $(BUILD_EXECUTABLE)
