# GameDeck v27 – proper Eden overlay ABI, controller readiness and safe test telemetry
from pathlib import Path
import sys

d=Path(sys.argv[1])/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"
def once(s,o,n,name):
    ct=s.count(o)
    if ct!=1: raise RuntimeError("v27 "+name+": "+str(ct)+" occurrence(s)")
    return s.replace(o,n,1)

file=d/"GameDeckAutoPilot.kt"
s=file.read_text(encoding="utf-8")
s=once(s,"import org.yuzu.yuzu_emu.features.input.NativeInput",
'''import org.yuzu.yuzu_emu.features.input.NativeInput
import org.yuzu.yuzu_emu.features.input.model.NativeButton
import org.yuzu.yuzu_emu.features.input.model.NativeAnalog''',"emulator overlay enum types")

s=once(s,'    private var controlGuid=OVERLAY_GUID',
'''    private var virtualOverlay=true
    private var controlGuid=OVERLAY_GUID''',"virtual vs physical")
s=once(s,'''        if(controller!=null) {
            controlGuid=controller.getGUID()''',
'''        if(controller!=null) {
            virtualOverlay=false
            controlGuid=controller.getGUID()''',"physical setup")
s=once(s,'''        } else {
            controlGuid=OVERLAY_GUID''',
'''        } else {
            virtualOverlay=true
            controlGuid=OVERLAY_GUID''',"overlay setup")

s=once(s,'''    private fun releaseAll() {''',
'''    /**
     * IMPORTANT: Eden has TWO input ABIs. Physical gamepads accept Android
     * keycodes via onGamePadButtonEvent; the virtual overlay accepts a
     * NativeButton enum through onOverlayButtonEvent. Treating these APIs
     * as interchangeable silently sends inputs to an unmapped controller.
     */
    private fun nativeOverlayButton(code:Int):NativeButton?=when(code) {
        KeyEvent.KEYCODE_BUTTON_A -> NativeButton.A
        KeyEvent.KEYCODE_BUTTON_B -> NativeButton.B
        KeyEvent.KEYCODE_BUTTON_X -> NativeButton.X
        KeyEvent.KEYCODE_BUTTON_Y -> NativeButton.Y
        KeyEvent.KEYCODE_BUTTON_START -> NativeButton.Plus
        KeyEvent.KEYCODE_BUTTON_SELECT -> NativeButton.Minus
        KeyEvent.KEYCODE_DPAD_UP -> NativeButton.DUp
        KeyEvent.KEYCODE_DPAD_DOWN -> NativeButton.DDown
        KeyEvent.KEYCODE_DPAD_LEFT -> NativeButton.DLeft
        KeyEvent.KEYCODE_DPAD_RIGHT -> NativeButton.DRight
        else -> null
    }

    private fun sendButton(code:Int,state:Int) {
        if(virtualOverlay) {
            val button=nativeOverlayButton(code)
                ?:throw IllegalArgumentException("Nicht unterstützte virtuelle Taste: "+code)
            NativeInput.onOverlayButtonEvent(controlPort,button,state)
        } else {
            NativeInput.onGamePadButtonEvent(controlGuid,controlPort,code,state)
        }
    }

    private fun sendAxis(axis:Int,value:Float) {
        if(virtualOverlay) {
            // Overlay API expects both axes of the native Switch left stick
            // in one call, not Android's individual AXIS_X/AXIS_Y events.
            val x=if(axis==MotionEvent.AXIS_X) value else 0f
            val y=if(axis==MotionEvent.AXIS_Y) value else 0f
            NativeInput.onOverlayJoystickEvent(controlPort,NativeAnalog.LStick,x,y)
        } else {
            NativeInput.onGamePadAxisEvent(controlGuid,controlPort,axis,value)
        }
    }

    private fun releaseAll() {''',"emulator ABI distinction")
s=once(s,'''                NativeInput.onGamePadButtonEvent(controlGuid,controlPort,key,
                    NativeInput.ButtonState.RELEASED)''',
'''                sendButton(key,NativeInput.ButtonState.RELEASED)''',"release real button")
s=once(s,'''            NativeInput.onGamePadAxisEvent(controlGuid,controlPort,MotionEvent.AXIS_X,0f)
            NativeInput.onGamePadAxisEvent(controlGuid,controlPort,MotionEvent.AXIS_Y,0f)''',
'''            sendAxis(MotionEvent.AXIS_X,0f)
            sendAxis(MotionEvent.AXIS_Y,0f)''',"release real axis")
s=once(s,'''                NativeInput.onGamePadButtonEvent(controlGuid,controlPort,choice.button,
                    NativeInput.ButtonState.PRESSED)''',
'''                sendButton(choice.button,NativeInput.ButtonState.PRESSED)''',"dispatch real button")
s=once(s,'''                NativeInput.onGamePadAxisEvent(controlGuid,controlPort,choice.axis,choice.value)''',
'''                sendAxis(choice.axis,choice.value)''',"dispatch real stick")
s=once(s,'''                    if(choice.button!=0) NativeInput.onGamePadButtonEvent(
                        controlGuid,controlPort,choice.button,NativeInput.ButtonState.RELEASED)
                    else NativeInput.onGamePadAxisEvent(controlGuid,controlPort,choice.axis,0f)''',
'''                    if(choice.button!=0) sendButton(
                        choice.button,NativeInput.ButtonState.RELEASED)
                    else sendAxis(choice.axis,0f)''',"release key after timed action")
s=once(s,'''        .put("controller_source",controlSource)''',
'''        .put("controller_source",controlSource)
        .put("input_abi",if(virtualOverlay) "eden_native_overlay" else "android_gamepad")
        .put("native_port_verified_by_gameplay",false)''',"report native API")
file.write_text(s,encoding="utf-8")

# Hardware readiness explicitly includes Eden registered devices, not only
# Android hardware presence. Fallback JNI errors are diagnostics, not success.
p=d/"GameDeckHardwareAudit.kt";s=p.read_text(encoding="utf-8")
s=once(s,"import org.yuzu.yuzu_emu.utils.InputHandler",
'''import org.yuzu.yuzu_emu.utils.InputHandler
import org.yuzu.yuzu_emu.features.input.NativeInput''',"native input readiness")
s=once(s,r'''            append("Physische Gamepads: ").append(controllers.size).append("\\n")''',
'''            val nativeInputDevices=runCatching { NativeInput.getInputDevices().toList() }
            append("Eden registrierte Eingabegeräte: ")
                .append(nativeInputDevices.getOrNull()?.size ?: "NICHT LESBAR")
                .append("\\n")
            nativeInputDevices.getOrNull()?.take(8)?.forEach {
                append("  ↳ ").append(it.take(180)).append("\\n")
            }
            if(nativeInputDevices.isFailure) {
                append("Eden-Registrierung nicht lesbar: ")
                    .append(nativeInputDevices.exceptionOrNull()?.javaClass?.simpleName)
                    .append("\\n")
            }
            append("Physische Gamepads: ").append(controllers.size).append("\\n")''',"registered Eden device audit")
p.write_text(s,encoding="utf-8")

# Keep the automated tester's outcome evidence qualified. A virtual overlay
# can be injected, but mapping to a game is only proved via actual game response.
p=d/"GameDeckAutoTester.kt";s=p.read_text(encoding="utf-8")
s=once(s,'''                    append("  Input: ").append(ai.optString("controller_source","unbekannt"))''',
'''                    append("  Native Eingabe: ").append(ai.optString("input_abi","unbekannt"))
                        .append("\\n")
                    append("  Input: ").append(ai.optString("controller_source","unbekannt"))''',"report overlay correctness")
p.write_text(s,encoding="utf-8")

# Improve message at user entrypoint to reflect specific native input ABI fix.
p=d/"GameDeckActivity.kt";s=p.read_text(encoding="utf-8")
s=once(s,'''                    .setMessage("GameDeck sendet echte A/B/X-, Start-, D-Pad- und Analogstick-Eingaben an Eden.''',
'''                    .setMessage("GameDeck verwendet für physische Gamepads Edens Hardware-Input und für Bildschirmtasten Edens separate native Overlay-Schnittstelle. Die KI sendet echte A/B/X-, Start-, D-Pad- und Analogstick-Eingaben an Eden.''',"accurate consent text")
p.write_text(s,encoding="utf-8")

p=d/"GameDeckRuntime.kt";s=p.read_text(encoding="utf-8")
s=once(s,'const val BUILD = "GameDeck v26"','const val BUILD = "GameDeck v27"',"version")
p.write_text(s,encoding="utf-8")
p=d/"GameDeckUpdateChecker.kt";s=p.read_text(encoding="utf-8")
s=once(s,'private const val LOCAL_VERSION = 26','private const val LOCAL_VERSION = 27',"updater version")
p.write_text(s,encoding="utf-8")
print("GameDeck v27 – genuine native overlay button and stick ABI + input readiness")
