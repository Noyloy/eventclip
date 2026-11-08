package expo.modules.eventcliprender

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MediaPreflightMagicTest {
  @Test
  fun detectsPngJpegMp4WavMp3() {
    val dir = createTempDir()
    fun write(name: String, bytes: ByteArray): File {
      val f = File(dir, name)
      f.writeBytes(bytes)
      return f
    }
    val png =
      write(
        "a.png",
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0),
      )
    val jpg = write("a.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(8))
    val mp4 = write("a.mp4", ByteArray(4) + "ftyp".toByteArray() + ByteArray(4))
    val wav = write("a.wav", "RIFF".toByteArray() + ByteArray(8))
    val mp3 = write("a.mp3", "ID3".toByteArray() + ByteArray(9))

    assertEquals("png", MediaPreflight.magicLabel(png))
    assertEquals("jpeg", MediaPreflight.magicLabel(jpg))
    assertEquals("mp4_ftyp", MediaPreflight.magicLabel(mp4))
    assertEquals("wav_riff", MediaPreflight.magicLabel(wav))
    assertEquals("mp3_id3", MediaPreflight.magicLabel(mp3))

    assertTrue(MediaPreflight.magicLooksPlausible("image", "png"))
    assertTrue(MediaPreflight.magicLooksPlausible("video", "mp4_ftyp"))
    assertTrue(MediaPreflight.magicLooksPlausible("audio", "wav_riff"))
    assertFalse(MediaPreflight.magicLooksPlausible("image", "mp4_ftyp"))
    dir.deleteRecursively()
  }

  @Test
  fun seedAssetsOnDisk_matchPackVersionExpectations() {
    val seed =
      File(
        System.getenv("EVENTCLIP_LEGACY_SEED_DIR") ?: "app/android/app/src/main/assets/eventclip-seed",
      )
    if (!seed.exists()) return // host-only path; skip if absent
    assertEquals(FixtureGenerator.PACK_VERSION.toString(), File(seed, "PACK_VERSION").readText().trim())
    for (i in 1..6) {
      val f = File(seed, "small/img_%02d.png".format(i))
      assertTrue(f.name, f.exists())
      assertEquals("png", MediaPreflight.magicLabel(f))
    }
    for (i in 1..3) {
      val f = File(seed, "small/clip_%02d.mp4".format(i))
      assertTrue(f.exists())
      assertEquals("mp4_ftyp", MediaPreflight.magicLabel(f))
    }
    assertEquals("wav_riff", MediaPreflight.magicLabel(File(seed, "audio/metronome_120bpm_8s.wav")))
    assertEquals("mp3_id3", MediaPreflight.magicLabel(File(seed, "audio/metronome_120bpm_8s.mp3")))
  }
}
