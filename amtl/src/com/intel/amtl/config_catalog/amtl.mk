# AMTL oneconfig
LOCAL_PATH:=vendor/intel/tools/log_capture/amtl/src/com/intel/amtl/config_catalog
ifneq (, $(findstring "$(TARGET_BUILD_VARIANT)", "eng" "userdebug"))
PRODUCT_COPY_FILES += \
        $(LOCAL_PATH)/TANGIER_XMM_6360.cfg:system/etc/amtl/catalog/TANGIER_XMM_6360.cfg \
        $(LOCAL_PATH)/TANGIER_XMM_7160_REV3.cfg:system/etc/amtl/catalog/TANGIER_XMM_7160_REV3.cfg \
        $(LOCAL_PATH)/TANGIER_XMM_7160_REV3_5.cfg:system/etc/amtl/catalog/TANGIER_XMM_7160_REV3_5.cfg \
        $(LOCAL_PATH)/TANGIER_XMM_7160_REV4.cfg:system/etc/amtl/catalog/TANGIER_XMM_7160_REV4.cfg \
        $(LOCAL_PATH)/TANGIER_XMM_7260_REV1.cfg:system/etc/amtl/catalog/TANGIER_XMM_7260_REV1.cfg \
        $(LOCAL_PATH)/TANGIER_XMM_7260_REV2.cfg:system/etc/amtl/catalog/TANGIER_XMM_7260_REV2.cfg \
        $(LOCAL_PATH)/CLOVERVIEW_XMM_6268.cfg:system/etc/amtl/catalog/CLOVERVIEW_XMM_6268.cfg \
        $(LOCAL_PATH)/CLOVERVIEW_XMM_6360.cfg:system/etc/amtl/catalog/CLOVERVIEW_XMM_6360.cfg \
        $(LOCAL_PATH)/CLOVERVIEW_XMM_7160.cfg:system/etc/amtl/catalog/CLOVERVIEW_XMM_7160.cfg \
        $(LOCAL_PATH)/CLOVERVIEW_XMM_7160_REV1.cfg:system/etc/amtl/catalog/CLOVERVIEW_XMM_7160_REV1.cfg \
        $(LOCAL_PATH)/VALLEYVIEW2_XMM_7160_REV3_5.cfg:system/etc/amtl/catalog/VALLEYVIEW2_XMM_7160_REV3_5.cfg \
        $(LOCAL_PATH)/VALLEYVIEW2_XMM_7160.cfg:system/etc/amtl/catalog/VALLEYVIEW2_XMM_7160.cfg

endif
