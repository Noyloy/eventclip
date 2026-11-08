package expo.modules.eventcliprender.realmedia

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import java.io.File
import java.util.concurrent.Executors

/** Only picker-selected content is touched, on device. No export/render API in this foundation module. */
class BirthdayImportsModule:Module() {
  private val worker=ProductWork.executor
  private var importer:BirthdayImporter?=null
  private var exportController:ProductExportController?=null
  private var deliveryController:ProductDeliveryController?=null
  @Synchronized private fun deliveries()=deliveryController ?: ProductDeliveryController(context()){runCatching{sendEvent("onDeliveryState",it)}}.also{deliveryController=it}
  @Synchronized private fun exports()=exportController ?: ProductExportController(context()){runCatching{sendEvent("onExportState",it)}}.also{exportController=it}
  @Volatile private var busy=false
  private var pending:Promise?=null
  private var kind="visual"
  private var replaceCurrent=false
  private val requestCode=48271
  private fun context():Context=appContext.reactContext ?: error("No context")
  private fun store()=BirthdayProjectStore(File(context().filesDir,"eventclip-birthday"))
  private fun payload(s:BirthdayProjectStore,p:BirthdayProject):Map<String,Any?> {
    val ordered=s.ordered(p)
    return mapOf("projectId" to p.id,"keepDateOrder" to p.keepDateOrder,"totalSourceBytes" to p.assets.sumOf{it.bytes},"foundationOnly" to true,"recoveredInterruptedImport" to s.recoveredInterruptedImport,"pendingCleanup" to s.pendingCleanup,
      "voiceRanges" to p.voiceRanges.map{mapOf("id" to it.id,"assetId" to it.assetId,"startUs" to it.startUs,"endUs" to it.endUs)},
      "pendingVoiceStart" to p.pendingVoiceStart?.let{mapOf("assetId" to it.assetId,"startUs" to it.startUs)},
      "assets" to ordered.map { a -> mapOf("id" to a.id,"kind" to a.kind,"bytes" to a.bytes,"mime" to a.mime,"durationMs" to a.durationMs,"durationUs" to a.durationUs,"width" to a.width,"height" to a.height,"rotation" to a.rotation,"included" to a.included,"state" to if(s.file(a.id).isFile) "READY" else "ACCESS_UNREADABLE") })
  }
  private fun task(promise:Promise, action:(BirthdayProjectStore)->BirthdayProject) {
    if(busy || ProductWork.busy()){promise.resolve(mapOf("ok" to false,"code" to "COPY_FAILED"));return}
    worker.execute {try {check(!ProductWork.busy());val s=store();promise.resolve(mapOf("ok" to true,"project" to payload(s,action(s))))}catch(_:Exception){promise.resolve(mapOf("ok" to false,"code" to "COPY_FAILED"))}}
  }
  private fun launchPicker(selection:String,promise:Promise,replacement:Boolean=false) {
      if(selection !in setOf("visual","song") || busy || ProductWork.busy()){promise.resolve(mapOf("ok" to false,"code" to "COPY_FAILED"));return}
      busy=true;pending=promise;kind=selection;replaceCurrent=replacement
      val activity=appContext.currentActivity
      if(activity==null){busy=false;pending=null;promise.resolve(mapOf("ok" to false,"code" to "ACCESS_UNREADABLE"));return}
      activity.runOnUiThread {
        try {
          val intent=if(selection=="visual") {
            ActivityResultContracts.PickMultipleVisualMedia(FoundationPolicy.MAX_VISUAL).createIntent(context(),PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo).build())
          } else Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="audio/*"}
          intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          intent.putExtra(Intent.EXTRA_LOCAL_ONLY,true) // advisory: OS picker/provider may still retrieve remote-backed selections
          activity.startActivityForResult(intent,requestCode)
        }catch(_:Exception){busy=false;pending=null;promise.resolve(mapOf("ok" to false,"code" to "ACCESS_UNREADABLE"))}
      }
  }
  override fun definition()=ModuleDefinition {
    Name("BirthdayImports")
    View(ProductPreviewView::class) {
      Events("onPreviewState","onProjectChange")
      Prop("exportId") { view:ProductPreviewView,id:String -> view.setExportId(id) }
      Prop("assetId") { view:ProductPreviewView,id:String -> view.setAsset(id) }
      Prop("revision") { view:ProductPreviewView,value:String -> view.setRevision(value) }
      Prop("command") { view:ProductPreviewView,value:Map<String,Any?> -> view.command(value) }
      OnViewDestroys { view:ProductPreviewView -> view.dispose() }
    }
    Events("onImportState","onExportState","onDeliveryState")
    OnActivityEntersForeground { deliveryController?.foreground() }
    OnActivityEntersBackground { ProductPreviewRegistry.interrupt();exportController?.requestStop("EXPORT_INTERRUPTED");deliveryController?.background() }
    OnActivityDestroys { ProductPreviewRegistry.interrupt();exportController?.requestStop("EXPORT_INTERRUPTED");deliveryController?.background() }
    AsyncFunction("loadProject") { promise:Promise -> worker.execute{try{val s=store();promise.resolve(mapOf("ok" to true,"project" to payload(s,s.load())))}catch(_:Exception){promise.resolve(mapOf("ok" to false,"code" to "COPY_FAILED"))}} }
    AsyncFunction("getDeliveryState") { id:String,promise:Promise -> worker.execute{try{promise.resolve(deliveries().info(id))}catch(_:Exception){promise.resolve(mapOf("exportId" to id,"saveState" to "SAVE_FAILED","active" to false,"cleanupPending" to true,"reasonCode" to "STATUS_UNKNOWN"))}} }
    AsyncFunction("informationPresented") { id:String,promise:Promise -> worker.execute{try{promise.resolve(deliveries().informationPresented(id))}catch(_:Exception){promise.resolve(mapOf("exportId" to id,"code" to "STATE_FAILED"))}} }
    AsyncFunction("saveExport") { id:String,promise:Promise -> android.os.Handler(android.os.Looper.getMainLooper()).post{if(busy){promise.resolve(mapOf("ok" to false));return@post};ProductPreviewRegistry.interrupt();promise.resolve(mapOf("ok" to deliveries().save(id)))} }
    AsyncFunction("cancelSave") { deliveryController?.cancel();mapOf("ok" to true) }
    AsyncFunction("shareExport") { id:String,continueFirst:Boolean,promise:Promise -> android.os.Handler(android.os.Looper.getMainLooper()).post{if(busy){promise.resolve(mapOf("state" to "BUSY"));return@post};ProductPreviewRegistry.interrupt();deliveries().share(id,continueFirst,appContext.currentActivity){promise.resolve(it)}} }
    AsyncFunction("getExportState") { promise:Promise -> worker.execute{try{promise.resolve(exports().status())}catch(_:Exception){promise.resolve(mapOf("state" to "EXPORT_FAILED","active" to false,"cleanupPending" to true))}} }
    AsyncFunction("createExport") { projectId:String,token:String,promise:Promise -> android.os.Handler(android.os.Looper.getMainLooper()).post{if(busy)promise.resolve(mapOf("ok" to false,"code" to "PRODUCT_MEDIA_BUSY"))else promise.resolve(mapOf("ok" to exports().create(projectId,token),"code" to "PRODUCT_MEDIA_BUSY"))} }
    AsyncFunction("cancelExport") { exportController?.requestStop();mapOf("ok" to true) }
    AsyncFunction("includeAsset") { id:String,included:Boolean,promise:Promise -> task(promise){it.include(id,included)} }
    AsyncFunction("removeAsset") { id:String,confirmation:String,promise:Promise -> task(promise){it.remove(id,confirmation)} }
    AsyncFunction("moveAsset") { id:String,delta:Int,promise:Promise -> task(promise){it.move(id,delta)} }
    AsyncFunction("startVoiceMark") { id:String,atUs:Double,promise:Promise -> task(promise){require(atUs.isFinite() && atUs==atUs.toLong().toDouble());it.startVoiceMark(id,atUs.toLong())} }
    AsyncFunction("finishVoiceMark") { atUs:Double,promise:Promise -> task(promise){require(atUs.isFinite() && atUs==atUs.toLong().toDouble());it.finishVoiceMark(atUs.toLong())} }
    AsyncFunction("discardPendingVoiceStart") { promise:Promise -> task(promise){it.discardPendingVoiceStart()} }
    AsyncFunction("removeVoiceRange") { id:String,promise:Promise -> task(promise){it.removeVoiceRange(id)} }
    AsyncFunction("setDateOrder") { enabled:Boolean,promise:Promise -> task(promise){it.dateOrder(enabled)} }
    AsyncFunction("deleteProject") { confirmation:String,promise:Promise -> task(promise){it.deleteProject(confirmation);it.load()} }
    AsyncFunction("cancelImport") { importer?.cancelPending();mapOf("ok" to true,"state" to "CANCEL_REQUESTED") }
    AsyncFunction("planProject") { beats:List<Double>,promise:Promise ->
      worker.execute {
        try {
          val s=store();val p=s.load();if(p.pendingVoiceStart!=null){promise.resolve(mapOf("ok" to false,"code" to "INCOMPLETE_VOICE_RANGE"));return@execute};if(p.assets.filter{it.included || it.kind=="song"}.any{!s.file(it.id).isFile || s.file(it.id).length()!=it.bytes})throw FoundationFailure(ImportCode.ACCESS_UNREADABLE);val song=p.assets.singleOrNull{it.kind=="song"} ?: throw FoundationFailure(ImportCode.SONG_TOO_SHORT)
          val plan=FoundationPolicy.plan(s.ordered(p).filter{it.kind!="song"}.map{VisualInput(it.id,it.kind,it.durationUs,it.included)},song.durationUs,beats.filter{it.isFinite() && it>=0}.map{(it*1000000).toLong()})
          promise.resolve(mapOf("ok" to true,"state" to "READY_TO_CREATE","durationSec" to plan.durationSec,"durationUs" to plan.durationUs,"displaySecondsCeil" to plan.displaySecondsCeil,"musicEndsAtMovieEnd" to plan.musicEndsAtMovieEnd,"musicOnlyFadeStartSec" to plan.musicFadeStartSec,"items" to plan.items.map{mapOf("id" to it.id,"startSec" to it.startSec,"durationSec" to it.durationSec)},"renderImplemented" to true,"projectId" to p.id,"planToken" to ProductExportPolicy.fingerprint(p)))
        }catch(e:FoundationFailure){promise.resolve(mapOf("ok" to false,"code" to e.code.name))}catch(_:Exception){promise.resolve(mapOf("ok" to false,"code" to "DURATION_LIMIT"))}
      }
    }
    AsyncFunction("pickAndImport") { selection:String,promise:Promise -> launchPicker(selection,promise) }
    AsyncFunction("replaceProject") { confirmation:String,promise:Promise ->
      if(confirmation!="REPLACE_PROJECT")promise.resolve(mapOf("ok" to false,"code" to "IMPORT_CANCELLED"))
      else launchPicker("visual",promise,true)
    }
    OnActivityResult { _, result ->
      if(result.requestCode==requestCode) {
        val promise=pending
        pending=null
        if(promise!=null){
          if(result.resultCode!=Activity.RESULT_OK){busy=false;promise.resolve(mapOf("ok" to false,"code" to "IMPORT_CANCELLED"))}
          else {
            val data=result.data
            val uris=mutableListOf<Uri>()
            data?.clipData?.let{c -> for(i in 0 until c.itemCount)uris.add(c.getItemAt(i).uri)}
            if(uris.isEmpty())data?.data?.let{uris.add(it)}
            val selection=kind
            val replacing=replaceCurrent
            worker.execute {
              val results=ArrayList<Map<String,Any?>>()
              var batchStore:BirthdayProjectStore?=null
              try {
                val s=store();val imp=BirthdayImporter(context(),s);importer=imp
                if(uris.isEmpty())throw FoundationFailure(ImportCode.ACCESS_UNREADABLE)
                if(replacing){
                  var known=0L
                  for(uri in uris.distinct()) {
                    if(imp.cancelled.get())throw FoundationFailure(ImportCode.IMPORT_CANCELLED)
                    val size=try{context().contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.SIZE),null,null,null)?.use{c->if(c.moveToFirst() && !c.isNull(0))c.getLong(0)else 0L}?:0L}catch(_:Exception){0L}
                    if(size>0){if(size>FoundationPolicy.MAX_SOURCE_BYTES-known)throw FoundationFailure(ImportCode.SOURCE_SIZE_LIMIT);known+=size}
                  }
                  s.beginImport(true,"REPLACE_PROJECT",known);batchStore=s
                }
                val remaining=FoundationPolicy.MAX_VISUAL-(if(replacing)0 else s.load().assets.count{it.kind!="song"})
                if(selection=="visual" && uris.size>remaining)throw FoundationFailure(ImportCode.ITEM_LIMIT)
                for((i,uri) in uris.distinct().withIndex()) {
                  if(imp.cancelled.get()){results.add(mapOf("selectionIndex" to i,"code" to "IMPORT_CANCELLED"));break}
                  try {
                    imp.importSelected(uri,selection,{state->sendEvent("onImportState",mapOf("selectionIndex" to i,"selectionTotal" to uris.distinct().size,"state" to state))},replacing)
                    results.add(mapOf("selectionIndex" to i,"code" to if(replacing)"CHECKING"else"READY"))
                  }catch(e:FoundationFailure){if(replacing)throw e;results.add(mapOf("selectionIndex" to i,"code" to e.code.name))}
                }
                if(replacing){
                  s.commitImport(imp.cancelled);batchStore=null
                  for(i in results.indices){results[i]=mapOf("selectionIndex" to i,"code" to "READY");sendEvent("onImportState",mapOf("selectionIndex" to i,"selectionTotal" to uris.distinct().size,"state" to "READY"))}
                }
                promise.resolve(mapOf("ok" to results.all{it["code"]=="READY"},"results" to results,"project" to payload(s,s.load())))
              }catch(e:FoundationFailure){batchStore?.abortImport();promise.resolve(mapOf("ok" to false,"code" to e.code.name))}
              catch(_:Exception){batchStore?.abortImport();promise.resolve(mapOf("ok" to false,"code" to "COPY_FAILED"))}
              finally{importer=null;busy=false}
            }
          }
        }
      }
    }
    OnDestroy { deliveryController?.background();exportController?.requestStop("EXPORT_INTERRUPTED");importer?.cancelPending();pending?.resolve(mapOf("ok" to false,"code" to "IMPORT_CANCELLED"));pending=null }
  }
}
