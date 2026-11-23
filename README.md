# Common device tree for Xiaomi SM8850

Common device tree for the **Xiaomi 17 series** and Snapdragon 8 Elite Gen 5 (SM8850-AC) devices, platform `canoe`.

## Building LineageOS

Clone the device manifest into `.repo/local_manifests/`:
```bash
git clone https://github.com/xiaomi17-pudding-dev/local_manifests_pudding .repo/local_manifests/pudding.xml
```

Initialize the build environment and start compilation for a target device (e.g. `pudding`):

```bash
source build/envsetup.sh
lunch lineage_pudding-userdebug
m bacon
```

## Generating the vendor tree

1. **Extract Blobs:** Run the extract script pointing to the unpacked stock dump:
   ```bash
   ./extract-files.py <path-to-unpacked-firmware>
   ```

2. Your extracted blobs are saved within a vendor tree located at `vendor/xiaomi/sm8850-common`.

## Credits
Structure follows the LineageOS Xiaomi SM8550 trees
([android_device_xiaomi_sm8550-common](https://github.com/LineageOS/android_device_xiaomi_sm8550-common)).
Initially generated with [aospdtgen](https://github.com/SebaUbuntu/aospdtgen).

## License
Apache-2.0. Proprietary files are redistributed as shipped in the stock firmware above.
