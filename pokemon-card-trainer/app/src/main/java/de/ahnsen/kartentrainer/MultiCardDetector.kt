package de.ahnsen.kartentrainer

import android.graphics.Bitmap
import android.graphics.Rect
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Card-sized contour clustering in LAB color space. Works offline on an arbitrary photo:
 * wood, textile and tabletop backgrounds. Geometrically proposed slots are marked
 * uncertain and are NEVER added to the collection without the user's confirmation.
 */
internal object MultiCardDetector {
    data class CardBox(val rect: Rect, val certain: Boolean, val votes: Int)
    private data class Candidate(val x: Int, val y: Int, val w: Int, val h: Int) {
        val cx get() = x + w / 2
        val cy get() = y + h / 2
    }
    private data class Cluster(val items: MutableList<Candidate>, var c: Candidate)
    private data class Result(val box: Candidate, val votes: Int, val source: String) {
        val cx get() = box.cx
        val cy get() = box.cy
    }
    private fun median(nums: List<Int>): Int =
        nums.sorted().let { it[it.size / 2] }

    fun detect(photo: Bitmap): List<CardBox> {
        require(OpenCVLoader.initLocal()) { "OpenCV konnte nicht geladen werden." }
        val targetW = 576
        val targetH = (photo.height.toFloat() / photo.width * targetW).roundToInt()
        val rgba = Mat()
        val resized = Mat()
        val rgb = Mat()
        val lab = Mat()
        val mask = Mat(targetH, targetW, CvType.CV_8UC1)
        val closed = Mat()
        val hierarchy = Mat()
        val kernels = listOf(3, 5, 7, 11).map { k ->
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(k.toDouble(), k.toDouble()))
        }
        try {
            Utils.bitmapToMat(photo, rgba)
            Imgproc.resize(rgba, resized, Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.cvtColor(resized, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
            val pixels = ByteArray(targetW * targetH * 3)
            lab.get(0, 0, pixels)
            val chroma = IntArray(targetW * targetH)
            val lights = IntArray(targetW * targetH)
            for (i in chroma.indices) {
                val p = i * 3
                lights[i] = pixels[p].toInt() and 255
                val a = (pixels[p + 1].toInt() and 255) - 142
                val b = (pixels[p + 2].toInt() and 255) - 147
                chroma[i] = a * a + b * b
            }
            val maskBytes = ByteArray(chroma.size)
            val raw = mutableListOf<Candidate>()
            for (distance in listOf(12, 16, 20, 24, 28, 32)) {
                for (lum in listOf(65, 80, 100, 115, 130)) {
                    val threshold = distance * distance
                    for (i in maskBytes.indices) {
                        maskBytes[i] = if (chroma[i] > threshold || lights[i] < lum) 255.toByte() else 0
                    }
                    mask.put(0, 0, maskBytes)
                    for (kernel in kernels) {
                        Imgproc.morphologyEx(mask, closed, Imgproc.MORPH_CLOSE, kernel)
                        val contours = ArrayList<MatOfPoint>()
                        Imgproc.findContours(closed.clone(), contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
                        for (contour in contours) {
                            val r = Imgproc.boundingRect(contour)
                            val area = Imgproc.contourArea(contour)
                            val ratio = r.width.toDouble() / r.height.coerceAtLeast(1)
                            val coverage = area / (r.width * r.height).coerceAtLeast(1)
                            if (area > 3800 && area < 30000 &&
                                r.width in 76..149 && r.height in 101..189 &&
                                ratio > 0.55 && ratio < 0.9 &&
                                coverage > 0.5 && coverage < 1.05
                            ) raw += Candidate(r.x, r.y, r.width, r.height)
                            contour.release()
                        }
                    }
                }
            }
            val clusters = mutableListOf<Cluster>()
            for (c in raw) {
                val cluster = clusters.firstOrNull { abs(it.c.cx - c.cx) < 20 && abs(it.c.cy - c.cy) < 20 }
                if (cluster == null) clusters += Cluster(mutableListOf(c), c)
                else {
                    cluster.items += c
                    cluster.c = Candidate(
                        median(cluster.items.map { it.x }),
                        median(cluster.items.map { it.y }),
                        median(cluster.items.map { it.w }),
                        median(cluster.items.map { it.h })
                    )
                }
            }
            val strong = mutableListOf<Result>()
            for (cl in clusters.sortedByDescending { it.items.size }) {
                if (cl.items.size < 15) continue
                val item = Result(cl.c, cl.items.size, "contour")
                if (strong.none { abs(it.cx - item.cx) <= 45 && abs(it.cy - item.cy) <= 45 }) strong += item
            }
            val weak = clusters.filter { it.items.size in 4..14 }.map { Result(it.c, it.items.size, "uncertain") }
                .filter { w -> strong.none { abs(it.cx - w.cx) <= 55 && abs(it.cy - w.cy) <= 55 } }
            val rows = mutableListOf<MutableList<Result>>()
            for (card in strong.sortedBy { it.cy }) {
                val row = rows.firstOrNull { abs(median(it.map { c -> c.cy }) - card.cy) < 75 }
                if (row != null) row += card else rows += mutableListOf(card)
            }
            for (row in rows) {
                if (row.size < 3) continue
                val rowY = median(row.map { it.cy })
                for (w in weak) {
                    if (abs(w.cy - rowY) < 65 && w.cy > 90 && w.box.y > 70 &&
                        row.none { abs(it.cx - w.cx) <= 80 } &&
                        w.cx >= row.minOf { it.cx } - 220 &&
                        w.cx <= row.maxOf { it.cx } + 220
                    ) {
                        val h = median(row.map { it.box.h })
                        val fixed = if (w.box.h < 140) {
                            val y = (w.box.y - 12).coerceAtLeast(0)
                            Result(Candidate(w.box.x, y, w.box.w, h.coerceAtMost(targetH - y)), w.votes, w.source)
                        } else w
                        row += fixed
                    }
                }
            }
            val boxes = rows.flatMap { it }.distinctBy { it.box }
                .toMutableList()
            val typicalW = if (strong.isNotEmpty()) median(strong.map { it.box.w }) else 110
            for (row in rows) {
                if (row.size != 2 || rows.size < 2) continue
                val sorted = row.sortedBy { it.cx }
                val a = sorted.first()
                val b = sorted.last()
                val gap = b.box.x - (a.box.x + a.box.w)
                if (gap > 1.05 * typicalW && gap < 2.4 * typicalW) {
                    val mid = (a.box.x + a.box.w + b.box.x) / 2
                    val h = median(row.map { it.box.h })
                    val x = mid - typicalW / 2
                    val y = median(row.map { it.cy }) - h / 2
                    val proposal = Result(Candidate(x, y, typicalW, h), 0, "grid")
                    if (boxes.none { abs(it.cx - proposal.cx) <= 60 && abs(it.cy - proposal.cy) <= 45 }) boxes += proposal
                }
            }
            val scaleX = photo.width.toFloat() / targetW
            val scaleY = photo.height.toFloat() / targetH
            return boxes
                .filter { it.box.x >= 0 && it.box.y > 5 &&
                    it.box.x + it.box.w < targetW && it.box.y + it.box.h < targetH }
                .sortedWith(compareBy<Result> { (it.cy / 150) }.thenBy { it.cx })
                .take(20)
                .map { b ->
                    val x = (b.box.x * scaleX).roundToInt().coerceIn(0, photo.width - 1)
                    val y = (b.box.y * scaleY).roundToInt().coerceIn(0, photo.height - 1)
                    val right = ((b.box.x + b.box.w) * scaleX).roundToInt().coerceIn(x + 1, photo.width)
                    val bottom = ((b.box.y + b.box.h) * scaleY).roundToInt().coerceIn(y + 1, photo.height)
                    CardBox(Rect(x, y, right, bottom), b.source == "contour", b.votes)
                }
        } finally {
            kernels.forEach { it.release() }
            rgba.release(); resized.release(); rgb.release(); lab.release()
            mask.release(); closed.release(); hierarchy.release()
        }
    }
}
