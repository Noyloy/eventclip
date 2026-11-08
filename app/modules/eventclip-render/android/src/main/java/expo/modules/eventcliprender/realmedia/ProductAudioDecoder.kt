package expo.modules.eventcliprender.realmedia
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

data class NativeAudioProbe(val audioTrack:Int?,val audioFirstPtsUs:Long?,val videoFirstPtsUs:Long?,val audioDurationUs:Long?,val sampleRate:Int?,val channels:Int?)
/** Product decoder, separate from diagnostic decoder's mono/PCM16 assumptions. No content logs. */
object ProductAudioDecoder {
  fun probe(file:File):NativeAudioProbe {
    val ex=MediaExtractor();try{ex.setDataSource(file.absolutePath);var a:Int?=null;var v:Int?=null;var af:Long?=null;var vf:Long?=null;var duration:Long?=null;var sampleRate:Int?=null;var channels:Int?=null
      for(i in 0 until ex.trackCount){val f=ex.getTrackFormat(i);val mime=f.getString(MediaFormat.KEY_MIME)?:""
        if(mime.startsWith("audio/")){require(a==null);a=i;sampleRate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);duration=if(f.containsKey(MediaFormat.KEY_DURATION))f.getLong(MediaFormat.KEY_DURATION)else null}
        if(mime.startsWith("video/")){require(v==null);v=i}
      }
      a?.let{ex.selectTrack(it);af=ex.sampleTime;ex.unselectTrack(it)};v?.let{ex.selectTrack(it);vf=ex.sampleTime;ex.unselectTrack(it)}
      if(a!=null)require(af!=null && af!! != -1L){"AUDIO_TIMESTAMP_UNAVAILABLE"}
      return NativeAudioProbe(a,af,vf,duration,sampleRate,channels)
    }finally{ex.release()}
  }
  fun decode(file:File,track:Int,sink:ProductPcmTimeline,cancelled:AtomicBoolean,stopAtSourceUs:Long?=null) {
    val ex=MediaExtractor();var codec:MediaCodec?=null;var started=false
    try{ex.setDataSource(file.absolutePath);ex.selectTrack(track);val format=ex.getTrackFormat(track)
      // Gapless trimming semantics must be verified before accepting nonzero delay/padding.
      GaplessPolicy.requireVerified(if(format.containsKey("encoder-delay"))format.getInteger("encoder-delay")else 0,if(format.containsKey("encoder-padding"))format.getInteger("encoder-padding")else 0)
      // Official MediaFormat defaults may enable AAC DRC/loudness normalization: explicitly disable.
      if(format.getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm"){
        format.setInteger("aac-target-ref-level",-1);format.setInteger("aac-drc-boost-level",0);format.setInteger("aac-drc-cut-level",0)
        if(android.os.Build.VERSION.SDK_INT>=28)format.setInteger("aac-drc-effect-type",-1)
      }
      val c=MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)));codec=c;c.configure(format,null,null,0);c.start();started=true
      var rate=0;var channels=0;var encoding=0;var inputDone=false;var done=false;var lastProgress=SystemClock.elapsedRealtime();val info=MediaCodec.BufferInfo()
      while(!done){
        if(cancelled.get())throw FoundationFailure(ImportCode.IMPORT_CANCELLED)
        check(SystemClock.elapsedRealtime()-lastProgress<21000){"AUDIO_DECODE_STALLED"} // bounded decoder inactivity, NOT a changed Media3 mux watchdog
        if(!inputDone){val i=c.dequeueInputBuffer(10000);if(i>=0){val b=requireNotNull(c.getInputBuffer(i));b.clear();val size=ex.readSampleData(b,0)
          if(size<0 || (stopAtSourceUs!=null && ex.sampleTime>stopAtSourceUs+1000000)){c.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}else{c.queueInputBuffer(i,0,size,ex.sampleTime,0);ex.advance()}
        }}
        when(val i=c.dequeueOutputBuffer(info,10000)){
          MediaCodec.INFO_OUTPUT_FORMAT_CHANGED->{val f=c.outputFormat;rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);encoding=if(f.containsKey("pcm-encoding"))f.getInteger("pcm-encoding")else 2;require(encoding in setOf(2,4)&&channels in 1..2);lastProgress=SystemClock.elapsedRealtime()}
          in 0..Int.MAX_VALUE->{try{if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0){require(rate>0&&channels>0);val b=requireNotNull(c.getOutputBuffer(i)).duplicate().order(ByteOrder.LITTLE_ENDIAN);b.position(info.offset);b.limit(info.offset+info.size);val bytes=if(encoding==4)4 else 2;require(b.remaining()%(bytes*channels)==0);val values=FloatArray(b.remaining()/bytes){if(encoding==4)b.float else b.short/32768f};sink.block(info.presentationTimeUs,rate,channels,values);lastProgress=SystemClock.elapsedRealtime()};done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0}finally{c.releaseOutputBuffer(i,false)}}
        }
      }
      sink.end()
    }finally{if(started)try{codec?.stop()}catch(_:Exception){};codec?.release();ex.release()}
  }
}
