import 'dart:typed_data';
import 'package:flutter/services.dart';

class EmulatorService {
  static const _channel = MethodChannel('com.brilliantlabs.halo_emulator');

  Future<void> startScript({String script = 'blink_main.lua'}) async {
    await _channel.invokeMethod('startScript', {'script': script});
  }

  Future<void> stopScript() async {
    await _channel.invokeMethod('stopScript');
  }

  Future<Uint8List?> getFramebuffer() async {
    final bytes = await _channel.invokeMethod<Uint8List>('getFramebuffer');
    return bytes;
  }

  Future<void> injectButtonSingle() => _channel.invokeMethod('injectButtonSingle');
  Future<void> injectButtonDouble() => _channel.invokeMethod('injectButtonDouble');
  Future<void> injectButtonLong() => _channel.invokeMethod('injectButtonLong');
  Future<void> injectImuTap() => _channel.invokeMethod('injectImuTap');

  Future<void> injectBluetoothData(Uint8List data) async {
    await _channel.invokeMethod('injectBluetoothData', {'data': data});
  }

  Future<void> executeLua(String code) async {
    await _channel.invokeMethod('executeLua', {'code': code});
  }

  Future<bool> isRunning() async {
    return await _channel.invokeMethod<bool>('isRunning') ?? false;
  }

  Future<bool> startBle() async {
    return await _channel.invokeMethod<bool>('startBle') ?? false;
  }

  Future<void> stopBle() async {
    await _channel.invokeMethod('stopBle');
  }

  Future<bool> isBleAdvertising() async {
    return await _channel.invokeMethod<bool>('isBleAdvertising') ?? false;
  }
}
