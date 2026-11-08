package expo.modules.eventcliprender.realmedia
import java.security.MessageDigest
import kotlin.math.*

data class ProductTrackSummary(val mime:String,val samples:Long,val firstPtsUs:Long,val lastPtsUs:Long,val maxGapUs:Long,val durationUs:Long,val monotonic:Boolean,val duplicates:Int,val width:Int=0,val height:Int=0,val rotation:Int=0,val sampleRate:Int=0,val channels:Int=0)
object ProductExportPolicy {
 fun fingerprint(p:BirthdayProject):String {
  // Stable canonical state, never locale/platform Properties timestamps, names or picker URIs.
  val s=buildString{append(p.id).append('|').append(p.keepDateOrder);for(a in p.assets)append('|').append(listOf(a.id,a.kind,a.bytes,a.mime,a.durationUs,a.width,a.height,a.rotation,a.captureAtMs,a.included).joinToString(":"));for(m in p.voiceRanges)append('|').append(listOf(m.id,m.assetId,m.startUs,m.endUs).joinToString(":"));append('|').append(p.pendingVoiceStart)}
  return MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
 }
 fun movie(store:BirthdayProjectStore,p:BirthdayProject):MoviePlan {require(p.pendingVoiceStart==null){"INCOMPLETE_VOICE_RANGE"};require(p.assets.count{it.kind!="song"}<=30);var remaining=FoundationPolicy.MAX_SOURCE_BYTES;for(a in p.assets){require(a.bytes in 1..remaining);remaining-=a.bytes};val song=p.assets.single{it.kind=="song"};return FoundationPolicy.plan(store.ordered(p).filter{it.kind!="song"}.map{VisualInput(it.id,it.kind,it.durationUs,it.included)},song.durationUs,emptyList())}
 fun neededBytes(durationUs:Long):Long {require(durationUs in 1..FoundationPolicy.MAX_MOVIE_US);val pcm=((durationUs*48000+999999)/1000000)*8+88;val output=((durationUs+999999)/1000000)*2_000_000L;return pcm+output+FoundationPolicy.FREE_RESERVE_BYTES} // conservative16Mbps output budget, not measured bitrate
 fun validate(durationUs:Long,tracks:List<ProductTrackSummary>,videoGapUs:Long) {
  require(durationUs in 1..FoundationPolicy.MAX_MOVIE_US && tracks.size==2)
  val v=tracks.single{it.mime=="video/avc"};val a=tracks.single{it.mime=="audio/mp4a-latm"}
  require(v.width==720 && v.height==1280 && v.rotation==0);require(a.sampleRate==48000 && a.channels==2)
  for(t in tracks){val audio=t===a;val gap=if(audio)24000L else maxOf(33334L,videoGapUs)+2000L;val tolerance=if(audio)50000L else gap+33334L
   require(t.samples in 1..100000 && t.monotonic && t.duplicates==0 && t.firstPtsUs in 0..tolerance && t.maxGapUs<=gap)
   require(abs(t.durationUs-durationUs)<=tolerance && t.lastPtsUs<=durationUs+tolerance && t.lastPtsUs>=durationUs-tolerance)
   require(t.samples>=maxOf(1L,(durationUs-tolerance)/gap))
  }
 }
}
data class WatermarkBox(val fontPx:Int,val padX:Int,val padY:Int,val radius:Int,val inset:Int,val width:Int,val height:Int,val x:Int,val y:Int)
object ProductWatermarkGeometry {
 fun box(w:Int,h:Int,inkWidth:Int,inkHeight:Int):WatermarkBox {require(w>0&&h>0&&inkWidth>0&&inkHeight>0);val s=min(w,h);fun scaled(n:Int)=(s*n/720.0).roundToInt()
  val px=scaled(8);val py=scaled(5);val inset=scaled(24);val bw=inkWidth+2*px;val bh=inkHeight+2*py;require(bw+inset<=w&&bh+inset<=h);return WatermarkBox((.025*s).roundToInt(),px,py,scaled(6),inset,bw,bh,w-inset-bw,h-inset-bh)}
 fun displayed(w:Int,h:Int,rotation:Int):Pair<Int,Int>{require(rotation in listOf(0,90,180,270));return if(rotation%180==0)w to h else h to w}
 fun fitted(w:Int,h:Int,rotation:Int,outW:Int,outH:Int):Pair<Double,Double>{val d=displayed(w,h,rotation);val scale=min(outW.toDouble()/d.first,outH.toDouble()/d.second);return d.first*scale to d.second*scale}
}
