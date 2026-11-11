package expo.modules.eventcliprender.realmedia
import android.content.Context
import android.media.MediaCodec
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.transformer.Codec
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * AAC-LC encoders (Android software and the S23 hardware path) emit ~2048 priming samples
 * (two 1024-sample frames). FrameworkMuxer timestamps those frames at PTS 0 with no elst
 * or iTunSMPB, so any player that decodes the raw stream hears audio one priming period late.
 *
 * Product fix: pad the same number of silent PCM samples before encoder EOS so the delayed
 * tail is flushed, drop the priming frames from the muxed stream, and rebase remaining PTS
 * to 0. Output metadata keeps encoderDelay/padding at 0 — no second trim, and the timeline
 * is correct even when a player ignores edit lists. Source GaplessPolicy is unchanged:
 * nonzero delay/padding on inputs is still rejected because that path is unverified.
 */
object ProductAacEncoderDelay {
 const val FRAME_SAMPLES=1024
 const val DEFAULT_DELAY_SAMPLES=2048
 fun applies(mime:String?)=mime==MimeTypes.AUDIO_AAC||mime=="audio/mp4a-latm"
 fun delaySamples(mime:String?,signaled:Int)=if(!applies(mime))0 else if(signaled>0)signaled else DEFAULT_DELAY_SAMPLES
 fun delayUs(samples:Int,sampleRate:Int):Long {require(samples>=0&&sampleRate in 8000..48000);return samples*1_000_000L/sampleRate}
}

class ProductEncoderFactory(context:Context):Codec.EncoderFactory {
 private val base=DefaultEncoderFactory.Builder(context).build()
 override fun createForAudioEncoding(format:Format):Codec {
  val codec=base.createForAudioEncoding(format)
  return if(ProductAacEncoderDelay.applies(format.sampleMimeType))ProductAacDelayCodec(codec,format)else codec
 }
 override fun createForVideoEncoding(format:Format)=base.createForVideoEncoding(format)
 override fun audioNeedsEncoding()=base.audioNeedsEncoding()
 override fun videoNeedsEncoding()=base.videoNeedsEncoding()
}

internal class ProductAacDelayCodec(private val delegate:Codec,request:Format):Codec {
 private val sampleRate=if(request.sampleRate>0)request.sampleRate else 48000
 private val bytesPerFrame=2*if(request.channelCount>0)request.channelCount else 2
 private var delaySamples=ProductAacEncoderDelay.delaySamples(request.sampleMimeType,0)
 private var delayUs=ProductAacEncoderDelay.delayUs(delaySamples,sampleRate)
 private var droppedSamples=0
 private var padded=false
 private var haveAdjusted=false
 private val adjusted=MediaCodec.BufferInfo()
 private val padBuffer=DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED)
 private fun adoptDelay(signaled:Int){if(signaled>0&&signaled!=delaySamples){delaySamples=signaled;delayUs=ProductAacEncoderDelay.delayUs(delaySamples,sampleRate)}}
 override fun getConfigurationFormat()=delegate.configurationFormat
 override fun getName()=delegate.name
 override fun getInputSurface():Surface=delegate.inputSurface
 override fun getMaxPendingFrameCount()=delegate.maxPendingFrameCount
 override fun maybeDequeueInputBuffer(buffer:DecoderInputBuffer)=delegate.maybeDequeueInputBuffer(buffer)
 override fun queueInputBuffer(buffer:DecoderInputBuffer) {
  if(!buffer.isEndOfStream||padded){delegate.queueInputBuffer(buffer);return}
  padded=true
  if(delaySamples==0){delegate.queueInputBuffer(buffer);return}
  // Encoders may not accept a follow-up dequeue at EOS, so put silence and EOS on the same buffers.
  var remaining=delaySamples
  var timeUs=buffer.timeUs
  var current=buffer
  while(true){
   val leftover=writeSilence(current,timeUs,remaining,eos=remaining<=maxFrames(current))
   if(leftover.second==0)return
   timeUs=leftover.first;remaining=leftover.second
   if(!delegate.maybeDequeueInputBuffer(padBuffer))throw ExportException.createForUnexpected(IllegalStateException("AAC_ENCODER_DELAY_PAD_STALLED"))
   current=padBuffer
  }
 }
 private fun maxFrames(buffer:DecoderInputBuffer):Int {val data=buffer.data?:return 0;val pos=data.position();data.clear();val n=data.remaining()/bytesPerFrame;data.position(pos);return n}
 private fun writeSilence(buffer:DecoderInputBuffer,startUs:Long,remaining:Int,eos:Boolean):Pair<Long,Int> {
  buffer.clear()
  val data=requireNotNull(buffer.data);data.clear()
  val frames=minOf(remaining,data.remaining()/bytesPerFrame)
  if(frames<=0)throw ExportException.createForUnexpected(IllegalStateException("AAC_ENCODER_DELAY_PAD_EMPTY"))
  val n=frames*bytesPerFrame
  data.put(ByteArray(n));data.flip()
  buffer.timeUs=startUs;buffer.setFlags(0);if(eos)buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
  delegate.queueInputBuffer(buffer)
  return startUs+ProductAacEncoderDelay.delayUs(frames,sampleRate) to remaining-frames
 }
 override fun signalEndOfInputStream()=delegate.signalEndOfInputStream()
 override fun getOutputFormat():Format? {
  val f=delegate.outputFormat?:return null
  adoptDelay(f.encoderDelay)
  return if(f.encoderDelay==0&&f.encoderPadding==0)f else f.buildUpon().setEncoderDelay(0).setEncoderPadding(0).build()
 }
 override fun getOutputBuffer():ByteBuffer? {haveAdjusted=false;return nextKept()}
 override fun getOutputBufferInfo():MediaCodec.BufferInfo? {if(!haveAdjusted&&nextKept()==null)return null;return adjusted}
 override fun releaseOutputBuffer(render:Boolean){haveAdjusted=false;delegate.releaseOutputBuffer(render)}
 override fun releaseOutputBuffer(renderTimeNs:Long){haveAdjusted=false;delegate.releaseOutputBuffer(renderTimeNs)}
 override fun isEnded()=delegate.isEnded&&!haveAdjusted
 override fun release()=delegate.release()
 private fun nextKept():ByteBuffer? {
  while(true){
   val buf=delegate.outputBuffer?:return null
   val info=requireNotNull(delegate.outputBufferInfo)
   if(shouldDrop(info)){delegate.releaseOutputBuffer(false);continue}
   adjusted.set(info.offset,info.size,max(0L,info.presentationTimeUs-delayUs),info.flags)
   haveAdjusted=true;return buf
  }
 }
 private fun shouldDrop(info:MediaCodec.BufferInfo):Boolean {
  if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0&&info.size==0)return false
  if(droppedSamples>=delaySamples)return false
  droppedSamples+=ProductAacEncoderDelay.FRAME_SAMPLES;return true
 }
}
