# Brilliant SDK Emulator

A Python emulator for the [Brilliant SDK](https://github.com/brilliantlabsAR/brilliant_sdk) — run and test Lua apps for [Brilliant Labs](https://brilliant.xyz/) Halo and Frame smart glasses without hardware.

## Overview

Brilliant glasses run user scripts in an on-device **Lua 5.3 VM** and expose the `frame.*` API for display, Bluetooth, IMU, buttons, file I/O, audio, and more. This emulator embeds the same Lua runtime and stubs all `frame.*` calls in Python, rendering display output to a 256×256 pixel buffer.

## Features

- **Full Lua 5.3 runtime** — run unmodified device scripts
- **Virtual 256×256 display** — drawing primitives, palette, text, and bitmap ops
- **Event injection** — simulate BLE data, button presses, and IMU taps from Python
- **BrilliantMsg adapter** — drop-in `EmulatorBrilliantMsg` for testing host apps against `brilliant-msg`
- **Interactive REPL** — `halo-emulator ./app/` opens a live pygame window and Python REPL
- **Video recording** — capture display output as GIF or MP4

## Android App (APK)

An Android app bridges the emulator to a phone, advertising as a Halo device over BLE:

```bash
export PATH="/workspace/tools/flutter/bin:$PATH"
export ANDROID_HOME=/workspace/android-sdk
cd android_app/halo_emulator_app
flutter build apk --release
```

APK output: `android_app/halo_emulator_app/build/app/outputs/flutter-apk/app-release.apk`

See [android_app/halo_emulator_app/README.md](android_app/halo_emulator_app/README.md) for details.

## Quick Start

```bash
cd python
uv sync --all-packages
uv run halo-emulator examples/lua/ --script blink_main.lua
```

### Python API

```python
from halo_emulator import HaloEmulator

emu = HaloEmulator(sandbox_dir="./my_app")
emu.load_directory("./my_app")
emu.start("main.lua")

emu.inject_button_single()
img = emu.get_framebuffer()
img.save("output.png")

emu.stop()
```

### With brilliant-msg

```python
from halo_emulator import HaloEmulator, EmulatorBrilliantMsg

emu = HaloEmulator()
frame = EmulatorBrilliantMsg(emu)
# Use frame like brilliant_msg.BrilliantMsg — connect, send_message, etc.
```

## Documentation

See [python/packages/halo_emulator/README.md](python/packages/halo_emulator/README.md) for the full API reference, supported `frame.*` API, and testing guide.

## License

BSD 3-Clause — see [LICENSE](LICENSE).
