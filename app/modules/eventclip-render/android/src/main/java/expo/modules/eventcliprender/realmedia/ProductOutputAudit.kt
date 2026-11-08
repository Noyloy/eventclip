package expo.modules.eventcliprender.realmedia
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Compressed sample metadata ONLY. Does not decode or prove audibility, pixels, true peak or complete codec frames. */
object ProductOutputAudit {
 fun inspect(file:File,cancelled:AtomicBoolean):List<ProductTrackSummary> {
  val probe=MediaExtractor();val count=try{probe.setDataSource(file.absolutePath);probe.trackCount}finally{probe.release()};require(count==2)
  return (0 until count).map{scan(file,it,cancelled)}
 }
 private fun scan(file:File,index:Int,cancelled:AtomicBoolean):ProductTrackSummary {
  val e=MediaExtractor();try{e.setDataSource(file.absolutePath);val f=e.getTrackFormat(index);e.selectTrack(index);var n=0L;var first=-1L;var last=-1L;var gap=0L;var mono=true;var duplicates=0
   while(true){if(cancelled.get())throw ExportStopped();val pts=e.sampleTime;if(pts<0)break;require(++n<=100000);require(e.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED==0);if(first<0)first=pts;if(last>=0){if(pts<last)mono=false;if(pts==last)duplicates++;gap=maxOf(gap,pts-last)};last=pts;if(!e.advance())break}
   fun int(k:String)=if(f.containsKey(k))f.getInteger(k)else 0
   return ProductTrackSummary(requireNotNull(f.getString(MediaFormat.KEY_MIME)),n,first,last,gap,if(f.containsKey(MediaFormat.KEY_DURATION))f.getLong(MediaFormat.KEY_DURATION)else -1,mono,duplicates,int(MediaFormat.KEY_WIDTH),int(MediaFormat.KEY_HEIGHT),int(MediaFormat.KEY_ROTATION),int(MediaFormat.KEY_SAMPLE_RATE),int(MediaFormat.KEY_CHANNEL_COUNT))
  }finally{e.release()}
 }
 fun expectedVideoGap(file:File,cancelled:AtomicBoolean):Long {
  val e=MediaExtractor();try{e.setDataSource(file.absolutePath);val track=(0 until e.trackCount).single{e.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true};e.selectTrack(track);val pts=java.util.TreeSet<Long>();var n=0
   while(e.sampleTime>=0){if(cancelled.get())throw ExportStopped();require(++n<=100000);pts.add(e.sampleTime);if(!e.advance())break};require(n>0);return pts.zipWithNext{a,b->b-a}.maxOrNull()?.coerceAtLeast(33334L)?:33334L
  }finally{e.release()}
 }
}
