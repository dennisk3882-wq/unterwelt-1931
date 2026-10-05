package de.ahnsen.kartentrainer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class ScannerOcrResult(
    val text: String,
    val confidence: Int,
    val blockCount: Int,
    val guess: OcrGuess,
    val guesses: List<OcrGuess>
)

@Composable
fun SmartCameraScanner(
    batchMode: Boolean,
    modifier: Modifier = Modifier,
    onResult: (ScannerOcrResult) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var status by remember { mutableStateOf("Karte vollständig in den Rahmen legen.") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (!granted) onError("Kameraberechtigung wurde nicht erteilt.")
    }

    val processing = remember { AtomicBoolean(false) }
    val lastAnalyzeAt = remember { AtomicLong(0L) }
    val lastSignature = remember { AtomicReference("") }

    fun processImage(proxy: ImageProxy, fromBatch: Boolean) {
        val mediaImage = proxy.image
        if (mediaImage == null) {
            proxy.close()
            processing.set(false)
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val text = result.text
                if (text.isBlank()) {
                    if (!fromBatch) mainHandler.post { onError("Kein lesbarer Kartentext erkannt.") }
                    return@addOnSuccessListener
                }
                val guess = OcrParser.parse(text)
                val collectorFound = !guess.localId.isNullOrBlank()
                val nameFound = guess.name.length >= 3
                val blocks = result.textBlocks.size
                val confidence = (
                    (if (collectorFound) 45 else 0) +
                        (if (nameFound) 35 else 0) +
                        (blocks.coerceAtMost(5) * 4)
                    ).coerceIn(0, 100)
                val signature = guess.name.lowercase() + "|" + guess.localId.orEmpty().lowercase()
                if (!fromBatch || (confidence >= 45 && signature.isNotBlank() && signature != lastSignature.get())) {
                    lastSignature.set(signature)
                    mainHandler.post {
                        status = "Erkannt: " + (guess.name.ifBlank { "Kartentext" }) + " · Sicherheit " + confidence + "%"
                        val blockGuesses = result.textBlocks
                            .map { OcrParser.parse(it.text) }
                            .filter { it.name.isNotBlank() || !it.localId.isNullOrBlank() }
                            .distinctBy { it.name.lowercase() + "|" + it.localId.orEmpty() }
                        onResult(
                            ScannerOcrResult(
                                text = text,
                                confidence = confidence,
                                blockCount = blocks,
                                guess = guess,
                                guesses = (listOf(guess) + blockGuesses)
                                    .filter { it.name.isNotBlank() || !it.localId.isNullOrBlank() }
                                    .distinctBy { it.name.lowercase() + "|" + it.localId.orEmpty() }
                            )
                        )
                    }
                }
            }
            .addOnFailureListener { error ->
                if (!fromBatch) {
                    mainHandler.post {
                        onError("Texterkennung fehlgeschlagen: " + (error.message ?: "unbekannter Fehler"))
                    }
                }
            }
            .addOnCompleteListener {
                proxy.close()
                processing.set(false)
            }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            runCatching { controller.bindToLifecycle(lifecycleOwner) }
                .onFailure { onError("Kamera konnte nicht gestartet werden: " + (it.message ?: "")) }
        }
    }

    DisposableEffect(batchMode, permissionGranted) {
        if (permissionGranted && batchMode) {
            controller.setImageAnalysisAnalyzer(executor) { proxy ->
                val now = System.currentTimeMillis()
                if (now - lastAnalyzeAt.get() < 1200 || !processing.compareAndSet(false, true)) {
                    proxy.close()
                } else {
                    lastAnalyzeAt.set(now)
                    processImage(proxy, true)
                }
            }
        } else {
            controller.clearImageAnalysisAnalyzer()
        }
        onDispose {
            controller.clearImageAnalysisAnalyzer()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.unbind()
            recognizer.close()
            executor.shutdown()
        }
    }

    Column(modifier = modifier) {
        if (!permissionGranted) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Kamera benötigt")
                    Text("Für den Karten-Scanner muss die Kamera freigegeben werden.")
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Text(" Kamera erlauben")
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp),
                contentAlignment = Alignment.Center
            ) {
                val cameraController = controller
                AndroidView(
                    modifier = Modifier.matchParentSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            this.controller = cameraController
                        }
                    },
                    update = { it.controller = cameraController }
                )
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(315.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CenterFocusStrong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                if (batchMode) {
                    "Batchmodus aktiv: Neue Karten werden automatisch erkannt. Nach einem Treffer einfach die nächste Karte in den Rahmen legen."
                } else status,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            if (!batchMode) {
                Button(
                    onClick = {
                        if (!processing.compareAndSet(false, true)) return@Button
                        status = "Foto wird analysiert …"
                        controller.takePicture(
                            executor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    processImage(image, false)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    processing.set(false)
                                    mainHandler.post {
                                        onError("Foto fehlgeschlagen: " + (exception.message ?: "unbekannter Fehler"))
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Text(" Karte hochauflösend scannen")
                }
            }
        }
    }
}
