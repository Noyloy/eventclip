package expo.modules.eventcliprender.realmedia
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
object ProductWork {
 val executor=Executors.newSingleThreadExecutor()
 private val owner=AtomicReference<String?>(null)
 fun acquire(id:String)=owner.compareAndSet(null,id)
 fun release(id:String){owner.compareAndSet(id,null)}
 fun busy()=owner.get()!=null
 fun owns(id:String)=owner.get()==id
}
/** No synthetic clock: marks require ready, rendered, non-seeking native player sample. */
object PreviewClock {
 fun clipTimeUs(positionMs:Long,ready:Boolean,rendered:Boolean,seeking:Boolean,startUs:Long,durationUs:Long,allowEnd:Boolean=false):Long? {
  if(!ready||!rendered||seeking||positionMs<0)return null
  val time=positionMs*1000-startUs
  return time.takeIf{it>=0 && (it<durationUs || allowEnd&&it==durationUs)}
 }
}
