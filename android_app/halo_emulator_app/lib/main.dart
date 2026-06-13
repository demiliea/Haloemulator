import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'emulator_service.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const HaloEmulatorApp());
}

class HaloEmulatorApp extends StatelessWidget {
  const HaloEmulatorApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Halo Emulator',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF1A1A2E),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      home: const EmulatorScreen(),
    );
  }
}

class EmulatorScreen extends StatefulWidget {
  const EmulatorScreen({super.key});

  @override
  State<EmulatorScreen> createState() => _EmulatorScreenState();
}

class _EmulatorScreenState extends State<EmulatorScreen> {
  final _emulator = EmulatorService();
  Uint8List? _frame;
  bool _running = false;
  bool _bleActive = false;
  String _selectedScript = 'blink_main.lua';
  Timer? _pollTimer;

  final _scripts = ['blink_main.lua', 'tap_counter.lua'];

  @override
  void initState() {
    super.initState();
    _bootstrap();
  }

  Future<void> _bootstrap() async {
    await _startEmulator();
    _pollTimer = Timer.periodic(const Duration(milliseconds: 100), (_) => _refreshFrame());
  }

  Future<void> _refreshFrame() async {
    final bytes = await _emulator.getFramebuffer();
    if (!mounted || bytes == null) return;
    setState(() => _frame = bytes);
  }

  Future<void> _startEmulator() async {
    await _emulator.startScript(script: _selectedScript);
    final running = await _emulator.isRunning();
    setState(() => _running = running);
  }

  Future<void> _stopEmulator() async {
    await _emulator.stopScript();
    setState(() => _running = false);
  }

  Future<void> _toggleBle() async {
    if (_bleActive) {
      await _emulator.stopBle();
      setState(() => _bleActive = false);
      return;
    }
    final ok = await _emulator.startBle();
    setState(() => _bleActive = ok);
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('BLE advertising failed. Enable Bluetooth and grant permissions.')),
      );
    }
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    _emulator.stopScript();
    _emulator.stopBle();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0D0D14),
      appBar: AppBar(
        title: const Text('Halo Emulator'),
        backgroundColor: const Color(0xFF16162A),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: Chip(
              avatar: Icon(
                _bleActive ? Icons.bluetooth_connected : Icons.bluetooth_disabled,
                size: 18,
                color: _bleActive ? Colors.greenAccent : Colors.grey,
              ),
              label: Text(_bleActive ? 'BLE On' : 'BLE Off'),
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SizedBox(height: 16),
            _GlassesDisplay(frame: _frame),
            const SizedBox(height: 20),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      value: _selectedScript,
                      decoration: const InputDecoration(
                        labelText: 'Lua script',
                        border: OutlineInputBorder(),
                      ),
                      items: _scripts
                          .map((s) => DropdownMenuItem(value: s, child: Text(s)))
                          .toList(),
                      onChanged: _running
                          ? null
                          : (v) {
                              if (v != null) setState(() => _selectedScript = v);
                            },
                    ),
                  ),
                  const SizedBox(width: 8),
                  FilledButton(
                    onPressed: _running ? _stopEmulator : _startEmulator,
                    child: Text(_running ? 'Stop' : 'Start'),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              alignment: WrapAlignment.center,
              children: [
                _EventButton(label: 'Tap', icon: Icons.touch_app, onPressed: _emulator.injectImuTap),
                _EventButton(label: 'Click', icon: Icons.radio_button_checked, onPressed: _emulator.injectButtonSingle),
                _EventButton(label: 'Double', icon: Icons.double_arrow, onPressed: _emulator.injectButtonDouble),
                _EventButton(label: 'Long', icon: Icons.timer, onPressed: _emulator.injectButtonLong),
                _EventButton(
                  label: _bleActive ? 'Stop BLE' : 'Advertise BLE',
                  icon: Icons.bluetooth,
                  onPressed: _toggleBle,
                ),
              ],
            ),
            const SizedBox(height: 16),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 24),
              child: Text(
                'Advertise BLE to connect from another phone running a Brilliant SDK app. '
                'The display shows the virtual 256×256 Halo glasses screen.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.white54, fontSize: 13),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _GlassesDisplay extends StatelessWidget {
  const _GlassesDisplay({required this.frame});

  final Uint8List? frame;

  @override
  Widget build(BuildContext context) {
    const size = 280.0;
    return Container(
      width: size + 40,
      height: size + 40,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: const Color(0xFF1E1E30),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.5),
            blurRadius: 24,
            offset: const Offset(0, 8),
          ),
        ],
        border: Border.all(color: const Color(0xFF3A3A55), width: 3),
      ),
      child: Center(
        child: ClipOval(
          child: frame != null
              ? Image.memory(frame!, width: size, height: size, fit: BoxFit.cover, gaplessPlayback: true)
              : Container(
                  width: size,
                  height: size,
                  color: Colors.black,
                  alignment: Alignment.center,
                  child: const Text('256×256', style: TextStyle(color: Colors.white24)),
                ),
        ),
      ),
    );
  }
}

class _EventButton extends StatelessWidget {
  const _EventButton({
    required this.label,
    required this.icon,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final Future<void> Function() onPressed;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: () async {
        HapticFeedback.lightImpact();
        await onPressed();
      },
      icon: Icon(icon, size: 18),
      label: Text(label),
    );
  }
}
