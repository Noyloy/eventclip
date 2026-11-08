package expo.modules.eventcliprender

import androidx.media3.transformer.ExportException
import java.io.File
import java.security.MessageDigest

/**
 * Privacy-safe typed error info for Media3 export failures.
 * Never includes absolute private paths or user identifiers — basename + sha256 only.
 */
object ExportErrorDiagnostics {
  data class CauseFrame(
    val type: String,
    val message: String?,
  )

  fun fromThrowable(
    t: Throwable,
    phase: String,
    relatedFiles: List<File> = emptyList(),
  ): Map<String, Any?> {
    val export = t as? ExportException ?: findExportException(t)
    val causes = boundedCauseChain(t, max = 6)
    return linkedMapOf(
      "phase" to phase,
      "errorMessage" to sanitizeMessage(t.message),
      "errorType" to t.javaClass.simpleName,
      "media3ErrorCode" to export?.errorCode,
      "media3ErrorCodeName" to export?.errorCodeName,
      "causeChain" to
        causes.map {
          mapOf("type" to it.type, "message" to sanitizeMessage(it.message))
        },
      "fixtures" to relatedFiles.map { privacySafeFileMeta(it) },
      "note" to
        "Absolute paths redacted; basenames+sha256 only. Nested causes bounded.",
    )
  }

  fun privacySafeFileMeta(file: File): Map<String, Any?> {
    val exists = file.exists()
    val bytes = if (exists) file.length() else -1L
    val hash =
      if (exists && bytes in 1..8_000_000) sha256Hex(file.readBytes()) else null
    return linkedMapOf(
      "basename" to file.name,
      "exists" to exists,
      "bytes" to bytes,
      "sha256" to hash,
      "ext" to file.extension.lowercase(),
    )
  }

  fun sanitizeMessage(msg: String?): String? {
    if (msg == null) return null
    // Strip absolute app-private / sdcard paths while keeping filenames.
    return msg
      .replace(Regex("/data/user/\\d+/[^\\s:]+/"), "…/")
      .replace(Regex("/data/data/[^\\s:]+/"), "…/")
      .replace(Regex("/storage/[^\\s:]+/"), "…/")
      .replace(Regex("file://[^\\s]+/"), "file://…/")
      .take(500)
  }

  fun boundedCauseChain(t: Throwable, max: Int): List<CauseFrame> {
    val out = ArrayList<CauseFrame>()
    var cur: Throwable? = t
    var n = 0
    val seen = HashSet<Int>()
    while (cur != null && n < max) {
      val id = System.identityHashCode(cur)
      if (!seen.add(id)) break
      out.add(CauseFrame(cur.javaClass.name.substringAfterLast('.'), cur.message))
      cur = cur.cause
      n++
    }
    return out
  }

  private fun findExportException(t: Throwable): ExportException? {
    var cur: Throwable? = t
    var n = 0
    while (cur != null && n < 8) {
      if (cur is ExportException) return cur
      cur = cur.cause
      n++
    }
    return null
  }

  fun sha256Hex(data: ByteArray): String {
    val dig = MessageDigest.getInstance("SHA-256").digest(data)
    return dig.joinToString("") { "%02x".format(it) }
  }
}
