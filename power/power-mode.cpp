/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "android.hardware.power-service.xiaomi-ext"

#include <aidl/android/hardware/power/BnPower.h>
#include <android-base/logging.h>
#include <android-base/unique_fd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <cstdint>

// Touch controller modes of the Xiaomi touch driver (/dev/xiaomi-touch), as set by the stock
// vendor.xiaomi.hw.touchfeature service.
#define TOUCH_DEV_PATH "/dev/xiaomi-touch"
#define TOUCH_ID 0
#define TOUCH_FOD_ENABLE_MODE 10
#define TOUCH_DOUBLETAP_MODE 14
#define TOUCH_SET_CUR_VALUE 0
#define TOUCH_CMD_DATA_BUF_SIZE 256

struct touch_data {
    int8_t touch_id;
    uint8_t cmd;
    uint16_t mode;
    uint16_t data_len;
    int32_t data_buf[TOUCH_CMD_DATA_BUF_SIZE];
};

// The request codes the stock touchfeature service passes to ioctl().
#define TOUCH_IOC_COMMON_DATA _IOWR('T', 0, struct touch_data)
#define TOUCH_IOC_SELECT_TOUCH_ID 0x40005403

static_assert(sizeof(struct touch_data) == 1032);
static_assert(TOUCH_IOC_COMMON_DATA == 0xc4085400);

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace impl {

using ::aidl::android::hardware::power::Mode;

static bool setTouchMode(uint16_t mode, bool enabled) {
    ::android::base::unique_fd fd(open(TOUCH_DEV_PATH, O_RDWR | O_CLOEXEC));
    if (fd < 0) {
        PLOG(ERROR) << "Failed to open " << TOUCH_DEV_PATH;
        return false;
    }

    // The touch id is passed by value.
    if (ioctl(fd, TOUCH_IOC_SELECT_TOUCH_ID, TOUCH_ID) < 0) {
        PLOG(ERROR) << "Failed to select touch " << TOUCH_ID;
        return false;
    }

    struct touch_data data = {};
    data.touch_id = TOUCH_ID;
    data.cmd = TOUCH_SET_CUR_VALUE;
    data.mode = mode;
    data.data_len = 1;
    data.data_buf[0] = enabled ? 1 : 0;
    if (ioctl(fd, TOUCH_IOC_COMMON_DATA, &data) < 0) {
        PLOG(ERROR) << "Failed to set touch mode " << mode << " to " << enabled;
        return false;
    }

    return true;
}

bool isDeviceSpecificModeSupported(Mode type, bool* _aidl_return) {
    switch (type) {
        case Mode::DOUBLE_TAP_TO_WAKE:
        case Mode::DISPLAY_INACTIVE:
            *_aidl_return = true;
            return true;
        default:
            return false;
    }
}

bool setDeviceSpecificMode(Mode type, bool enabled) {
    switch (type) {
        case Mode::DOUBLE_TAP_TO_WAKE:
            setTouchMode(TOUCH_DOUBLETAP_MODE, enabled);
            return true;
        case Mode::DISPLAY_INACTIVE:
            // Keep detecting a finger on the fingerprint sensor while the screen is off.
            setTouchMode(TOUCH_FOD_ENABLE_MODE, enabled);
            return true;
        default:
            return false;
    }
}

}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
