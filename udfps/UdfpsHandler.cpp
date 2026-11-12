/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "UdfpsHandler.xiaomi_sm8850"

#include <aidl/android/hardware/biometrics/fingerprint/AcquiredInfo.h>
#include <android-base/logging.h>

#include "UdfpsHandler.h"

// Commands of the Xiaomi fingerprint HAL modules (fingerprint_device::extCmd): the touch
// position over the sensor and the press state, as HyperOS's SystemUI sends them.
#define COMMAND_FOD_PRESS_STATUS 1
#define COMMAND_FOD_PRESS_X 2
#define COMMAND_FOD_PRESS_Y 3
#define PARAM_FOD_PRESSED 1
#define PARAM_FOD_RELEASED 0

// Vendor acquired codes: the module waits for a new touch to authenticate or enroll.
#define VENDOR_CODE_WAIT_FINGER_AUTH 201
#define VENDOR_CODE_WAIT_FINGER_ENROLL 202

using ::aidl::android::hardware::biometrics::fingerprint::AcquiredInfo;

class XiaomiSm8850UdfpsHandler : public UdfpsHandler {
  public:
    void init(fingerprint_device_t* device) { mDevice = device; }

    void onFingerDown(uint32_t x, uint32_t y, float /*minor*/, float /*major*/) {
        LOG(DEBUG) << __func__ << " x: " << x << " y: " << y;
        sendPress(x, y, PARAM_FOD_PRESSED);
    }

    void onFingerUp() {
        LOG(DEBUG) << __func__;
        sendPress(0, 0, PARAM_FOD_RELEASED);
    }

    void onAcquired(int32_t result, int32_t vendorCode) {
        LOG(DEBUG) << __func__ << " result: " << result << " vendorCode: " << vendorCode;
        // A new attempt starts: drop a press the framework did not release.
        if (static_cast<AcquiredInfo>(result) == AcquiredInfo::VENDOR &&
            (vendorCode == VENDOR_CODE_WAIT_FINGER_AUTH ||
             vendorCode == VENDOR_CODE_WAIT_FINGER_ENROLL)) {
            onFingerUp();
        }
    }

    void cancel() { onFingerUp(); }

  private:
    fingerprint_device_t* mDevice = nullptr;

    void sendPress(int32_t x, int32_t y, int32_t status) {
        if (!mDevice) {
            LOG(ERROR) << "No fingerprint HAL module opened";
            return;
        }
        mDevice->extCmd(mDevice, COMMAND_FOD_PRESS_X, x);
        mDevice->extCmd(mDevice, COMMAND_FOD_PRESS_Y, y);
        mDevice->extCmd(mDevice, COMMAND_FOD_PRESS_STATUS, status);
    }
};

static UdfpsHandler* create() {
    return new XiaomiSm8850UdfpsHandler();
}

static void destroy(UdfpsHandler* handler) {
    delete handler;
}

extern "C" UdfpsHandlerFactory UDFPS_HANDLER_FACTORY = {
        .create = create,
        .destroy = destroy,
};
