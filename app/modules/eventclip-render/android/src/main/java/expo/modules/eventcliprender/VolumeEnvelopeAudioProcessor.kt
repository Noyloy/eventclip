package expo.modules.eventcliprender

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Applies DuckEnvelope gains to PCM16 audio in the Transformer pipeline.
 * Tracks presentation time from the number of frames consumed.
 */
@UnstableApi
class VolumeEnvelopeAudioProcessor(
  private val ranges: List<DuckRange>,
  private val rampSec: Double = 0.05,
) : BaseAudioProcessor() {
  private var sampleRate = 44100
  private var channelCount = 1
  private var framesProcessed = 0L

  override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
    }
    sampleRate = inputAudioFormat.sampleRate
    channelCount = inputAudioFormat.channelCount
    return inputAudioFormat
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    if (!inputBuffer.hasRemaining()) return
    inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
    val out = replaceOutputBuffer(inputBuffer.remaining())
    out.order(ByteOrder.LITTLE_ENDIAN)
    while (inputBuffer.hasRemaining()) {
      // one frame = channelCount * 2 bytes
      val timeSec = framesProcessed.toDouble() / sampleRate.toDouble()
      val gain = DuckEnvelope.gainAt(timeSec, ranges, rampSec).toFloat()
      for (ch in 0 until channelCount) {
        if (!inputBuffer.hasRemaining()) break
        val sample = inputBuffer.short
        val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        out.putShort(scaled.toShort())
      }
      framesProcessed++
    }
    out.flip()
  }

  override fun onFlush() {
    // keep framesProcessed — flush mid-stream should not reset timeline for Transformer continuity
  }

  override fun onReset() {
    framesProcessed = 0L
  }
}
