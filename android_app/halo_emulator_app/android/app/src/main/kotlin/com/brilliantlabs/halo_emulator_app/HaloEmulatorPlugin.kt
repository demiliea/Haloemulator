package com.brilliantlabs.halo_emulator_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.brilliantlabs.halo_emulator_app.ble.BrilliantBlePeripheral
import com.brilliantlabs.halo_emulator_app.emulator.HaloEmulatorEngine
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.Timer
import java.util.TimerTask

class HaloEmulatorPlugin(private val activity: MainActivity) : MethodChannel.MethodCallHandler {
  companion object {
    const val CHANNEL = "com.brilliantlabs.halo_emulator"
  }

  private val engine = HaloEmulatorEngine(activity.applicationContext)
  private var ble: BrilliantBlePeripheral? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  private var frameTimer: Timer? = null

  fun register(flutterEngine: FlutterEngine) {
    MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler(this)
    engine.setBluetoothSendListener { data ->
      ble?.notifyRx(data)
    }
    try {
      engine.loadAssetScript("lua/blink_main.lua", "blink_main.lua")
    } catch (_: Exception) {
    }
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "startScript" -> {
        val script = call.argument<String>("script") ?: "blink_main.lua"
        engine.startScript(script)
        startFramePolling()
        result.success(true)
      }
      "stopScript" -> {
        engine.stop()
        stopFramePolling()
        result.success(true)
      }
      "getFramebuffer" -> result.success(engine.getFramebufferPng())
      "injectButtonSingle" -> { engine.injectButtonSingle(); result.success(true) }
      "injectButtonDouble" -> { engine.injectButtonDouble(); result.success(true) }
      "injectButtonLong" -> { engine.injectButtonLong(); result.success(true) }
      "injectImuTap" -> { engine.injectImuTap(); result.success(true) }
      "injectBluetoothData" -> {
        val data = call.argument<ByteArray>("data")
        if (data != null) engine.injectBluetoothData(data)
        result.success(true)
      }
      "executeLua" -> {
        val code = call.argument<String>("code") ?: ""
        engine.executeLua(code)
        result.success(true)
      }
      "isRunning" -> result.success(engine.isRunning())
      "startBle" -> {
        if (!hasBlePermissions()) {
          requestBlePermissions()
          result.success(false)
          return
        }
        ble = BrilliantBlePeripheral(activity.applicationContext) { value ->
          if (value.isNotEmpty() && value[0] == 0x01.toByte()) {
            engine.injectBluetoothData(value)
          } else {
            val lua = String(value, Charsets.UTF_8)
            when {
              lua == "\u0003" -> engine.stop()
              lua == "\u0004" -> engine.stop()
              else -> engine.executeLua(lua)
            }
          }
        }
        val ok = ble?.start("Halo Emulator") ?: false
        result.success(ok)
      }
      "stopBle" -> {
        ble?.stop()
        ble = null
        result.success(true)
      }
      "isBleAdvertising" -> result.success(ble?.isAdvertising ?: false)
      else -> result.notImplemented()
    }
  }

  private fun startFramePolling() {
    stopFramePolling()
    frameTimer = Timer()
    frameTimer?.scheduleAtFixedRate(object : TimerTask() {
      override fun run() {
        // Framebuffer polled from Flutter side via getFramebuffer
      }
    }, 0, 33)
  }

  private fun stopFramePolling() {
    frameTimer?.cancel()
    frameTimer = null
  }

  fun dispose() {
    stopFramePolling()
    engine.stop()
    ble?.stop()
  }

  private fun hasBlePermissions(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val perms = arrayOf(
      Manifest.permission.BLUETOOTH_ADVERTISE,
      Manifest.permission.BLUETOOTH_CONNECT,
    )
    return perms.all {
      ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }
  }

  private fun requestBlePermissions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    ActivityCompat.requestPermissions(
      activity,
      arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
      ),
      1001,
    )
  }
}
