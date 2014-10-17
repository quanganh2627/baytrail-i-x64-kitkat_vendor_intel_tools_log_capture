LOCAL_PATH := $(call my-dir)

prebuilt_executables := vmm_tool

define copy-prebuilt-executables
        include $(CLEAR_VARS)
        LOCAL_MODULE := $(1)
        LOCAL_SRC_FILES := $(1)
        LOCAL_MODULE_CLASS := EXECUTABLES
        LOCAL_MODULE_TAGS := debug
        LOCAL_MODULE_PATH := $(TARGET_OUT_EXECUTABLES)
        include $(BUILD_PREBUILT)
endef

$(foreach f, $(prebuilt_executables),$(eval $(call copy-prebuilt-executables,$f)))
include $(call all-subdir-makefiles)

