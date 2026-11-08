package expo.modules.eventcliprender

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PcmAudio(val sampleRate: Int, val channelCount: Int, val samples: FloatArray)

/**
 * Decode local MP3/M4A/WAV to mono float PCM via MediaExtractor + MediaCodec.
 * Streams decode; does not keep encoded file in memory beyond buffers.
 */
object AudioDecoder {
  fun decodeUri(context: Context, uriString: String, maxSeconds: Double? = null): PcmAudio {
    val extractor = MediaExtractor()
    val uri = Uri.parse(if (uriString.startsWith("file:") || uriString.startsWith("content:") || uriString.startsWith("asset:")) uriString else "file://$uriString")
    if (uriString.startsWith("asset://")) {
      val assetPath = uriString.removePrefix("asset://")
      context.assets.openFd(assetPath).use { afd ->
        extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
      }
    } else {
      extractor.setDataSource(context, uri, null)
    }
    var track = -1
    var format: MediaFormat? = null
    for (i in 0 until extractor.trackCount) {
      val f = extractor.getTrackFormat(i)
      val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
      if (mime.startsWith("audio/")) {
        track = i
        format = f
        break
      }
    }
    if (track < 0 || format == null) {
      extractor.release()
      throw IllegalArgumentException("No audio track in $uriString")
    }
    extractor.selectTrack(track)
    val mime = format.getString(MediaFormat.KEY_MIME)!!
    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    val codec = MediaCodec.createDecoderByType(mime)
    codec.configure(format, null, null, 0)
    codec.start()
    val pcm = ArrayList<Float>()
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    var outputDone = false
    val maxSamples = if (maxSeconds != null) (maxSeconds * sampleRate).toInt() * channelCount else Int.MAX_VALUE
    while (!outputDone) {
      if (!inputDone) {
        val inIndex = codec.dequeueInputBuffer(10_000)
        if (inIndex >= 0) {
          val buf = codec.getInputBuffer(inIndex)!!
          val sampleSize = extractor.readSampleData(buf, 0)
          if (sampleSize < 0) {
            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
          } else {
            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
            extractor.advance()
          }
        }
      }
      val outIndex = codec.dequeueOutputBuffer(info, 10_000)
      if (outIndex >= 0) {
        val outBuf = codec.getOutputBuffer(outIndex)!!
        if (info.size > 0) {
          outBuf.order(ByteOrder.LITTLE_ENDIAN)
          outBuf.position(info.offset)
          outBuf.limit(info.offset + info.size)
          // assume PCM 16-bit
          while (outBuf.remaining() >= 2 && pcm.size < maxSamples) {
            val s = outBuf.short / 32768f
            pcm.add(s)
          }
        }
        codec.releaseOutputBuffer(outIndex, false)
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
          outputDone = true
        }
        if (pcm.size >= maxSamples) outputDone = true
      }
    }
    codec.stop()
    codec.release()
    extractor.release()
    // downmix to mono
    val frames = pcm.size / channelCount
    val mono = FloatArray(frames)
    var idx = 0
    for (f in 0 until frames) {
      var acc = 0f
      for (c in 0 until channelCount) {
        acc += pcm[idx++]
      }
      mono[f] = acc / channelCount
    }
    return PcmAudio(sampleRate, 1, mono)
  }
}
