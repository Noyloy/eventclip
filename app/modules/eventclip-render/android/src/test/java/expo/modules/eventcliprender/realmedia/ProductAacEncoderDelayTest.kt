package expo.modules.eventcliprender.realmedia
import android.media.MediaCodec
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.transformer.Codec
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class ProductAacEncoderDelayTest {
 @Test fun aacUsesSignaledDelayOtherwise2048AndNonAacIsZero(){
  assertTrue(ProductAacEncoderDelay.applies("audio/mp4a-latm"))
  assertEquals(2048,ProductAacEncoderDelay.delaySamples("audio/mp4a-latm",0))
  assertEquals(1024,ProductAacEncoderDelay.delaySamples("audio/mp4a-latm",1024))
  assertEquals(2048,ProductAacEncoderDelay.delaySamples("audio/mp4a-latm",2048))
  assertEquals(0,ProductAacEncoderDelay.delaySamples("audio/opus",0))
  assertEquals(0,ProductAacEncoderDelay.delaySamples("audio/raw",2048))
  assertFalse(ProductAacEncoderDelay.applies("video/avc"))
 }
 @Test fun delayUsIsExactFor2048At48kAndMatchesTwoAacFrames(){
  assertEquals(42666L,ProductAacEncoderDelay.delayUs(2048,48000))
  assertEquals(21333L,ProductAacEncoderDelay.delayUs(1024,48000))
  assertEquals(2*ProductAacEncoderDelay.FRAME_SAMPLES,ProductAacEncoderDelay.DEFAULT_DELAY_SAMPLES)
 }

 /** Growable buffers can hold the full 2048-frame pad plus EOS in one queue; never dequeue. */
 @Test fun eosPadFlushesOnTheSameBufferAndNeverDequeuesAgain(){
  val fake=RecordingCodec()
  val codec=ProductAacDelayCodec(fake,aacFormat())
  val buffer=DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)
  buffer.ensureSpaceForWrite(64)
  buffer.timeUs=31_000_000L
  buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
  buffer.flip()
  codec.queueInputBuffer(buffer)
  assertEquals("must not dequeue a second encoder input at EOS",0,fake.dequeueCalls)
  assertEquals(1,fake.queued.size)
  val q=fake.queued.single()
  assertTrue("silence and EOS are on the same buffer",q.eos)
  assertEquals(2048*4,q.size)
  assertEquals(31_000_000L,q.timeUs)
 }

 /**
  * Production c2.android.aac.encoder buffers are 4096 bytes (one AAC frame) and cannot grow.
  * The first EOS must pad 1024 frames without EOS; Media3's next pump supplies another empty
  * EOS buffer for the remaining 1024 frames plus EOS. This method never dequeues.
  */
 @Test fun eosPadSplitsAcrossMedia3PumpsWhenCodecBufferIsOneAacFrame(){
  val fake=RecordingCodec()
  val codec=ProductAacDelayCodec(fake,aacFormat())
  codec.queueInputBuffer(disabledEos(4096,9_000_000L))
  assertEquals(0,fake.dequeueCalls)
  assertEquals(1,fake.queued.size)
  assertFalse("first 4096-byte buffer must not end the stream",fake.queued[0].eos)
  assertEquals(1024*4,fake.queued[0].size)
  assertEquals(9_000_000L,fake.queued[0].timeUs)
  codec.queueInputBuffer(disabledEos(4096,9_000_000L))
  assertEquals(0,fake.dequeueCalls)
  assertEquals(2,fake.queued.size)
  assertTrue(fake.queued[1].eos)
  assertEquals(1024*4,fake.queued[1].size)
  assertEquals(9_000_000L+21333L,fake.queued[1].timeUs)
 }

 @Test fun eosPadKeepsExistingPcmAndAppendsSilenceOnTheSameGrowableBuffer(){
  val fake=RecordingCodec()
  val codec=ProductAacDelayCodec(fake,aacFormat())
  val existing=ByteArray(1024*4){i->(i and 0xff).toByte()}
  val buffer=DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)
  buffer.ensureSpaceForWrite(existing.size+16)
  buffer.data!!.put(existing)
  buffer.timeUs=31_000_000L
  buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
  buffer.flip()
  codec.queueInputBuffer(buffer)
  assertEquals(0,fake.dequeueCalls)
  assertEquals(1,fake.queued.size)
  val q=fake.queued.single()
  assertTrue(q.eos)
  assertEquals(existing.size+2048*4,q.size)
  assertEquals(31_000_000L,q.timeUs)
  assertArrayEquals(existing,q.bytes.copyOf(existing.size))
 }

 private fun aacFormat()=Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setSampleRate(48000).setChannelCount(2).build()
 private fun disabledEos(capacity:Int,timeUs:Long):DecoderInputBuffer {
  val buffer=DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED)
  buffer.data=ByteBuffer.allocate(capacity)
  buffer.clear()
  buffer.timeUs=timeUs
  buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
  buffer.flip()
  return buffer
 }

 private data class Queued(val size:Int,val eos:Boolean,val timeUs:Long,val bytes:ByteArray)
 private class RecordingCodec:Codec {
  var dequeueCalls=0
  val queued=ArrayList<Queued>()
  override fun getConfigurationFormat()=Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()
  override fun getName()="fake"
  override fun getInputSurface():Surface=throw UnsupportedOperationException()
  override fun maybeDequeueInputBuffer(buffer:DecoderInputBuffer):Boolean {dequeueCalls++;return false}
  override fun queueInputBuffer(buffer:DecoderInputBuffer) {
   val data=buffer.data
   val bytes=if(data==null) ByteArray(0) else ByteArray(data.remaining()).also{data.asReadOnlyBuffer().get(it)}
   queued.add(Queued(bytes.size,buffer.isEndOfStream,buffer.timeUs,bytes))
  }
  override fun signalEndOfInputStream(){}
  override fun getOutputFormat():Format?=null
  override fun getOutputBuffer():ByteBuffer?=null
  override fun getOutputBufferInfo():MediaCodec.BufferInfo?=null
  override fun releaseOutputBuffer(render:Boolean){}
  override fun releaseOutputBuffer(renderTimeNs:Long){}
  override fun isEnded()=false
  override fun release(){}
 }
}
