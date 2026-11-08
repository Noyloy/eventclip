package expo.modules.eventcliprender

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ExportErrorDiagnosticsTest {
  @Test
  fun sanitizeRedactsAbsolutePaths() {
    val msg = "failed file:///data/user/0/com.eventclip.spike/files/eventclip/fixtures/small/img_01.png"
    val s = ExportErrorDiagnostics.sanitizeMessage(msg)!!
    assertFalse(s.contains("/data/user/0"))
    assertTrue(s.contains("img_01.png") || s.contains("…/"))
  }

  @Test
  fun boundedCauseChainStops() {
    val root = RuntimeException("root")
    var cur: Throwable = root
    for (i in 0 until 20) {
      val n = RuntimeException("c$i")
      cur.initCause(n)
      cur = n
    }
    // rebuild proper chain
    val e1 = RuntimeException("e1")
    val e0 = RuntimeException("e0", e1)
    val frames = ExportErrorDiagnostics.boundedCauseChain(e0, max = 2)
    assertEquals(2, frames.size)
  }

  @Test
  fun privacySafeFileMetaUsesBasenameAndHash() {
    val f = File.createTempFile("fixture", ".png")
    f.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    val meta = ExportErrorDiagnostics.privacySafeFileMeta(f)
    assertEquals(f.name, meta["basename"])
    assertEquals(true, meta["exists"])
    assertNotNull(meta["sha256"])
    assertFalse(meta.values.any { it is String && it.startsWith("/tmp") })
    f.delete()
  }
}
