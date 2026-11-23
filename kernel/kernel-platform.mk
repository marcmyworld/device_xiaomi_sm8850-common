#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

ifeq ($(KERNEL_PREBUILT_DIR),)
$(error KERNEL_PREBUILT_DIR must be set by the device BoardConfig.mk)
endif

# Kernel headers
TARGET_NO_KERNEL_OVERRIDE := true
TARGET_KERNEL_SOURCE := $(KERNEL_PREBUILT_DIR)/kernel-headers

# DTB and DTBO
BOARD_INCLUDE_DTB_IN_BOOTIMG := true
BOARD_PREBUILT_DTBIMAGE_DIR := $(KERNEL_PREBUILT_DIR)/dtb
BOARD_PREBUILT_DTBOIMAGE := $(KERNEL_PREBUILT_DIR)/dtbo.img

# Kernel modules
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
