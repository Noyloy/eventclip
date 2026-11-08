package expo.modules.eventcliprender

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/** Actual finalized MP4 sample metadata, NOT a decoder test or live encoder probe. */
object OutputTrackAudit {
  fun inspect(file: File, expectedSeconds: Double = 20.0, expectedWidth: Int = 720, expectedHeight: Int = 1280, fps: Int = 30): Map<String, Any?> {
    val tracks = ArrayList<Map<String, Any?>>()
    var audioValid = false
    var videoValid = false
    try {
      val probe = MediaExtractor()
      val count = try { probe.setDataSource(file.absolutePath); probe.trackCount } finally { probe.release() }
      for (i in 0 until count) {
        val ex = MediaExtractor()
        try {
          ex.setDataSource(file.absolutePath)
          val f = ex.getTrackFormat(i)
          val mime = f.getString(MediaFormat.KEY_MIME) ?: "unknown"
          ex.selectTrack(i)
          var n = 0
          var first = -1L
          var last = -1L
          var monotonic = true
          var maxGapUs = 0L
          var duplicatePts = 0
          while (ex.sampleTime >= 0L && n < 50000) {
            val pts = ex.sampleTime
            if (n == 0) first = pts
            if (last > pts) monotonic = false
            if (last >= 0) {
              maxGapUs = maxOf(maxGapUs, pts - last)
              if (pts == last) duplicatePts++
            }
            last = pts
            n++
            if (!ex.advance()) break
          }
          val duration = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else -1L
          val w = if (f.containsKey(MediaFormat.KEY_WIDTH)) f.getInteger(MediaFormat.KEY_WIDTH) else null
          val h = if (f.containsKey(MediaFormat.KEY_HEIGHT)) f.getInteger(MediaFormat.KEY_HEIGHT) else null
          val rotation = if (f.containsKey("rotation-degrees")) f.getInteger("rotation-degrees") else 0
          val displayW = if (rotation % 180 != 0) h else w
          val displayH = if (rotation % 180 != 0) w else h
          tracks.add(mapOf("mime" to mime, "samples" to n, "firstPtsUs" to first,
            "lastPtsUs" to last, "monotonicPts" to monotonic, "durationUs" to duration,
            "encodedWidth" to w, "encodedHeight" to h, "rotationDegrees" to rotation,
            "displayWidth" to displayW, "displayHeight" to displayH,
            "scanCapped" to (n >= 50000), "maxSampleGapUs" to maxGapUs, "duplicatePtsCount" to duplicatePts))
          val durationValid = kotlin.math.abs(duration - expectedSeconds * 1000000.0) <= 200000.0 && first in 0L..100000L && n < 50000
          if (mime.startsWith("audio/")) audioValid = durationValid && n > 0 && monotonic && maxGapUs <= 100000L
          if (mime.startsWith("video/")) videoValid = durationValid && n == (expectedSeconds * fps).toInt() && monotonic && duplicatePts == 0 && maxGapUs <= (1000000L / fps + 2L) && displayW == expectedWidth && displayH == expectedHeight
        } finally { ex.release() }
      }
      return mapOf("tracks" to tracks, "bytes" to file.length(), "intendedTracksValid" to (audioValid && videoValid), "audioValid" to audioValid, "videoValid" to videoValid, "expectedDurationSec" to expectedSeconds, "expectedVideoFrames" to (expectedSeconds * fps).toInt(), "expectedWidth" to expectedWidth, "expectedHeight" to expectedHeight,
        "decodeVerified" to false, "note" to "Finalized MP4 sample metadata only; not decoded-frame or audio waveform verification")
    } catch (e: Exception) {
      return mapOf("intendedTracksValid" to false, "errorType" to e.javaClass.simpleName,
        "decodeVerified" to false, "tracks" to tracks)
    }
  }
}
