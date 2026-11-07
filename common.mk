#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Enable updating of APEXes
$(call inherit-product, $(SRC_TARGET_DIR)/product/updatable_apex.mk)

# A/B
$(call inherit-product, $(SRC_TARGET_DIR)/product/virtual_ab_ota.mk)

PRODUCT_PACKAGES += \
    android.hardware.boot-service.qti \
    android.hardware.boot-service.qti.recovery

PRODUCT_PACKAGES += \
    update_engine \
    update_engine_sideload \
    update_verifier

AB_OTA_POSTINSTALL_CONFIG += \
    RUN_POSTINSTALL_system=true \
    POSTINSTALL_PATH_system=system/bin/otapreopt_script \
    FILESYSTEM_TYPE_system=erofs \
    POSTINSTALL_OPTIONAL_system=true

AB_OTA_POSTINSTALL_CONFIG += \
    RUN_POSTINSTALL_vendor=true \
    POSTINSTALL_PATH_vendor=bin/checkpoint_gc \
    FILESYSTEM_TYPE_vendor=erofs \
    POSTINSTALL_OPTIONAL_vendor=true

PRODUCT_PACKAGES += \
    checkpoint_gc \
    otapreopt_script

# API levels
PRODUCT_SHIPPING_API_LEVEL := 36

# fastbootd
PRODUCT_PACKAGES += \
    android.hardware.fastboot-service.example_recovery \
    fastbootd

# Kernel
PRODUCT_ENABLE_UFFD_GC := true

# Overlays
PRODUCT_ENFORCE_RRO_TARGETS := *

# Partitions
PRODUCT_USE_DYNAMIC_PARTITIONS := true

# Product characteristics
PRODUCT_CHARACTERISTICS := nosdcard

# Rootdir
PRODUCT_PACKAGES += \
    coresight_reset_source_sink.sh \
    exec-debug-diag.sh \
    flash.sh \
    flash_before.sh \
    init.class_main.sh \
    init.crda.sh \
    init.kernel.extra_free_kbytes.sh \
    init.kernel.init_boot-memory.sh \
    init.kernel.post_boot-alor.sh \
    init.kernel.post_boot-alor_5_1.sh \
    init.kernel.post_boot-alor_5_2.sh \
    init.kernel.post_boot-alor_6_1.sh \
    init.kernel.post_boot-alor_default_6_2.sh \
    init.kernel.post_boot-canoe.sh \
    init.kernel.post_boot-canoe_5_1.sh \
    init.kernel.post_boot-canoe_5_2.sh \
    init.kernel.post_boot-canoe_6_1.sh \
    init.kernel.post_boot-canoe_default_6_2.sh \
    init.kernel.post_boot-memory.sh \
    init.kernel.post_boot.sh \
    init.mdm.sh \
    init.qcom.class_core.sh \
    init.qcom.coex.sh \
    init.qcom.early_boot.sh \
    init.qcom.efs.sync.sh \
    init.qcom.post_boot.sh \
    init.qcom.sdio.sh \
    init.qcom.sensors.sh \
    init.qcom.sh \
    init.qcom.usb.sh \
    init.qcrild.sh \
    init.qti.ese.strongbox.sh \
    init.qti.kernel.debug-alor.sh \
    init.qti.kernel.debug-canoe.sh \
    init.qti.kernel.debug.sh \
    init.qti.kernel.early_debug-canoe.sh \
    init.qti.kernel.early_debug.sh \
    init.qti.kernel.sh \
    init.qti.keymaster.sh \
    init.qti.media.sh \
    init.qti.qcv.sh \
    init.qti.write.sh \
    init.xiaomi.modem.cust.sh \
    init.xiaomi.modem.sh \
    qca6234-service.sh \
    start_subsys.sh \
    system_dlkm_modprobe.sh \
    ufs_ffu.sh \
    vendor.qti.diag.sh \
    vendor_modprobe.sh \

PRODUCT_PACKAGES += \
    fstab.qcom \
    init.batterysecret.rc \
    init.mi_ambient.rc \
    init.mi_thermald.rc \
    init.qcom.factory.rc \
    init.qcom.rc \
    init.qcom.usb.rc \
    init.qti.kernel.rc \
    init.qti.kernel.target.rc \
    init.qti.ufs.rc \
    init.target.rc \
    init.recovery.hardware.rc \
    init.recovery.qcom.rc \
    miui.factoryreset.rc \

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_VENDOR_RAMDISK_OUT)/first_stage_ramdisk/fstab.qcom

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH) \
    vendor/qcom/opensource/usb/etc

# Inherit the proprietary files
$(call inherit-product, vendor/xiaomi/sm8850-common/sm8850-common-vendor.mk)

# WiFi
PRODUCT_PACKAGES += \
    android.hardware.wifi-service \
    hostapd \
    hostapd_cli \
    libwifi-hal \
    libwifi-hal-ctrl \
    libwifi-hal-qcom \
    wpa_cli \
    wpa_supplicant \
    wpa_supplicant.conf

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.wifi.aware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.aware.xml \
    frameworks/native/data/etc/android.hardware.wifi.direct.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.direct.xml \
    frameworks/native/data/etc/android.hardware.wifi.passpoint.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.passpoint.xml \
    frameworks/native/data/etc/android.hardware.wifi.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.xml

# HALs built from source (stock binaries share these module names)
PRODUCT_PACKAGES += \
    android.hardware.camera.provider-V1-external-service \
    android.hardware.drm-service-lazy.clearkey \
    android.hardware.health-service.qti \
    android.hardware.health-service.qti_recovery \
    android.hardware.ir-service.example \
    android.hardware.sensors-service.multihal \
    android.hardware.usb-service.qti \
    android.hardware.usb.gadget-service.qti \
    sensors.dynamic_sensor_hal \
    usb_compositions.conf \
    vendor.qti.hardware.memtrack-service \
    vendor.qti.qspa-service


# IPA: ipacm and its configs built from hardware/qcom-caf/sm8850/data-ipa-cfg-mgr
# (configs identical to stock)
PRODUCT_PACKAGES += \
    ipacm \
    IPACM_Filter_cfg.xml \
    IPACM_cfg.xml

