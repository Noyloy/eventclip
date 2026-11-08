package expo.modules.eventcliprender
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
class DiagnosticWavTest {
  @Test fun matched20AndFull300WavHaveExactPcmDurationsAndNonzeroSamples() {
    for (seconds in listOf(20.0, 300.0)) {
      val f = File.createTempFile("eventclip-diagnostic-", ".wav")
      try {
        FixtureGenerator.writeMetronomeWav(f, seconds, 120.0)
        val bytes = f.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(22050, bb.getInt(24))
        assertEquals((seconds * 22050 * 2).toInt(), bb.getInt(40))
        assertEquals(44L + (seconds * 22050 * 2).toLong(), f.length())
        assertTrue(bytes.drop(44).any { it != 0.toByte() })
      } finally { f.delete() }
    }
  }
}
