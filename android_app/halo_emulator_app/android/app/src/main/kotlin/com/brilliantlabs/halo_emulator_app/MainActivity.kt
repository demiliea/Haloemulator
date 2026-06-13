package com.brilliantlabs.halo_emulator_app

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
  private var emulatorPlugin: HaloEmulatorPlugin? = null

  override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
    super.configureFlutterEngine(flutterEngine)
    emulatorPlugin = HaloEmulatorPlugin(this).also { it.register(flutterEngine) }
  }

  override fun onDestroy() {
    emulatorPlugin?.dispose()
    super.onDestroy()
  }
}
