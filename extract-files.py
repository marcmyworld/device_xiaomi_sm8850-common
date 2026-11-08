#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixups,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

namespace_imports = [
    'device/xiaomi/sm8850-common',
    'hardware/qcom-caf/sm8850',
    'hardware/qcom-caf/wlan',
    'hardware/xiaomi',
    'vendor/qcom/opensource/commonsys-intf/display',
    'vendor/qcom/opensource/dataservices',
]

blob_fixups: blob_fixups_user_type = {
    # Stock blobs link older versions of stable AIDL interfaces than LineageOS
    # builds; point them at the newest frozen version instead of shipping both.
    'vendor/lib64/libqti-perfd.so': blob_fixup()
        .replace_needed('vendor.qti.hardware.display.config-V12-ndk.so', 'vendor.qti.hardware.display.config-V18-ndk.so'),
    (
        'vendor/bin/hw/audiohalservice.qti',
        'vendor/bin/hw/vendor.qti.hardware.display.composer-service',
        'vendor/bin/poweropt-service',
        'vendor/lib64/hw/libaudiocorehal.qti.so',
        'vendor/lib64/hw/libaudioeffecthal.qti.so',
        'vendor/lib64/libaudiocloudctrl.so',
        'vendor/lib64/libdemura_oem_plugin.so',
        'vendor/lib64/liblearningmodule.so',
        'vendor/lib64/libpowercore.so',
        'vendor/lib64/libsdmcore.so',
        'vendor/lib64/libxlog.so',
        'vendor/lib64/soundfx/libquasar.so',
    ): blob_fixup()
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v36.so'),
    # libsdmclient is BOLT-optimized; newer patchelf moves its program
    # headers to a file offset that does not match PT_PHDR, and the linker
    # then finds no PT_DYNAMIC (composer fails to start).
    'vendor/lib64/libsdmclient.so': blob_fixup()
        .patchelf_version('0_9')
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v36.so'),
    # libcodec2_aidl_noisurface (frameworks/av) uses media.c2 V2; services on
    # libcodec2_aidl stay on V1 like the source library
    'vendor/bin/hw/vendor.qti.media.c2@1.0-service': blob_fixup()
        .replace_needed('android.hardware.media.c2-V1-ndk.so', 'android.hardware.media.c2-V2-ndk.so'),
    (
        'vendor/bin/qguard',
        'vendor/lib64/libqcodec2_utils.so',
        'vendor/lib64/libwfddisplayconfig_vendor.so',
    ): blob_fixup()
        .replace_needed('vendor.qti.hardware.display.config-V5-ndk.so', 'vendor.qti.hardware.display.config-V18-ndk.so'),
    'vendor/bin/trusteduilistener': blob_fixup()
        .replace_needed('vendor.qti.hardware.display.config-V7-ndk.so', 'vendor.qti.hardware.display.config-V18-ndk.so')
        .replace_needed('vendor.qti.hardware.systemhelperaidl-V1-ndk.so', 'vendor.qti.hardware.systemhelperaidl-V2-ndk.so'),
    (
        'vendor/lib64/libTrustedUIAIDL.so',
        'vendor/lib64/libops.so',
    ): blob_fixup()
        .replace_needed('vendor.qti.hardware.display.config-V7-ndk.so', 'vendor.qti.hardware.display.config-V18-ndk.so'),
    (
        'vendor/lib64/libcamerapoweroptfeature.so',
        'vendor/lib64/libgamepoweroptfeature.so',
        'vendor/lib64/liboffscreenpoweroptfeature.so',
        'vendor/lib64/libpsmoptfeature.so',
        'vendor/lib64/libvideooptfeature.so',
    ): blob_fixup()
        .replace_needed('vendor.qti.hardware.display.config-V5-ndk.so', 'vendor.qti.hardware.display.config-V18-ndk.so')
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v36.so'),
    'vendor/lib64/libdolbyui.so': blob_fixup()
        .replace_needed('android.hardware.graphics.common-V6-ndk.so', 'android.hardware.graphics.common-V7-ndk.so'),
    'vendor/lib64/libqcodec2_core.so': blob_fixup()
        .replace_needed('android.hardware.graphics.common-V5-ndk.so', 'android.hardware.graphics.common-V7-ndk.so'),
    'vendor/lib64/libqti-qape-engine.so': blob_fixup()
        .replace_needed('vendor.qti.hardware.display.config-V12-ndk.so', 'vendor.qti.hardware.display.config-V18-ndk.so'),
    # The stock display_boot service runs the renamed stock script
    'vendor/etc/init/init.qti.display_boot_stock.rc': blob_fixup()
        .regex_replace(r'/vendor/bin/init\.qti\.display_boot\.sh', '/vendor/bin/init.qti.display_boot_stock.sh'),
    # Android 17 changed BluetoothAudioSession::IsSessionReady() and dropped
    # audio_utils::mutex_get_enable_flag(); the LineageOS compat shims provide them
    'vendor/lib64/libaudioserviceexampleimpl.so': blob_fixup()
        .add_needed('libaudioutils_shim.so')
        .add_needed('libbluetooth_audio_session_aidl_shim.so'),
}  # fmt: skip

module = ExtractUtilsModule(
    'sm8850-common',
    'xiaomi',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
)

if __name__ == '__main__':
    utils = ExtractUtils.device(module)
    utils.run()
