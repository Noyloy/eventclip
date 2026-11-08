package expo.modules.eventcliprender.realmedia
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.transformer.CompositionPlayer
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import expo.modules.kotlin.viewevent.EventDispatcher
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ProductPreviewView(context:Context,private val expoContext:AppContext):ExpoView(context,expoContext) {
 private val onPreviewState by EventDispatcher()
 private val onProjectChange by EventDispatcher()
 private val main=Handler(Looper.getMainLooper())
 private val surface=SurfaceView(context).apply{layoutDirection=View.LAYOUT_DIRECTION_LTR;setZOrderOnTop(false)}
 private var owner=UUID.randomUUID().toString()
 private var player:CompositionPlayer?=null
 private var prepared:PreparedProductComposition?=null
 private var cancelled=AtomicBoolean(false)
 @Volatile private var generation=0
 private var selected=""
 private var revision=""
 private var exportId="";private var exportDurationUs=0L
 private var markBusy=false
 private data class MarkWrite(val action:String,val rangeId:String,val at:Long?,val asset:String,val project:BirthdayProject)
 private var failedWrite:MarkWrite?=null
 private var rendered=false
 private var seeking=false
 private var attached=false
 init{addView(surface,FrameLayout.LayoutParams(-1,-1));layoutDirection=View.LAYOUT_DIRECTION_LTR}
 private fun store()=BirthdayProjectStore(File(context.filesDir,"eventclip-birthday"))
 private fun spoken(us:Long,language:String):String {
  val seconds=(us.coerceAtLeast(0)/1000000);val fmt=android.icu.text.MeasureFormat.getInstance(java.util.Locale.forLanguageTag(language),android.icu.text.MeasureFormat.FormatWidth.WIDE)
  return if(seconds<60)fmt.formatMeasures(android.icu.util.Measure(seconds,android.icu.util.MeasureUnit.SECOND))else fmt.formatMeasures(android.icu.util.Measure(seconds/60,android.icu.util.MeasureUnit.MINUTE),android.icu.util.Measure(seconds%60,android.icu.util.MeasureUnit.SECOND))
 }
 private fun state(code:String="") {
  val p=player;val item=prepared?.moviePlan?.items?.singleOrNull{it.id==selected}
  val ready=p?.playbackState==Player.STATE_READY
  val local=item?.let{PreviewClock.clipTimeUs(p?.currentPosition?:-1,ready,rendered,seeking,it.startUs,it.durationUs)}
  onPreviewState(mapOf("state" to if(code.isNotEmpty())code else if(ready)"READY"else if(p?.playbackState==Player.STATE_ENDED)"PREVIEW_ENDED"else"PREPARING","positionUs" to ((p?.currentPosition?:0)*1000),"positionSpokenEn" to spoken((p?.currentPosition?:0)*1000,"en"),"positionSpokenHe" to spoken((p?.currentPosition?:0)*1000,"he"),"durationSpokenEn" to spoken(prepared?.moviePlan?.durationUs?:exportDurationUs,"en"),"durationSpokenHe" to spoken(prepared?.moviePlan?.durationUs?:exportDurationUs,"he"),"durationUs" to (prepared?.moviePlan?.durationUs?:exportDurationUs),"markBusy" to markBusy,"markFailed" to (failedWrite!=null),"clipPositionUs" to (local?:-1L),"canEndMark" to (item?.let{PreviewClock.clipTimeUs(p?.currentPosition?:-1,ready||p?.playbackState==Player.STATE_ENDED,rendered,seeking,it.startUs,it.durationUs,true)}!=null && prepared?.audioPlan?.originals?.any{it.assetId==selected}==true),"canMark" to (local!=null && prepared?.audioPlan?.originals?.any{it.assetId==selected}==true)))
 }
 private val tick=object:Runnable{override fun run(){if(player!=null){state();main.postDelayed(this,200)}}}
 fun setExportId(id:String){if(exportId==id)return;stop();exportId=id;exportDurationUs=0}
 fun setAsset(id:String){if(selected==id)return;selected=id;seekSelected()}
 fun setRevision(value:String){if(revision==value)return;revision=value;stop("PREVIEW_STOPPED")}
 private fun seekSelected(){val p=player?:return;val item=prepared?.moviePlan?.items?.singleOrNull{it.id==selected}?:return;seeking=true;rendered=false;p.seekTo(item.startUs/1000);state()}
 private fun cleanup(work:PreparedProductComposition?){work?.let{val dir=it.workingDirectory;require(dir.parentFile?.name=="eventclip-birthday-render" && dir.absoluteFile==dir.canonicalFile);ProductWorkspaces(requireNotNull(dir.parentFile)).release(dir)}}
 fun stop(code:String="PREVIEW_STOPPED") {
  val session=owner;generation++;cancelled.set(true);main.removeCallbacks(tick);player?.release();player=null;rendered=false;seeking=false;markBusy=false;failedWrite=null
  val work=prepared;prepared=null
  ProductWork.executor.execute{cleanup(work);ProductWork.release(session)}
  if(attached)state(code)
 }
 private fun prepareRetainedAndPlay(){
  stop();val epoch=generation;val session=UUID.randomUUID().toString();owner=session;val id=exportId;state("PREPARING")
  ProductWork.executor.execute{if(!ProductWork.acquire(session)){main.post{if(epoch==generation)state("PREVIEW_BUSY")};return@execute}
   try{val outputs=ProductExportStore(File(context.filesDir,"eventclip-birthday-exports"));val file=outputs.verifiedFile(id)
    val record=ProductPrivateFiles.read(ProductPrivateFiles.child(requireNotNull(file.parentFile),"$id.properties"));val duration=record.getProperty("durationUs").toLong()
    main.post{if(!attached||epoch!=generation){ProductWork.release(session);return@post}
     try{exportDurationUs=duration;val p=CompositionPlayer.Builder(context).setLooper(Looper.getMainLooper()).build();player=p
      p.addListener(object:Player.Listener{override fun onPlaybackStateChanged(state:Int){state()};override fun onRenderedFirstFrame(){rendered=true;seeking=false;state()};override fun onPlayerError(error:PlaybackException){stop("PREVIEW_FAILED")}})
      p.setVideoSurfaceView(surface);p.setComposition(Composition.Builder(EditedMediaItemSequence.Builder(listOf(EditedMediaItem.Builder(MediaItem.fromUri(file.toURI().toString())).build())).build()).build());p.prepare();p.play();main.post(tick)
     }catch(_:Exception){stop("PREVIEW_FAILED")}}
   }catch(_:Exception){ProductWork.release(session);main.post{if(epoch==generation&&attached)state("PREVIEW_FAILED")}}
  }
 }
 private fun prepareAndPlay(){
  stop();val epoch=generation;val session=UUID.randomUUID().toString();owner=session;val flag=AtomicBoolean(false);cancelled=flag;state("PREPARING")
  ProductWork.executor.execute {
   if(!ProductWork.acquire(session)){main.post{if(epoch==generation)state("PREVIEW_BUSY")};return@execute}
   var work:PreparedProductComposition?=null
   try{val s=store();val project=s.load();val song=project.assets.single{it.kind=="song"};val movie=FoundationPolicy.plan(s.ordered(project).filter{it.kind!="song"}.map{VisualInput(it.id,it.kind,it.durationUs,it.included)},song.durationUs,emptyList())
    work=ProductNativeComposition.prepare(context,s,movie,flag,true)
    val readyWork=work
    main.post {
     if(!attached||epoch!=generation||flag.get()){ProductWork.executor.execute{cleanup(readyWork);ProductWork.release(session)};return@post}
     try{prepared=readyWork;val p=CompositionPlayer.Builder(context).setLooper(Looper.getMainLooper()).build();player=p
      p.addListener(object:Player.Listener {
       override fun onPlaybackStateChanged(playbackState:Int){state();if(playbackState==Player.STATE_ENDED)state("PREVIEW_ENDED")}
       override fun onRenderedFirstFrame(){rendered=true;seeking=false;state()}
       override fun onPlayerError(error:PlaybackException){stop("PREVIEW_FAILED")} // no platform message/URI exposed
      })
      p.setVideoSurfaceView(surface);p.setComposition(readyWork.composition);p.prepare();seekSelected();p.play();main.post(tick)
     }catch(_:Exception){stop("PREVIEW_FAILED")}
    }
   }catch(e:Exception){cleanup(work);ProductWork.release(session);main.post{if(epoch==generation&&attached)state(if(flag.get())"PREVIEW_INTERRUPTED"else if(e.message=="AUDIO_GAPLESS_METADATA_UNSUPPORTED")"UNSUPPORTED_MEDIA"else"PREVIEW_FAILED")}}
  }
 }
 private fun mark(action:String,rangeId:String="") {
  if(markBusy)return
  val p=player;val item=prepared?.moviePlan?.items?.singleOrNull{it.id==selected}
  val at=if(action in setOf("start","end"))item?.let{PreviewClock.clipTimeUs(p?.currentPosition?:-1,(p?.playbackState==Player.STATE_READY || action=="end"&&p?.playbackState==Player.STATE_ENDED),rendered,seeking,it.startUs,it.durationUs,action=="end")}else null
  if(action in setOf("start","end") && (at==null || prepared?.audioPlan?.originals?.none{it.assetId==selected}!=false)){state("PREVIEW_NOT_READY");return}
  p?.pause();val asset=selected;val epoch=generation;markBusy=true;failedWrite=null;state()
  ProductWork.executor.execute{try{val write=MarkWrite(action,rangeId,at,asset,store().load());main.post{if(epoch==generation)applyMark(write,epoch)else markBusy=false}}catch(_:Exception){main.post{markBusy=false;state("PREVIEW_MARK_FAILED")}}}
 }
 private fun applyMark(write:MarkWrite,epoch:Int){
  markBusy=true;state()
  ProductWork.executor.execute{try{
   check(epoch==generation);check(!ProductWork.busy()||ProductWork.owns(owner));val s=store();val project=s.load();require(project.id==write.project.id)
   // If commit succeeded but acknowledgement failed, retry is idempotent and never duplicates/removes other ranges.
   val applied=when(write.action){"start"->project==write.project.copy(pendingVoiceStart=PendingVoiceStart(write.asset,requireNotNull(write.at)));"end"->{val pending=write.project.pendingVoiceStart;pending!=null&&project.pendingVoiceStart==null&&project.voiceRanges.size==write.project.voiceRanges.size+1&&project.voiceRanges.take(write.project.voiceRanges.size)==write.project.voiceRanges&&project.voiceRanges.last().let{it.assetId==pending.assetId&&it.startUs==pending.startUs&&it.endUs==write.at}};"discard"->project==write.project.copy(pendingVoiceStart=null);"remove"->project==write.project.copy(voiceRanges=write.project.voiceRanges.filterNot{it.id==write.rangeId});else->false}
   if(!applied){require(project==write.project){"MARK_STATE_CHANGED"};when(write.action){"start"->s.startVoiceMark(write.asset,requireNotNull(write.at));"end"->{require(project.pendingVoiceStart?.assetId==write.asset);s.finishVoiceMark(requireNotNull(write.at))};"discard"->s.discardPendingVoiceStart();"remove"->s.removeVoiceRange(write.rangeId);else->error("Invalid action")}}
   main.post{if(epoch==generation&&attached){markBusy=false;failedWrite=null;state();onProjectChange(mapOf("changed" to true))}}
  }catch(_:Exception){main.post{if(epoch==generation&&attached){markBusy=false;failedWrite=write;state("PREVIEW_MARK_FAILED")}}}}
 }
 fun command(value:Map<String,Any?>){
  when(value["action"] as? String){
   "play"->{if(player==null){if(exportId.isNotEmpty())prepareRetainedAndPlay()else prepareAndPlay()}else player?.play()}
   "pause"->player?.pause()
   "stop"->stop()
   "seek"->{val p=player?:return;val delta=(value["deltaMs"] as? Number)?.toLong()?:return;seeking=true;rendered=false;p.seekTo((p.currentPosition+delta).coerceIn(0,(prepared?.moviePlan?.durationUs?:exportDurationUs)/1000));state()}
   "retry"->{if(!markBusy)failedWrite?.let{applyMark(it,generation)}}
   "start","end","discard","remove"->mark(value["action"] as String,value["rangeId"] as? String?:"")
  }
 }
 override fun onAttachedToWindow(){super.onAttachedToWindow();attached=true;ProductPreviewRegistry.attach(this)}
 override fun onDetachedFromWindow(){attached=false;stop();ProductPreviewRegistry.detach(this);super.onDetachedFromWindow()}
 fun dispose(){attached=false;stop();ProductPreviewRegistry.detach(this)}
}

object ProductPreviewRegistry {
 private val views=java.util.Collections.newSetFromMap(java.util.WeakHashMap<ProductPreviewView,Boolean>())
 fun attach(view:ProductPreviewView){views.add(view)}
 fun detach(view:ProductPreviewView){views.remove(view)}
 fun interrupt(){val action=Runnable{views.toList().forEach{it.stop("PREVIEW_INTERRUPTED")}};if(Looper.myLooper()==Looper.getMainLooper())action.run()else Handler(Looper.getMainLooper()).post(action)}
}
