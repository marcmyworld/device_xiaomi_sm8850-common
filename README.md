# device_xiaomi_sm8850-common

Common device tree for the **Xiaomi 17 series** (Snapdragon 8 Elite Gen 5 / SM8850, platform
`canoe`), used by the per-device trees. Built on **LineageOS 24.0** (Android 17).

> **Status: bring-up. Boots to LineageOS; hardware support is being tested.** Supported device:
> `pudding` (Xiaomi 17).

## Layout
| File | Purpose |
|---|---|
| `BoardConfigCommon.mk` | Boot layout (header v4, dedicated A/B recovery, LZ4), super/EROFS partitions, AVB |
| `kernel/kernel-platform.mk` | Prebuilt kernel setup: kernel headers, DTBs, DTBO and the module sets with their load lists, from the device's kernel repo (`KERNEL_PREBUILT_DIR`) |
| `common.mk` | Product configuration shared by the series |
| `proprietary-files.txt` | Blobs from the **vendor** partition. Xiaomi builds the vendor image once for the series (`ro.product.vendor.device=mivendor`); device-specific files come from the odm partition and are listed in each device tree |
| `extract-files.py`, `setup-makefiles.py` | LineageOS extract-utils module `sm8850-common` |
| `rootdir/`, `manifest.xml`, `*.prop` | Init scripts, VINTF manifest and properties from stock |
| `sepolicy/vendor/` | The part of the stock vendor SELinux policy that the shipped blobs need, on top of the Qualcomm SM8850 policy in `device/qcom/sepolicy_vndr` |
| `sepolicy/integration/` | Hand-written rules the stock blobs need on top of that with source-built services and the LineageOS platform policy (each rule explains its reason) |
| `udfps/` | `libudfpshandler`: forwards under-display fingerprint touches to the stock Xiaomi HAL module hosted by the `hardware/xiaomi` fingerprint service |
| `overlays/` | Overlays with the values of the stock vendor overlays (Wi-Fi, NFC, SecureElement) |
| `lineage.dependencies` | Extra LineageOS repositories this tree needs (`hardware/xiaomi`) |

## Source firmware
All values and blobs come from HyperOS **OS3.0.335.0.XPCEUXM** (EEA global) for the Xiaomi 17
(vendor build `Xiaomi/mivendor/mivendor:16/BQ2A.260225.001-BP2A.250705.008/OS3.0.335.0.XPCMI:user/release-keys`).

## Use
Pulled in by a device tree, e.g. [device_xiaomi_pudding](https://github.com/xiaomi17-pudding-dev/device_xiaomi_pudding):
`BoardConfig.mk` includes `BoardConfigCommon.mk`, `device.mk` inherits `common.mk`. Also needs
LineageOS [`android_hardware_xiaomi`](https://github.com/LineageOS/android_hardware_xiaomi) at
`hardware/xiaomi`; the fingerprint service needs the change that adds the Qualcomm ultrasonic
HAL module (`qcom_us`), see the manifest. Blobs live on GitLab: [vendor_xiaomi_sm8850-common](https://gitlab.com/xiaomi17-pudding-dev/vendor_xiaomi_sm8850-common).
Build instructions: [local_manifests_pudding](https://github.com/xiaomi17-pudding-dev/local_manifests_pudding).
Updating to a newer stock firmware: see the device tree's README ("Updating to a new firmware").

## Credits
Structure follows the LineageOS Xiaomi SM8550 trees
([android_device_xiaomi_sm8550-common](https://github.com/LineageOS/android_device_xiaomi_sm8550-common)).
Initially generated with [aospdtgen](https://github.com/SebaUbuntu/aospdtgen).

## License
Apache-2.0. Proprietary files are redistributed as shipped in the stock firmware above.
