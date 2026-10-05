package de.ahnsen.kartentrainer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@Composable
fun PrecisionCardScannerButton(
    modifier: Modifier = Modifier,
    onText: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val options = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(9)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scanner = remember { GmsDocumentScanning.getClient(options) }

    DisposableEffect(Unit) {
        onDispose { recognizer.close() }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val pages = scanResult?.pages.orEmpty()
        if (pages.isEmpty()) {
            onError("Präzisionsscan lieferte keine Karte.")
        } else {
            pages.forEach { page ->
                runCatching {
                    InputImage.fromFilePath(context, page.imageUri)
                }.onSuccess { image ->
                    recognizer.process(image)
                        .addOnSuccessListener { text ->
                            if (text.text.isBlank()) {
                                onError("Auf einem korrigierten Scan wurde kein Text erkannt.")
                            } else {
                                onText(text.text)
                            }
                        }
                        .addOnFailureListener {
                            onError("OCR nach Präzisionsscan fehlgeschlagen: " + (it.message ?: ""))
                        }
                }.onFailure {
                    onError("Korrigierter Scan konnte nicht gelesen werden: " + (it.message ?: ""))
                }
            }
        }
    }

    Button(
        modifier = modifier.fillMaxWidth(),
        onClick = {
            val host = activity
            if (host == null) {
                onError("Scanner konnte die aktuelle Activity nicht ermitteln.")
                return@Button
            }
            scanner.getStartScanIntent(host)
                .addOnSuccessListener { intentSender ->
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener {
                    onError("Präzisionsscanner konnte nicht gestartet werden: " + (it.message ?: ""))
                }
        }
    ) {
        Icon(Icons.Default.DocumentScanner, contentDescription = null)
        Text(" Präzisionsscan mit Rand-/Perspektivkorrektur")
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
