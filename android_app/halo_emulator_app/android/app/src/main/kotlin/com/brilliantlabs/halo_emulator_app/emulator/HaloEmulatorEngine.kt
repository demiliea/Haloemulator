package com.brilliantlabs.halo_emulator_app.emulator

import android.content.Context
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class HaloEmulatorEngine(private val context: Context) {
    private val display = DisplayBuffer()
    private val sandboxDir = File(context.filesDir, "lua_sandbox").apply { mkdirs() }
    private val globals = JsePlatform.standardGlobals()
    private val eventQueue = ArrayBlockingQueue<EmulatorEvent>(64)
    private val running = AtomicBoolean(false)
    private var luaThread: Thread? = null
    private var bluetoothSendListener: ((ByteArray) -> Unit)? = null
    private var receiveCallback: LuaValue = LuaValue.NIL
    private var batteryLevel = 85

    fun setBluetoothSendListener(listener: ((ByteArray) -> Unit)?) {
        bluetoothSendListener = listener
    }

    fun buildRuntime() {
        val frame = LuaTable()
        frame.set("HARDWARE_VERSION", "EMULATOR")
        frame.set("FIRMWARE_VERSION", "0.0.0-emulator")
        frame.set("GIT_TAG", "emulator")
        frame.set("SE_REVISION", "0.0.0")

        frame.set("sleep", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val seconds = if (arg.isnil()) 0.0 else arg.checkdouble()
                val deadline = System.nanoTime() + (seconds * 1_000_000_000).toLong()
                while (System.nanoTime() < deadline) {
                    if (!running.get()) throw InterruptedException("stopped")
                    drainEvents()
                    Thread.sleep(1)
                }
                return NONE
            }
        })
        frame.set("yield", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                drainEvents()
                return NONE
            }
        })
        frame.set("light_sleep", frame.get("sleep"))
        frame.set("standby", frame.get("sleep"))
        frame.set("battery_level", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(batteryLevel)
        })
        frame.set("battery_voltage", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(4100)
        })
        frame.set("battery_charging", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaValue.FALSE
        })
        frame.set("wakeup_source", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaValue.valueOf("timeout")
        })
        frame.set("stay_awake", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = LuaValue.TRUE
        })
        frame.set("reboot", object : ZeroArgFunction() {
            override fun call(): LuaValue { running.set(false); throw InterruptedException("reboot") }
        })

        val disp = LuaTable()
        disp.set("clear", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                display.clear(if (arg.isnil()) 0 else arg.checkint())
                return NONE
            }
        })
        disp.set("text", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                display.text(args.arg(1).checkjstring(), args.arg(2).checkint(), args.arg(3).checkint(), args.arg(4).optint(0xFFFFFF))
                return NONE
            }
        })
        disp.set("set_pixel", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                display.setPixel(args.arg(1).checkint(), args.arg(2).checkint(), args.arg(3).checkint())
                return NONE
            }
        })
        disp.set("line", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                display.line(args.arg(1).checkint(), args.arg(2).checkint(), args.arg(3).checkint(), args.arg(4).checkint(), args.arg(5).checkint())
                return NONE
            }
        })
        disp.set("rect", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                display.rect(args.arg(1).checkint(), args.arg(2).checkint(), args.arg(3).checkint(), args.arg(4).checkint(), args.arg(5).checkint(), args.arg(6).optboolean(false))
                return NONE
            }
        })
        disp.set("circle", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                display.circle(args.arg(1).checkint(), args.arg(2).checkint(), args.arg(3).checkint(), args.arg(4).checkint(), args.arg(5).optboolean(false))
                return NONE
            }
        })
        disp.set("show", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue { display.show(); return NONE }
        })
        disp.set("width", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(display.width())
        })
        disp.set("height", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(display.height())
        })
        frame.set("display", disp)

        val bt = LuaTable()
        bt.set("is_connected", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaValue.TRUE
        })
        bt.set("address", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaValue.valueOf("AA:BB:CC:DD:EE:FF")
        })
        bt.set("max_length", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(243)
        })
        bt.set("send", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val bytes = arg.checkjstring().toByteArray(Charsets.ISO_8859_1)
                bluetoothSendListener?.invoke(bytes)
                return NONE
            }
        })
        bt.set("receive_callback", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                receiveCallback = if (arg.isnil()) LuaValue.NIL else arg
                return NONE
            }
        })
        frame.set("bluetooth", bt)

        val btn = LuaTable()
        btn.set("single", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue { buttonSingle = if (arg.isnil()) null else arg.checkfunction(); return NONE }
        })
        btn.set("double", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue { buttonDouble = if (arg.isnil()) null else arg.checkfunction(); return NONE }
        })
        btn.set("long", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue { buttonLong = if (arg.isnil()) null else arg.checkfunction(); return NONE }
        })
        frame.set("button", btn)

        val imu = LuaTable()
        imu.set("tap_callback", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue { imuTap = if (arg.isnil()) null else arg.checkfunction(); return NONE }
        })
        imu.set("direction", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                val t = LuaTable()
                t.set("pitch", 0.0); t.set("roll", 0.0); t.set("heading", 0.0)
                return t
            }
        })
        frame.set("imu", imu)

        val timeTbl = LuaTable()
        timeTbl.set("utc", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = LuaValue.valueOf(System.currentTimeMillis() / 1000.0)
        })
        frame.set("time", timeTbl)

        globals.set("frame", frame)
        globals.set("print", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val line = arg.tojstring()
                bluetoothSendListener?.invoke(line.toByteArray(Charsets.UTF_8))
                return NONE
            }
        })
    }

    private var buttonSingle: org.luaj.vm2.LuaFunction? = null
    private var buttonDouble: org.luaj.vm2.LuaFunction? = null
    private var buttonLong: org.luaj.vm2.LuaFunction? = null
    private var imuTap: org.luaj.vm2.LuaFunction? = null

    private fun drainEvents() {
        while (true) {
            val event = eventQueue.poll() ?: break
            when (event) {
                is EmulatorEvent.Ble -> {
                    if (!receiveCallback.isnil()) {
                        receiveCallback.call(LuaValue.valueOf(event.data.toString(Charsets.ISO_8859_1)))
                    }
                }
                EmulatorEvent.ButtonSingle -> buttonSingle?.call()
                EmulatorEvent.ButtonDouble -> buttonDouble?.call()
                EmulatorEvent.ButtonLong -> buttonLong?.call()
                EmulatorEvent.ImuTap -> imuTap?.call()
            }
        }
    }

    fun loadAssetScript(assetPath: String, destName: String) {
        context.assets.open(assetPath).use { input ->
            File(sandboxDir, destName).outputStream().use { output -> input.copyTo(output) }
        }
    }

    fun startScript(scriptName: String) {
        stop()
        buildRuntime()
        running.set(true)
        val script = File(sandboxDir, scriptName)
        if (!script.exists()) {
            context.assets.open("lua/$scriptName").use { input ->
                script.outputStream().use { output -> input.copyTo(output) }
            }
        }
        luaThread = thread(name = "halo-lua") {
            try {
                val chunk = globals.loadfile(script.absolutePath)
                chunk.call()
            } catch (_: Exception) {
            } finally {
                running.set(false)
            }
        }
    }

    fun executeLua(code: String): String? {
        buildRuntime()
        val chunk = globals.load(code, "repl")
        chunk.call()
        return null
    }

    fun stop() {
        running.set(false)
        luaThread?.join(2000)
        luaThread = null
    }

    fun injectBluetoothData(data: ByteArray) {
        eventQueue.offer(EmulatorEvent.Ble(data))
    }

    fun injectButtonSingle() { eventQueue.offer(EmulatorEvent.ButtonSingle) }
    fun injectButtonDouble() { eventQueue.offer(EmulatorEvent.ButtonDouble) }
    fun injectButtonLong() { eventQueue.offer(EmulatorEvent.ButtonLong) }
    fun injectImuTap() { eventQueue.offer(EmulatorEvent.ImuTap) }

    fun getFramebufferPng(): ByteArray = display.toPng()
    fun isRunning(): Boolean = running.get()
}

private sealed class EmulatorEvent {
    data class Ble(val data: ByteArray) : EmulatorEvent()
    object ButtonSingle : EmulatorEvent()
    object ButtonDouble : EmulatorEvent()
    object ButtonLong : EmulatorEvent()
    object ImuTap : EmulatorEvent()
}
