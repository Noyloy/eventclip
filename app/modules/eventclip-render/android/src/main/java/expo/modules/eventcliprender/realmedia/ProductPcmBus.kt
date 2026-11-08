package expo.modules.eventcliprender.realmedia
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Two bounded PCM buses. Gains applied BEFORE Media3 sums/clamps; no already-clipped postgain. */
class ProductPcmBus(val durationUs:Long) {
  val rate=48000
  val frames=((durationUs*rate+999999)/1000000).toInt()
  val samples=ShortArray(frames*2)
  init{require(durationUs in 1..FoundationPolicy.MAX_MOVIE_US)}
  fun put(frame:Int,left:Float,right:Float,gain:Double){
    if(frame !in 0 until frames)return
    require(gain in 0.0..0.5 && left.isFinite() && right.isFinite() && left in -1f..1f && right in -1f..1f)
    samples[frame*2]=(left*gain*32767).toInt().toShort();samples[frame*2+1]=(right*gain*32767).toInt().toShort()
  }
  fun writeWav(file:File){
    require(!file.exists());val bytes=samples.size*2
    FileOutputStream(file).use{out->
      val h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
      h.put("RIFF".toByteArray()).putInt(36+bytes).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(rate).putInt(rate*4).putShort(4).putShort(16).put("data".toByteArray()).putInt(bytes);out.write(h.array())
      val buf=ByteBuffer.allocate(16384).order(ByteOrder.LITTLE_ENDIAN)
      for(v in samples){if(buf.remaining()<2){out.write(buf.array(),0,buf.position());buf.clear()};buf.putShort(v)}
      if(buf.position()>0)out.write(buf.array(),0,buf.position());out.fd.sync()
    }
  }
}
/** Streaming linear rate conversion on presentation timestamps; mono duplicates, stereo stays stereo.
 * Discontinuities leave zero-filled gaps; overlapping/changed formats reject, never stretch/mute. */
class ProductPcmTimeline(private val bus:ProductPcmBus,private val movieStartUs:Long,private val slotDurationUs:Long,private val sourceOriginUs:Long,private val gainAt:(Long)->Double) {
  private var previousTime:Double?=null;private var previousL=0f;private var previousR=0f
  private var inputRate=0;private var inputChannels=0;private var nextFrame=ceil(movieStartUs*bus.rate/1000000.0).toInt()
  var receivedFrames=0L;private set
  fun block(ptsUs:Long,rate:Int,channels:Int,data:FloatArray){
    require(rate in 8000..48000 && channels in 1..2 && data.size%channels==0)
    if(inputRate!=0)require(inputRate==rate && inputChannels==channels){"AUDIO_FORMAT_CHANGED"}
    inputRate=rate;inputChannels=channels
    val base=Math.subtractExact(ptsUs,sourceOriginUs).toDouble()+movieStartUs
    val step=1000000.0/rate
    for(i in 0 until data.size/channels){
      val t=base+i*step;val l=data[i*channels];val r=if(channels==1)l else data[i*channels+1]
      require(l.isFinite()&&r.isFinite()&&l in -1f..1f&&r in -1f..1f){"AUDIO_PCM_RANGE_UNSUPPORTED"}
      val prev=previousTime
      if(prev!=null){
        require(t>prev){"AUDIO_TIMESTAMP_OVERLAP"}
        if(t-prev<=step+2.0){
          while(nextFrame<bus.frames && nextFrame*1000000.0/bus.rate<t){
            val outT=nextFrame*1000000.0/bus.rate
            if(outT>=prev && outT>=movieStartUs && outT<movieStartUs+slotDurationUs){val k=((outT-prev)/(t-prev)).toFloat();bus.put(nextFrame,previousL+(l-previousL)*k,previousR+(r-previousR)*k,gainAt(outT.toLong()))}
            nextFrame++
          }
        }else {
          // Finish ONLY previous input sample's own interval, never stretch it across a gap.
          while(nextFrame<bus.frames && nextFrame*1000000.0/bus.rate<prev+step){val outT=nextFrame*1000000.0/bus.rate;if(outT>=movieStartUs&&outT<movieStartUs+slotDurationUs)bus.put(nextFrame,previousL,previousR,gainAt(outT.toLong()));nextFrame++}
          nextFrame=maxOf(nextFrame,ceil(t*bus.rate/1000000.0).toInt())
        }
      }else nextFrame=maxOf(nextFrame,ceil(t*bus.rate/1000000.0).toInt())
      previousTime=t;previousL=l;previousR=r;receivedFrames++
    }
  }
  fun end(){val last=previousTime?:error("AUDIO_TRACK_DECODE_EMPTY");val end=last+1000000.0/inputRate
    while(nextFrame<bus.frames && nextFrame*1000000.0/bus.rate<end){val t=nextFrame*1000000.0/bus.rate;if(t>=movieStartUs&&t<movieStartUs+slotDurationUs)bus.put(nextFrame,previousL,previousR,gainAt(t.toLong()));nextFrame++}
  }
}
