package expo.modules.eventcliprender.realmedia
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.transformer.*
import java.io.File
import java.util.Properties
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground-only job; no persisted resume. All Transformer access on MAIN, file work serialized. */
class ProductExportController(context:Context,private val emit:(Map<String,Any?>)->Unit) {
 private val context=context.applicationContext
 private val main=Handler(Looper.getMainLooper())
 private class Job(val lease:String) {
  val cancelled=AtomicBoolean(false);val finalized=AtomicBoolean(false)
  @Volatile var id:String?=null;@Volatile var prepared:PreparedProductComposition?=null
  @Volatile var stopState="EXPORT_CANCELLED";@Volatile var reasonCode="";@Volatile var error:String?=null;var transformer:Transformer?=null
  @Volatile var stage="PREPARING"
 }
 @Volatile private var active:Job?=null
 private fun outputs()=ProductExportStore(File(context.filesDir,"eventclip-birthday-exports"))
 private fun workspaces()=ProductWorkspaces(File(context.cacheDir,"eventclip-birthday-render"))
 private fun sources()=BirthdayProjectStore(File(context.filesDir,"eventclip-birthday"))
 private fun latest(p:Properties?):Map<String,Any?>?=p?.let{mapOf("exportId" to it.getProperty("id"),"durationUs" to it.getProperty("durationUs").toLong(),"bytes" to it.getProperty("bytes").toLong(),"decodeVerified" to false,"metadataVerified" to true,"videoSamples" to (0..1).filter{i->it.getProperty("track.$i.mime").startsWith("video/")}.sumOf{i->it.getProperty("track.$i.samples").toLong()},"audioSamples" to (0..1).filter{i->it.getProperty("track.$i.mime").startsWith("audio/")}.sumOf{i->it.getProperty("track.$i.samples").toLong()})}
 fun status():Map<String,Any?> {val s=outputs();if(active==null&&!ProductWork.busy()){s.recover();workspaces().recover()};return mapOf("state" to (s.status()?.getProperty("state")?:"READY_TO_CREATE"),"active" to (active!=null),"lastGood" to latest(s.latest()),"decodeVerified" to false)}
 fun create(projectId:String,fingerprint:String):Boolean {
  check(Looper.myLooper()==Looper.getMainLooper());if(active!=null)return false
  val j=Job(UUID.randomUUID().toString());if(!ProductWork.acquire(j.lease))return false;active=j
  emit(mapOf("state" to "PLANNING","active" to true,"progressPercent" to null,"outBytes" to 0L))
  ProductWork.executor.execute {
   try{val o=outputs();o.recover();workspaces().recover();if(j.cancelled.get())throw ExportStopped()
    val store=sources();val p=store.load();require(p.id==projectId&&ProductExportPolicy.fingerprint(p)==fingerprint){"PROJECT_CHANGED"};val movie=ProductExportPolicy.movie(store,p)
    require(p.assets.filter{it.included||it.kind=="song"}.all{store.file(it.id).isFile&&store.file(it.id).length()==it.bytes}){"SOURCE_UNREADABLE"}
    if(context.filesDir.usableSpace<ProductExportPolicy.neededBytes(movie.durationUs))throw FoundationFailure(ImportCode.STORAGE_LOW)
    j.id=o.begin(p.id,fingerprint,movie.durationUs)
    val gap=movie.items.maxOf{item->val a=p.assets.single{it.id==item.id};if(a.kind=="video")ProductOutputAudit.expectedVideoGap(store.file(a.id),j.cancelled)else 33334L}
    val prepared=ProductNativeComposition.prepare(context,store,movie,j.cancelled);j.prepared=prepared
    require(ProductExportPolicy.fingerprint(store.load())==fingerprint){"PROJECT_CHANGED"}
    if(context.filesDir.usableSpace<FoundationPolicy.FREE_RESERVE_BYTES+((movie.durationUs+999999)/1000000)*2_000_000)throw FoundationFailure(ImportCode.STORAGE_LOW)
    if(j.cancelled.get())throw ExportStopped();o.phase(requireNotNull(j.id),"EXPORTING")
    main.post {
     if(active!==j||j.cancelled.get()){finish(j,j.stopState);return@post}
     try{j.stage="TRANSFORMING";j.transformer=ProductNativeComposition.start(context,prepared,o.partial(requireNotNull(j.id)),object:Transformer.Listener {
      override fun onCompleted(composition:Composition,exportResult:ExportResult){if(active!==j||j.finalized.get())return;j.transformer=null;j.stage="AUDITING";main.removeCallbacks(tick);emit(mapOf("state" to "EXPORTING","active" to true,"progressPercent" to null,"phase" to "AUDITING","sampleCount" to null,"outBytes" to o.partial(requireNotNull(j.id)).length()))
       ProductWork.executor.execute{try{val id=requireNotNull(j.id);val tracks=ProductOutputAudit.inspect(o.partial(id),j.cancelled);if(j.cancelled.get())throw ExportStopped();o.publish(id,tracks,gap){j.cancelled.get()};finish(j,"EXPORT_READY",exportResult.videoFrameCount)}catch(e:Exception){finish(j,errorState(j,e))}}
      }
      override fun onError(composition:Composition,exportResult:ExportResult,exportException:ExportException){j.transformer=null;if(!j.cancelled.get()){j.reasonCode="EXPORT_FAILED";j.error=listOfNotNull(exportException.errorCodeName,exportException.message,exportException.cause?.message).joinToString(":")};finish(j,if(j.cancelled.get())j.stopState else "EXPORT_FAILED")}
     });main.post(tick)}catch(_:Exception){finish(j,if(j.cancelled.get())j.stopState else "EXPORT_FAILED")}
    }
   }catch(e:Exception){finish(j,errorState(j,e))}
  };return true
 }
 private fun errorState(j:Job,e:Exception):String {
  j.error=e.message
  j.reasonCode=when{e is FoundationFailure->e.code.name;e.message=="AUDIO_GAPLESS_METADATA_UNSUPPORTED"->"UNSUPPORTED_MEDIA";e.message=="PROJECT_CHANGED"->"PROJECT_CHANGED";e.message=="SOURCE_UNREADABLE"->"ACCESS_UNREADABLE";else->"EXPORT_FAILED"}
  return if(j.cancelled.get()||e is ExportStopped)j.stopState else if(e is FoundationFailure&&e.code==ImportCode.STORAGE_LOW)"STORAGE_LOW"else "EXPORT_FAILED"
 }
 private val tick=object:Runnable {override fun run(){val j=active?:return;val t=j.transformer?:return
  val holder=ProgressHolder();val available=t.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE
  val bytes=j.id?.let{outputs().partial(it).length()}?:0L
  emit(mapOf("state" to if(j.cancelled.get())"CANCEL_REQUESTED"else"EXPORTING","active" to true,"progressPercent" to if(available)holder.progress else null,"outBytes" to bytes,"sampleCount" to null,"progressSource" to "TRANSFORMER_PERCENT_NOT_SAMPLE_PTS"))
  if(context.filesDir.usableSpace<FoundationPolicy.FREE_RESERVE_BYTES){requestStop("STORAGE_LOW");return};main.postDelayed(this,250)
 }}
 fun requestStop(state:String="EXPORT_CANCELLED") {if(Looper.myLooper()!=Looper.getMainLooper()){main.post{requestStop(state)};return};val j=active?:return;if(j.finalized.get())return;j.stopState=state;j.cancelled.set(true);emit(mapOf("state" to "CANCEL_REQUESTED","active" to true));main.removeCallbacks(tick)
  // Decode polls cancel; no unsafe worker interruption/abandoning a still-open output.
  val t=j.transformer;if(t!=null){try{t.cancel()}finally{j.transformer=null;finish(j,state)}}
 }
 private fun finish(j:Job,state:String,frames:Int?=null) {
  if(!j.finalized.compareAndSet(false,true))return
  ProductWork.executor.execute {
   var finalState=state
   try{val o=outputs();j.id?.let{if(state!="EXPORT_READY")o.stop(it,state)};j.prepared?.let{workspaces().release(it.workingDirectory)}
    if(j.id!=null)finalState=o.status()?.getProperty("state")?:state
    val result=mapOf("state" to finalState,"active" to false,"lastGood" to latest(o.latest()),"decodeVerified" to false,"renderedVideoFrameCount" to frames,"reasonCode" to j.reasonCode,"error" to j.error)
    main.post{if(active===j){active=null;main.removeCallbacks(tick);ProductWork.release(j.lease);emit(result)}}
   }catch(_:Exception){main.post{if(active===j){active=null;main.removeCallbacks(tick);ProductWork.release(j.lease);emit(mapOf("state" to "EXPORT_FAILED","active" to false,"cleanupPending" to true))}}}
  }
 }
}
