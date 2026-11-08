package com.eventclip.spike
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** INSTRUMENTATION verifier, not the product converter. No manual delay/padding trim or time shifting. */
data class EvidencePcm(val samples:FloatArray,val rate:Int,val channels:Int,val firstPtsUs:Long,val maxPtsDiscontinuityUs:Long,val format:JSONObject) {
 val frames get()=samples.size/channels
 fun amplitude(freq:Double,startUs:Long,endUs:Long):Double {
  val from=(((startUs-firstPtsUs)*rate/1e6).roundToInt()).coerceAtLeast(0);val to=(((endUs-firstPtsUs)*rate/1e6).roundToInt()).coerceAtMost(frames);require(to-from>=100)
  var re=0.0;var im=0.0;var weight=0.0
  for(i in from until to){val w=.5-.5*cos(2*PI*(i-from)/(to-from-1));val angle=2*PI*freq*(firstPtsUs/1e6+i.toDouble()/rate);val sample=samples[i*channels];re+=sample*w*cos(angle);im+=sample*w*sin(angle);weight+=w}
  return 2*hypot(re,im)/weight
 }
 fun peak()=samples.maxOf{abs(it.toDouble())}
 fun jump(atUs:Long):Double{val center=((atUs-firstPtsUs)*rate/1000000).toInt();return ((center-rate/50).coerceAtLeast(1) until (center+rate/50).coerceAtMost(frames)).maxOfOrNull{i->abs(samples[i*channels]-samples[(i-1)*channels]).toDouble()}?:0.0}
 fun writeFloat(file:File){FileOutputStream(file).use{out->val b=ByteBuffer.allocate(65536).order(ByteOrder.LITTLE_ENDIAN);for(s in samples){if(b.remaining()<4){out.write(b.array(),0,b.position());b.clear()};b.putFloat(s)};if(b.position()>0)out.write(b.array(),0,b.position());out.fd.sync()}}
}
object NativePcmEvidence {
 fun decode(file:File,deadlineMs:Long):EvidencePcm {
  val ex=MediaExtractor();var codec:MediaCodec?=null;var started=false
  try{ex.setDataSource(file.absolutePath);val track=(0 until ex.trackCount).single{ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true};ex.selectTrack(track);val f=ex.getTrackFormat(track)
   val metadata=JSONObject().put("inputMime",f.getString(MediaFormat.KEY_MIME)).put("declaredDelayFrames",if(f.containsKey("encoder-delay"))f.getInteger("encoder-delay")else 0).put("declaredPaddingFrames",if(f.containsKey("encoder-padding"))f.getInteger("encoder-padding")else 0)
   if(f.getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm"){f.setInteger("aac-target-ref-level",-1);f.setInteger("aac-drc-boost-level",0);f.setInteger("aac-drc-cut-level",0);if(android.os.Build.VERSION.SDK_INT>=28)f.setInteger("aac-drc-effect-type",-1)}
   val c=MediaCodec.createDecoderByType(requireNotNull(f.getString(MediaFormat.KEY_MIME)));codec=c;c.configure(f,null,null,0);c.start();started=true
   var rate=0;var channels=0;var encoding=2;var inputDone=false;var outputDone=false;val info=MediaCodec.BufferInfo();val values=FloatArray(48000*2*16);var used=0;var first=Long.MIN_VALUE;var expectedNext=Long.MIN_VALUE;var gap=0L;val blocks=JSONArray()
   while(!outputDone){check(SystemClock.elapsedRealtime()<deadlineMs){"NATIVE_DECODE_DEADLINE"}
    if(!inputDone){val i=c.dequeueInputBuffer(10000);if(i>=0){val b=requireNotNull(c.getInputBuffer(i));b.clear();val n=ex.readSampleData(b,0);if(n<0){c.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}else{c.queueInputBuffer(i,0,n,ex.sampleTime,0);ex.advance()}}}
    when(val i=c.dequeueOutputBuffer(info,10000)){
     MediaCodec.INFO_OUTPUT_FORMAT_CHANGED->{val o=c.outputFormat;val r=o.getInteger(MediaFormat.KEY_SAMPLE_RATE);val ch=o.getInteger(MediaFormat.KEY_CHANNEL_COUNT);require(rate==0||rate==r&&channels==ch);rate=r;channels=ch;encoding=if(o.containsKey("pcm-encoding"))o.getInteger("pcm-encoding")else 2;require(rate in 8000..48000&&channels in 1..2&&encoding in listOf(2,4));metadata.put("rate",rate).put("channels",channels).put("encoding",encoding)}
     in 0..Int.MAX_VALUE->{try{if(info.size>0&&info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0){require(rate>0);val b=requireNotNull(c.getOutputBuffer(i)).duplicate().order(ByteOrder.LITTLE_ENDIAN);b.position(info.offset);b.limit(info.offset+info.size);val count=b.remaining()/(if(encoding==4)4 else 2);require(count%channels==0&&used+count<=values.size);if(first==Long.MIN_VALUE)first=info.presentationTimeUs;if(expectedNext!=Long.MIN_VALUE)gap=maxOf(gap,abs(info.presentationTimeUs-expectedNext));expectedNext=info.presentationTimeUs+(count/channels)*1000000L/rate;blocks.put(JSONObject().put("ptsUs",info.presentationTimeUs).put("frames",count/channels));repeat(count){values[used++]=if(encoding==4)b.float else b.short/32768f}};outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0}finally{c.releaseOutputBuffer(i,false)}}
    }
   }
   require(used>0);metadata.put("blocks",blocks).put("firstPtsUs",first).put("frames",used/channels).put("maxPtsDiscontinuityUs",gap).put("manualTrim",false)
   return EvidencePcm(values.copyOf(used),rate,channels,first,gap,metadata)
  }finally{if(started)try{codec?.stop()}catch(_:Exception){};codec?.release();ex.release()}
 }
 fun envelopeLag(output:EvidencePcm,reference:EvidencePcm,centerUs:Long):Pair<Int,Double>{
  val start=centerUs-700000;val ref=(0..140).map{i->reference.amplitude(440.0,start+i*10000,start+i*10000+20000)}
  val actual=(0..140).map{i->output.amplitude(440.0,start+i*10000,start+i*10000+20000)}
  var best=-2.0;var bestLag=0
  for(lag in -5..5){val pairs=(5..135).map{i->ref[i] to actual[i+lag]};val a=pairs.map{it.first}.average();val b=pairs.map{it.second}.average();val numerator=pairs.sumOf{(it.first-a)*(it.second-b)};val denominator=sqrt(pairs.sumOf{(it.first-a).pow(2)}*pairs.sumOf{(it.second-b).pow(2)});val corr=if(denominator>0)numerator/denominator else -1.0;if(corr>best){best=corr;bestLag=lag*10}}
  return bestLag to best
 }
}
