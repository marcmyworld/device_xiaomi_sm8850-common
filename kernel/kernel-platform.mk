#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Prebuilt stock kernel: GKI image, DTBs, DTBO, kernel headers and modules from the
# device's kernel repository. The device BoardConfig.mk sets KERNEL_PREBUILT_DIR before it
# includes BoardConfigCommon.mk; the kernel image itself is installed by the device's
# product makefile (PRODUCT_COPY_FILES).

ifeq ($(KERNEL_PREBUILT_DIR),)
$(error KERNEL_PREBUILT_DIR must be set by the device BoardConfig.mk)
endif

# Kernel and headers: no kernel is built; TARGET_KERNEL_SOURCE only provides the installed
# kernel UAPI headers for the Qualcomm HALs built from source.
TARGET_NO_KERNEL_OVERRIDE := true
TARGET_KERNEL_SOURCE := $(KERNEL_PREBUILT_DIR)/kernel-headers

# DTB and DTBO
BOARD_INCLUDE_DTB_IN_BOOTIMG := true
BOARD_PREBUILT_DTBIMAGE_DIR := $(KERNEL_PREBUILT_DIR)/dtb
BOARD_PREBUILT_DTBOIMAGE := $(KERNEL_PREBUILT_DIR)/dtbo.img

# Kernel modules: stock vendor_boot ramdisk, vendor_dlkm and system_dlkm sets with their
# stock load and block lists. Stock load lists repeat entries; keep the first occurrence.
KERNEL_MODULES_LOAD = $(strip $(shell awk '!seen[$$0]++' $(1)))
BOARD_VENDOR_RAMDISK_KERNEL_MODULES := $(wildcard $(KERNEL_PREBUILT_DIR)/vendor_ramdisk/*.ko)
BOARD_VENDOR_RAMDISK_KERNEL_MODULES_LOAD := $(call KERNEL_MODULES_LOAD,$(KERNEL_PREBUILT_DIR)/vendor_ramdisk/modules.load)
BOARD_VENDOR_RAMDISK_RECOVERY_KERNEL_MODULES_LOAD := $(call KERNEL_MODULES_LOAD,$(KERNEL_PREBUILT_DIR)/vendor_ramdisk/modules.load.recovery)
BOARD_VENDOR_RAMDISK_KERNEL_MODULES_BLOCKLIST_FILE := $(KERNEL_PREBUILT_DIR)/vendor_ramdisk/modules.blocklist
BOARD_VENDOR_KERNEL_MODULES := $(wildcard $(KERNEL_PREBUILT_DIR)/vendor_dlkm/*.ko)
BOARD_VENDOR_KERNEL_MODULES_LOAD := $(call KERNEL_MODULES_LOAD,$(KERNEL_PREBUILT_DIR)/vendor_dlkm/modules.load)
BOARD_VENDOR_KERNEL_MODULES_BLOCKLIST_FILE := $(KERNEL_PREBUILT_DIR)/vendor_dlkm/modules.blocklist
BOARD_SYSTEM_KERNEL_MODULES := $(wildcard $(KERNEL_PREBUILT_DIR)/system_dlkm/*.ko)
BOARD_SYSTEM_KERNEL_MODULES_LOAD := $(call KERNEL_MODULES_LOAD,$(KERNEL_PREBUILT_DIR)/system_dlkm/modules.load)
BOARD_SYSTEM_KERNEL_MODULES_BLOCKLIST_FILE := $(KERNEL_PREBUILT_DIR)/vendor_dlkm/system_dlkm.modules.blocklist
