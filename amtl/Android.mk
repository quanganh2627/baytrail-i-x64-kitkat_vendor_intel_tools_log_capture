LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

ifneq ($(findstring sofia3g, $(TARGET_BOARD_PLATFORM)),)
    LOCAL_JAVA_LIBRARIES := com.msm.android.MSMClientLib
else
    LOCAL_STATIC_JAVA_LIBRARIES := com.intel.internal.telephony.MmgrClient
endif

LOCAL_SRC_FILES := $(call all-java-files-under, src)
# Only compile source java files in this apk.

ifneq ($(findstring sofia3g, $(TARGET_BOARD_PLATFORM)),)
    LOCAL_SRC_FILES += $(call all-java-files-under, src_msm)
else
    LOCAL_SRC_FILES += $(call all-java-files-under, src_mmgr)
endif

LOCAL_PACKAGE_NAME := Amtl

LOCAL_CERTIFICATE := platform

LOCAL_JNI_SHARED_LIBRARIES := libamtl_jni

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_REQUIRED_MODULES := amtl_cfg

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call first-makefiles-under,$(LOCAL_PATH))
