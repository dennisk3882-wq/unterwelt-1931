// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.gamedeck

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Locale

/** Bundled offline text recognition. Pixels are never stored or uploaded. */
object GameDeckSceneObserver {
    private val ui=Handler(Looper.getMainLooper())
    private val ocr by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var activity=WeakReference<Activity>(null)
    private var epoch=0
    private var busy=false
    private var lastCapture=0L
    private var captures=0
    private var recognized=0
    private var errors=0
    private var candidate="UNKNOWN"
    private var stable="UNKNOWN"
    private var consecutive=0
    private var maxLoading=0
    private var excerpt=""

    fun attach(screen:Activity) { activity=WeakReference(screen) }
    fun reset() {
        epoch++;activity.clear();busy=false;lastCapture=0L
        captures=0;recognized=0;errors=0;candidate="UNKNOWN"
        stable="UNKNOWN";consecutive=0;maxLoading=0;excerpt=""
    }
    fun detach() {epoch++;activity.clear();busy=false}

    private fun interpret(value:String):String {
        val txt=value.lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
        fun any(vararg keys:String)=keys.any {txt.contains(it)}
        return when {
            txt.isBlank() -> "UNKNOWN"
            any("failed to load","fatal error","file not found",
                "error occurred","could not start","fehlgeschlagen") -> "LOAD_ERROR"
            any("level complete","stage clear","mission complete",
                "you win","congratulations","victory","geschafft") -> "POSSIBLE_SUCCESS"
            any("game over","you died","mission failed","try again",
                "you lose","verloren") -> "GAME_OVER"
            any("loading","please wait","wird geladen","lade...") -> "LOADING"
            any("paused","resume game","pausiert","fortsetzen") -> "PAUSE"
            any("press start","press any button","press a","new game",
                "start game","neues spiel","continue","spiel starten") -> "MENU"
            any("tutorial","jump","use the stick","move with",
                "press b","steuerung","springen") -> "TUTORIAL"
            else -> "UNCLASSIFIED_TEXT"
        }
    }

    fun captureIfDue(){
        if(busy)return
        val screen=activity.get()?:return
        if(screen.isFinishing||screen.isDestroyed||
           !screen.window.decorView.isAttachedToWindow)return
        val now=SystemClock.elapsedRealtime()
        if(now-lastCapture<7200L)return
        lastCapture=now;busy=true
        val token=epoch
        val bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(screen.window,bitmap,{result ->
                if(token!=epoch){bitmap.recycle();return@request}
                if(result!=PixelCopy.SUCCESS){
                    errors++;bitmap.recycle();busy=false;return@request
                }
                val task=runCatching {
                    ocr.process(InputImage.fromBitmap(bitmap,0))
                }.getOrElse {
                    errors++;bitmap.recycle();busy=false;return@request
                }
                task.addOnSuccessListener { resultText ->
                    if(token!=epoch)return@addOnSuccessListener
                    captures++
                    val found=resultText.text.orEmpty()
                    if(found.isNotBlank())recognized++
                    val next=interpret(found)
                    if(next==candidate)consecutive++ else{
                        candidate=next;consecutive=1
                    }
                    // Two consecutive recognized frames are required.
                    stable=if(consecutive>=2)candidate else "UNKNOWN"
                    if(stable=="LOADING")maxLoading=maxOf(maxLoading,consecutive)
                    excerpt=found.replace(Regex("\\s+")," ").take(120)
                }.addOnFailureListener {
                    if(token==epoch)errors++
                }.addOnCompleteListener {
                    bitmap.recycle()
                    if(token==epoch)busy=false
                }
            },ui)
        }catch(_:Throwable){errors++;bitmap.recycle();busy=false}
    }

    fun snapshot():JSONObject=JSONObject()
        .put("scene",stable).put("candidate",candidate)
        .put("consecutive_matches",consecutive)
        .put("longest_loading_sequence",maxLoading)
        .put("captures",captures).put("frames_with_text",recognized)
        .put("errors",errors).put("excerpt",excerpt)
        .put("level_completed_proven",false)
        .put("source","Bundled on-device OCR of PixelCopy window")
}
