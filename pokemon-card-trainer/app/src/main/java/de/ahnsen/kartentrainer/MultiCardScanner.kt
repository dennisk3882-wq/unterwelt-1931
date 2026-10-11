package de.ahnsen.kartentrainer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private data class MultiCardReview(
    val number: Int,
    val crop: Bitmap,
    val certain: Boolean,
    val name: String = "",
    val localId: String = "",
    val matches: List<CardBrief> = emptyList(),
    val chosenId: String? = null,
    val variant: String = "Normal",
    val note: String = "Wird eingelesen …",
    val saved: Boolean = false
)

private suspend fun <T> Task<T>.awaitScanner(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

private suspend fun readFullPhoto(context: Context, uri: Uri): Bitmap =
    withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Bilddatei konnte nicht geöffnet werden." }
            BitmapFactory.decodeStream(input, null, options)
        }
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 4600) sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Fotodatei ist nicht mehr verfügbar." }
            BitmapFactory.decodeStream(input, null, options) ?: error("Fotoformat konnte nicht gelesen werden.")
        }
    }

private fun drawPhotoReview(photo: Bitmap, boxes: List<MultiCardDetector.CardBox>): Bitmap {
    val result = photo.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(5f, photo.width / 180f)
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = maxOf(28f, photo.width / 30f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    boxes.forEachIndexed { index, item ->
        border.color = if (item.certain) android.graphics.Color.rgb(34, 180, 75) else android.graphics.Color.rgb(250, 155, 5)
        canvas.drawRect(item.rect, border)
        canvas.drawText((index + 1).toString(), item.rect.left.toFloat() + 6f,
            item.rect.top.toFloat() + textPaint.textSize, textPaint)
    }
    return result
}

@Composable
internal fun MultiCardPhotoScanner(
    repository: TcgDexRepository,
    onAdd: (CardData, String, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val reviews = remember { mutableStateListOf<MultiCardReview>() }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf("Bis zu 20 Karten nebeneinander fotografieren. Einzelne Treffer danach prüfen.") }
    var progress by remember { mutableStateOf("") }
    val photoFile = remember { File.createTempFile("karten_gesamt_", ".jpg", File(context.cacheDir, "photos").apply { mkdirs() }) }
    val photoUri = remember {
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", photoFile)
    }

    DisposableEffect(Unit) { onDispose { recognizer.close() } }

    fun analyze(uri: Uri) {
        scope.launch {
            if (busy) return@launch
            busy = true
            reviews.clear()
            preview = null
            state = "Karten werden im Foto gesucht …"
            try {
                val photo = readFullPhoto(context, uri)
                val boxes = withContext(Dispatchers.Default) { MultiCardDetector.detect(photo) }
                if (boxes.isEmpty()) {
                    state = "Keine vollständigen Kartenrahmen erkannt. Bitte Foto mit Abstand und guter Beleuchtung wiederholen."
                    return@launch
                }
                val previews = withContext(Dispatchers.Default) {
                    drawPhotoReview(photo, boxes)
                }
                preview = previews
                boxes.forEachIndexed { index, box ->
                    val r: Rect = box.rect
                    val cut = Bitmap.createBitmap(photo, r.left, r.top, r.width(), r.height())
                    reviews.add(MultiCardReview(index + 1, cut, box.certain))
                }
                state = boxes.size.toString() + " Kartenpositionen gefunden. Prüfe jedes Kartenbild!"
                for (index in reviews.indices) {
                    progress = "Karte " + (index + 1) + " von " + reviews.size + " wird erkannt …"
                    val original = reviews[index]
                    try {
                        val ocr = recognizer.process(InputImage.fromBitmap(original.crop, 0)).awaitScanner()
                        val guess = OcrParser.parse(ocr.text)
                        val found = if (ocr.text.isNotBlank()) {
                            runCatching { repository.resolveScannedCards(ocr.text) }.getOrDefault(emptyList())
                        } else emptyList()
                        reviews[index] = original.copy(
                            name = guess.name,
                            localId = guess.localId.orEmpty(),
                            matches = found.take(6),
                            chosenId = found.singleOrNull()?.id,
                            note = when {
                                ocr.text.isBlank() -> "Text unleserlich – Namen manuell eingeben."
                                found.size == 1 -> "Treffer gefunden – vor dem Speichern prüfen."
                                found.isEmpty() -> "Kein eindeutiger Treffer – Namen korrigieren."
                                else -> "Bitte passende Karte aus den Treffern wählen."
                            }
                        )
                    } catch (error: Exception) {
                        reviews[index] = original.copy(note = "Scan unsicher – Namen eingeben.")
                    }
                }
                state = "Alle Karten geprüft? Dann unten auf „Ausgewählte Karten speichern“ tippen."
            } catch (error: Exception) {
                state = "Fotoverarbeitung fehlgeschlagen: " + (error.message ?: "unbekannter Fehler")
            } finally {
                progress = ""
                busy = false
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) analyze(photoUri) else state = "Kameraaufnahme abgebrochen."
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) analyze(uri)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KidPalette.SoftYellow)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("📸 Mehrere Karten auf EINEM Foto", fontWeight = FontWeight.ExtraBold)
            Text("Lege deine Karten wie auf einem Tisch nebeneinander. Ein Foto reicht – die App trennt sie in einzelne Karten.")
            Button(
                enabled = !busy,
                onClick = { camera.launch(photoUri) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("📷 Mehrfach-Foto aufnehmen") }
            OutlinedButton(
                enabled = !busy,
                onClick = { gallery.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🖼 Foto aus Galerie auswählen") }
            if (busy) {
                CircularProgressIndicator()
                if (progress.isNotBlank()) Text(progress)
            }
            Text(state)
            preview?.let { p ->
                Text("Erkannte Karten: grün = automatisch, orange = bitte prüfen.")
                Image(
                    bitmap = p.asImageBitmap(),
                    contentDescription = "Foto mit nummerierten Kartenrahmen",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            reviews.forEachIndexed { index, review ->
                OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            "Karte " + review.number + if (review.certain) " · erkannt" else " · Rahmen prüfen",
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            Image(
                                bitmap = review.crop.asImageBitmap(),
                                contentDescription = "Kartenausschnitt " + review.number,
                                modifier = Modifier.size(width = 92.dp, height = 126.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(review.note)
                                if (review.saved) Text("✓ In Meine Karten gespeichert")
                            }
                        }
                        if (!review.saved) {
                            OutlinedTextField(
                                value = review.name,
                                onValueChange = { value ->
                                    reviews[index] = reviews[index].copy(name = value, chosenId = null)
                                },
                                label = { Text("Kartenname korrigieren") },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = review.localId,
                                onValueChange = { value ->
                                    reviews[index] = reviews[index].copy(localId = value, chosenId = null)
                                },
                                label = { Text("Kartennummer (optional)") },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                enabled = !busy && (review.name.isNotBlank() || review.localId.isNotBlank()),
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            val matches = repository.searchCards(review.name, review.localId.takeIf { it.isNotBlank() })
                                            reviews[index] = reviews[index].copy(
                                                matches = matches.take(6),
                                                chosenId = matches.singleOrNull()?.id,
                                                note = if (matches.isEmpty()) "Nichts gefunden. Bitte Eingabe prüfen." else "Treffer auswählen."
                                            )
                                        } catch (e: Exception) {
                                            state = "Suche fehlgeschlagen: " + (e.message ?: "Netzwerkproblem")
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            ) { Text("🔎 Karte suchen") }

                            review.matches.forEach { candidate ->
                                OutlinedCard(
                                    onClick = { reviews[index] = reviews[index].copy(chosenId = candidate.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(7.dp)) {
                                        AsyncImage(
                                            model = candidate.imageUrl,
                                            contentDescription = candidate.name,
                                            modifier = Modifier.size(width = 52.dp, height = 74.dp)
                                        )
                                        Spacer(Modifier.width(9.dp))
                                        Column {
                                            Text((if (review.chosenId == candidate.id) "✓ " else "○ ") + candidate.name,
                                                fontWeight = if (review.chosenId == candidate.id) FontWeight.Bold else FontWeight.Normal)
                                            Text(candidate.localId + " · " + candidate.id)
                                        }
                                    }
                                }
                            }
                            if (review.chosenId != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    listOf("Normal", "Reverse", "Holo").forEach { variant ->
                                        FilterChip(
                                            selected = review.variant == variant,
                                            onClick = { reviews[index] = reviews[index].copy(variant = variant) },
                                            label = { Text(variant) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (reviews.isNotEmpty()) {
                val pending = reviews.count { it.chosenId != null && !it.saved }
                Text(pending.toString() + " Karten zum Speichern ausgewählt.")
                Button(
                    enabled = pending > 0 && !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            busy = true
                            var savedCount = 0
                            var failedCount = 0
                            try {
                                for (index in reviews.indices) {
                                    val current = reviews[index]
                                    val chosen = current.chosenId ?: continue
                                    if (current.saved) continue
                                    progress = "Speichere Karte " + current.number + " …"
                                    try {
                                        val card = repository.getCard(chosen)
                                        onAdd(card, current.variant, System.currentTimeMillis())
                                        reviews[index] = current.copy(saved = true, note = "Sicher gespeichert")
                                        savedCount++
                                    } catch (error: Exception) {
                                        reviews[index] = current.copy(note = "Nicht gespeichert: " + (error.message ?: "Fehler"))
                                        failedCount++
                                    }
                                }
                                state = savedCount.toString() + " Karten in „Meine Karten“ gespeichert." +
                                    if (failedCount > 0) " " + failedCount + " Karten fehlgeschlagen, bitte erneut versuchen." else ""
                            } finally {
                                progress = ""
                                busy = false
                            }
                        }
                    }
                ) { Text("✓ Ausgewählte Karten speichern (" + pending + ")") }
            }
        }
    }
}
