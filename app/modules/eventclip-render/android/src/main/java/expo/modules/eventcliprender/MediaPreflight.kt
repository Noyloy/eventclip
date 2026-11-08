package expo.modules.eventcliprender

import android.content.Context
import android.media.MediaMetadataRetriever
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Local preflight before compose — existence/size/hash + best-effort metadata.
 * Not a mobile performance proxy; informs Asset-loader diagnosis only.
 */
object MediaPreflight {
  data class Item(
    val role: String,
    val kind: String, // image|video|audio
    val file: File,
  )

  fun inspect(items: List<Item>): Map<String, Any?> {
    val results = items.map { inspectOne(it) }
    val allOk = results.all { it["ok"] == true }
    return linkedMapOf(
      "ok" to allOk,
      "itemCount" to items.size,
      "items" to results,
      "fixturePackVersionExpected" to FixtureGenerator.PACK_VERSION,
    )
  }

  fun inspectOne(item: Item): Map<String, Any?> {
    val meta = ExportErrorDiagnostics.privacySafeFileMeta(item.file).toMutableMap()
    meta["role"] = item.role
    meta["kind"] = item.kind
    if (item.file.exists() && item.file.length() > 0) {
      meta["magic"] = magicLabel(item.file)
      meta.putAll(retrieverFields(item.file, item.kind))
      meta["ok"] = magicLooksPlausible(item.kind, meta["magic"] as? String)
    } else {
      meta["ok"] = false
      meta["reason"] = "missing_or_empty"
    }
    return meta
  }

  fun magicLabel(file: File): String {
    val b = file.readBytes().take(12).toByteArray()
    return when {
      b.size >= 8 &&
        b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte() ->
        "png"
      b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> "jpeg"
      b.size >= 12 && String(b.copyOfRange(4, 8)) == "ftyp" -> "mp4_ftyp"
      b.size >= 3 && String(b.copyOfRange(0, 3)) == "ID3" -> "mp3_id3"
      b.size >= 2 && (b[0] == 0xFF.toByte() && (b[1].toInt() and 0xE0) == 0xE0) -> "mp3_frame"
      b.size >= 4 && String(b.copyOfRange(0, 4)) == "RIFF" -> "wav_riff"
      else -> "unknown"
    }
  }

  fun magicLooksPlausible(kind: String, magic: String?): Boolean =
    when (kind) {
      "image" -> magic == "png" || magic == "jpeg"
      "video" -> magic == "mp4_ftyp"
      "audio" -> magic == "mp3_id3" || magic == "mp3_frame" || magic == "wav_riff"
      else -> false
    }

  private fun retrieverFields(file: File, kind: String): Map<String, Any?> {
    if (kind == "image") {
      return mapOf(
        "guessMime" to
          (MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
            ?: if (file.extension == "png") "image/png" else "image/jpeg"),
      )
    }
    val mmr = MediaMetadataRetriever()
    return try {
      mmr.setDataSource(file.absolutePath)
      mapOf(
        "durationMs" to mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
        "width" to mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
        "height" to mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
        "hasAudio" to
          (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"),
        "hasVideo" to
          (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"),
        "mimeFromRetriever" to mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
      )
    } catch (e: Exception) {
      mapOf(
        "retrieverError" to ExportErrorDiagnostics.sanitizeMessage(e.message),
        "retrieverErrorType" to e.javaClass.simpleName,
      )
    } finally {
      try {
        mmr.release()
      } catch (_: Throwable) {
      }
    }
  }
}
