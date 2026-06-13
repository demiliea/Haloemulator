# Halo Emulator — Android App

Android APK that emulates Brilliant Labs Halo smart glasses on your phone.

## Features

- **256×256 virtual display** in a circular glasses-style UI
- **Lua 5.x runtime** (LuaJ) running device scripts (`blink_main.lua`, `tap_counter.lua`)
- **BLE GATT peripheral** advertising as a Halo device so other phones running Brilliant SDK apps can connect
- **Touch controls** for button click, double-click, long press, and IMU tap injection

## Build APK

```bash
export PATH="/workspace/tools/flutter/bin:$PATH"
export ANDROID_HOME=/workspace/android-sdk
cd android_app/halo_emulator_app
flutter pub get
flutter build apk --release
```

Output: `build/app/outputs/flutter-apk/app-release.apk`

## Architecture

```
Flutter UI (lib/main.dart)
    ↕ MethodChannel
Kotlin HaloEmulatorPlugin
    ├── HaloEmulatorEngine (LuaJ + frame.* stubs)
    └── BrilliantBlePeripheral (GATT server, Brilliant service UUIDs)
```

The Kotlin emulator mirrors the Python `halo_emulator` package: display, Bluetooth, buttons, IMU, and system APIs. BLE uses the same service/characteristic UUIDs as real Halo/Frame hardware:

| UUID | Role |
|------|------|
| `7a230001-…` | Brilliant service |
| `7a230002-…` | TX (host writes Lua/data) |
| `7a230003-…` | RX (device notifies host) |
| `7a230005-…` | Audio TX (Halo) |

## Usage

1. Install the APK on an Android phone
2. Tap **Start** to run a bundled Lua script
3. Tap **Advertise BLE** to make the phone discoverable as "Halo Emulator"
4. On a second phone, open any Brilliant SDK app and scan for devices

## Permissions

On Android 12+, grant Bluetooth Advertise and Connect permissions when prompted.
