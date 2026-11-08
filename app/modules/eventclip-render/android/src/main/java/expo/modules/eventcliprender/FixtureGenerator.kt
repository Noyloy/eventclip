package expo.modules.eventcliprender

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/**
 * Builds synthetic fixtures under app filesDir.
 * Pack version [PACK_VERSION] invalidates cached copies when seed format changes.
 * Durations for the "full" pack are ARTIFICIAL: 60×4.7s + 15×1.2s = 300s.
 */
object FixtureGenerator {
  const val PACK_VERSION = 4
  const val FULL_IMAGE_DUR = 4.7
  const val FULL_CLIP_DUR = 1.2
  const val FULL_TARGET_SEC = 300.0

  data class PackInfo(
    val root: String,
    val smallReady: Boolean,
    val fullReady: Boolean,
    val labels: Map<String, Any?>,
  )

  fun ensurePack(context: Context, includeFull: Boolean, onProgress: (String) -> Unit = {}): PackInfo {
    val fix = SpikePaths.fixtures(context)
    invalidateIfStale(fix, onProgress)

    onProgress("copying_seed_assets")
    copyAssetDir(context, "eventclip-seed/small", File(fix, "small"), overwrite = true)
    copyAssetFile(context, "eventclip-seed/audio/metronome_120bpm_8s.mp3", File(fix, "audio/metronome_120bpm_8s.mp3"), overwrite = true)
    // WAV preferred for compose song (PCM); MP3 kept for beats parity with prior runs
    copyAssetFile(context, "eventclip-seed/audio/metronome_120bpm_8s.wav", File(fix, "audio/metronome_120bpm_8s.wav"), overwrite = true)
    copyAssetFile(context, "eventclip-seed/clip_template.mp4", File(fix, "clip_template.mp4"), overwrite = true)

    // Remove stale JPEG seed copies if present from pack v1/v2
    File(fix, "small").listFiles()?.filter { it.extension.equals("jpg", true) }?.forEach { it.delete() }

    if (includeFull) {
      onProgress("generating_full_images")
      val images = File(fix, "images").also { it.mkdirs() }
      // Drop old jpg full images when migrating
      images.listFiles()?.filter { it.extension.equals("jpg", true) }?.forEach { it.delete() }
      for (i in 1..60) {
        val id = "%02d".format(i)
        val out = File(images, "photo_$id.png")
        if (!out.exists() || out.length() < 100) {
          writeSolidPng(out, Color.rgb(40 + (i * 3) % 180, 80 + (i * 5) % 150, 100 + (i * 7) % 120), 720, 1280)
        }
      }
      onProgress("duplicating_clip_templates")
      val clips = File(fix, "clips").also { it.mkdirs() }
      val template = File(fix, "clip_template.mp4")
      for (i in 1..15) {
        val id = "%02d".format(i)
        val dest = File(clips, "clip_$id.mp4")
        template.copyTo(dest, overwrite = true)
      }
      onProgress("generating_300s_metronome_wav")
      val song = File(fix, "audio/metronome_120bpm_300s.wav")
      if (!song.exists() || song.length() < 1000) {
        writeMetronomeWav(song, durationSec = 300.0, bpm = 120.0)
      }
    }

    val matchedSong = File(fix, "audio/metronome_120bpm_20s.wav")
    if (!matchedSong.exists()) writeMetronomeWav(matchedSong, 20.0, 120.0)
    writePackVersion(fix)
    val smallReady =
      (1..6).all { File(fix, "small/img_%02d.png".format(it)).exists() } &&
        (1..3).all { File(fix, "small/clip_%02d.mp4".format(it)).exists() }
    val fullReady =
      File(fix, "images/photo_01.png").exists() && File(fix, "audio/metronome_120bpm_300s.wav").exists()

    return PackInfo(
      root = fix.absolutePath,
      smallReady = smallReady,
      fullReady = fullReady,
      labels =
        mapOf(
          "synthetic" to true,
          "personalMedia" to false,
          "fixturePackVersion" to PACK_VERSION,
          "imageFormat" to "png",
          "fullTimelineNote" to
            "ARTIFICIAL durations: images held ${FULL_IMAGE_DUR}s each; clip files are short ~1.2s duplicates of one template, scheduled for ${FULL_CLIP_DUR}s; timeline target ${FULL_TARGET_SEC}s",
          "fullImageDurSec" to FULL_IMAGE_DUR,
          "fullClipDurSec" to FULL_CLIP_DUR,
          "fullClipFileContent" to "duplicate_of_seed_clip_template",
        ),
    )
  }

  private fun invalidateIfStale(fix: File, onProgress: (String) -> Unit) {
    val marker = File(fix, "PACK_VERSION")
    val current = if (marker.exists()) marker.readText().trim().toIntOrNull() else null
    if (current == PACK_VERSION) return
    onProgress("invalidating_fixture_pack_v${current ?: 0}_to_v$PACK_VERSION")
    if (fix.exists()) {
      fix.deleteRecursively()
    }
    fix.mkdirs()
  }

  private fun writePackVersion(fix: File) {
    File(fix, "PACK_VERSION").writeText(PACK_VERSION.toString())
  }

  private fun copyAssetDir(context: Context, assetDir: String, destDir: File, overwrite: Boolean = false) {
    destDir.mkdirs()
    val names = context.assets.list(assetDir) ?: return
    for (name in names) {
      val assetPath = "$assetDir/$name"
      val children = context.assets.list(assetPath)
      if (children != null && children.isNotEmpty()) {
        copyAssetDir(context, assetPath, File(destDir, name), overwrite)
      } else {
        copyAssetFile(context, assetPath, File(destDir, name), overwrite)
      }
    }
  }

  private fun copyAssetFile(context: Context, assetPath: String, dest: File, overwrite: Boolean = false) {
    dest.parentFile?.mkdirs()
    if (!overwrite && dest.exists() && dest.length() > 0) return
    context.assets.open(assetPath).use { input ->
      FileOutputStream(dest).use { output -> input.copyTo(output) }
    }
  }

  fun writeSolidPng(file: File, color: Int, w: Int, h: Int) {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(bmp).drawColor(color)
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
  }

  /** 16-bit mono WAV metronome — synthetic, DRM-free. */
  fun writeMetronomeWav(file: File, durationSec: Double, bpm: Double, sampleRate: Int = 22050) {
    file.parentFile?.mkdirs()
    val n = (durationSec * sampleRate).toInt()
    val period = 60.0 / bpm
    val data = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until n) {
      val t = i.toDouble() / sampleRate
      val phase = t % period
      val sample =
        if (phase < 0.03) {
          (0.6 * 32767.0 * sin(2.0 * Math.PI * 880.0 * phase)).toInt().coerceIn(-32767, 32767).toShort()
        } else {
          0
        }
      data.putShort(sample)
    }
    val dataBytes = data.array()
    FileOutputStream(file).use { out ->
      out.write("RIFF".toByteArray())
      out.write(intLE(36 + dataBytes.size))
      out.write("WAVE".toByteArray())
      out.write("fmt ".toByteArray())
      out.write(intLE(16))
      out.write(shortLE(1))
      out.write(shortLE(1))
      out.write(intLE(sampleRate))
      out.write(intLE(sampleRate * 2))
      out.write(shortLE(2))
      out.write(shortLE(16))
      out.write("data".toByteArray())
      out.write(intLE(dataBytes.size))
      out.write(dataBytes)
    }
  }

  private fun intLE(v: Int): ByteArray =
    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

  private fun shortLE(v: Int): ByteArray =
    ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()
}
