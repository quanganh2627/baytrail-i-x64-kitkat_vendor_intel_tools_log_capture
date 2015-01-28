LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := TelephonyEventsNotifier
LOCAL_CERTIFICATE := platform
LOCAL_MODULE_TAGS := optional
LOCAL_JAVACFLAGS += -Xlint
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_JNI_SHARED_LIBRARIES := libten_jni
include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))

