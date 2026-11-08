package expo.modules.eventcliprender.realmedia
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** No external send: chooser opens ONLY after explicit Share/Continue tap, never reports delivery. */
class ProductDeliveryController(context:Context,private val emit:(Map<String,Any?>)->Unit) {
 private val context=context.applicationContext;private val main=Handler(Looper.getMainLooper())
 @Volatile private var foreground=true;@Volatile private var generation=0
 @Volatile private var activeSaveId:String?=null
 private val busy=AtomicBoolean(false);@Volatile private var cancelled=AtomicBoolean(false)
 private fun root()=File(context.filesDir,"eventclip-birthday-exports")
 private fun metadata()=ProductDeliveryStore(root())
 private fun source(id:String):SaveSource {val file=ProductExportStore(root()).verifiedFile(id);val record=ProductPrivateFiles.read(ProductPrivateFiles.child(root(),"$id.properties"));return SaveSource(id,file,record.getProperty("bytes").toLong(),record.getProperty("sha256"))}
 private fun transaction()=ProductSaveTransaction(metadata(),ProductMediaStore(context))
 private fun payload(id:String):Map<String,Any?> {val p=metadata().info(id);val source=source(id);return mapOf("exportId" to id,"bytes" to source.bytes,"saveState" to p.getProperty("saveState"),"savedUri" to p.getProperty("savedUri"),"infoShown" to (p.getProperty("infoShown")=="true"),"firstShareContinued" to (p.getProperty("firstShareContinued")=="true"),"active" to busy.get(),"saveSupported" to (Build.VERSION.SDK_INT>=29))}
 fun info(id:String):Map<String,Any?> {if(!busy.get()&&Build.VERSION.SDK_INT>=29)transaction().recover();return payload(id)}
 fun informationPresented(id:String):Map<String,Any?>{source(id);metadata().informationPresented(id);return payload(id)}
 fun cancel(){cancelled.set(true);activeSaveId?.let{id->if(busy.get())emit(mapOf("exportId" to id,"saveState" to "SAVING","active" to true,"reasonCode" to "CANCEL_REQUESTED"))}}
 fun background(){foreground=false;generation++;cancel()}
 fun foreground(){foreground=true}
 fun save(id:String):Boolean {
  if(!foreground||!busy.compareAndSet(false,true))return false;val flag=AtomicBoolean(false);cancelled=flag;activeSaveId=id;val lease=UUID.randomUUID().toString()
  // Serialized behind preview release; immutable MP4 is unchanged, no concurrent render/copy job.
  ProductWork.executor.execute {
   var acquired=false;var state="SAVE_FAILED";var reason=""
   try{check(ProductWork.acquire(lease));acquired=true;require(Build.VERSION.SDK_INT>=29);val s=source(id)
    emit(mapOf("exportId" to id,"saveState" to "SAVING","active" to true,"bytes" to s.bytes,"writtenBytes" to 0L))
    var last=0L;state=transaction().save(s,flag){n->if(n==s.bytes||n-last>=1048576){last=n;emit(mapOf("exportId" to id,"saveState" to "SAVING","active" to true,"bytes" to s.bytes,"writtenBytes" to n))}}
   }catch(e:Exception){reason=if(flag.get())"SAVE_CANCELLED"else if(e is FoundationFailure&&e.code==ImportCode.STORAGE_LOW)"STORAGE_LOW"else "SAVE_FAILED"}
   finally{activeSaveId=null;busy.set(false);if(acquired)ProductWork.release(lease);val result=try{payload(id)+mapOf("saveState" to state,"active" to false,"reasonCode" to if(metadata().job()?.getProperty("phase") !in listOf(null,"DONE"))"STATUS_UNKNOWN"else reason,"cleanupPending" to (metadata().job()?.getProperty("phase") !in listOf(null,"DONE")))}catch(_:Exception){mapOf("exportId" to id,"saveState" to "SAVE_FAILED","active" to false,"reasonCode" to reason)};emit(result)}
  };return true
 }
 fun share(id:String,continueFirst:Boolean,activity:Activity?,result:(Map<String,Any?>)->Unit){
  val epoch=generation
  if(!busy.compareAndSet(false,true)){result(mapOf("state" to "BUSY"));return};val lease=UUID.randomUUID().toString()
  ProductWork.executor.execute {var acquired=false
   try{check(ProductWork.acquire(lease));acquired=true;val s=source(id);val info=metadata().info(id)
    if(info.getProperty("firstShareContinued")!="true"&&!continueFirst){busy.set(false);ProductWork.release(lease);main.post{result(mapOf("state" to "NEEDS_NOTICE","bytes" to s.bytes))};return@execute}
    require(epoch==generation&&foreground&&activity!=null&&!activity.isFinishing&&!activity.isDestroyed)
    val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",s.file,"EventClip.mp4")
    val send=Intent(Intent.ACTION_SEND).apply{type="video/mp4";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri("",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
    main.post{try{require(epoch==generation&&foreground&&!activity.isFinishing&&!activity.isDestroyed);val chooser=Intent.createChooser(send,null).apply{clipData=send.clipData;addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
      // Explicit Continue is presentation-state only, not consent/license. Persist before launching.
      ProductWork.executor.execute{try{if(continueFirst)metadata().firstShareContinued(id);main.post{try{require(epoch==generation&&foreground&&!activity.isFinishing&&!activity.isDestroyed);activity.startActivity(chooser);result(mapOf("state" to "CHOOSER_OPENED","bytes" to s.bytes,"recipientDeliveryVerified" to false))}catch(_:Exception){result(mapOf("state" to "SHARE_FAILED"))}finally{busy.set(false);ProductWork.release(lease)}}}catch(_:Exception){busy.set(false);ProductWork.release(lease);main.post{result(mapOf("state" to "SHARE_FAILED"))}}}
     }catch(_:Exception){busy.set(false);ProductWork.release(lease);result(mapOf("state" to "SHARE_FAILED"))}}
   }catch(_:Exception){busy.set(false);if(acquired)ProductWork.release(lease);main.post{result(mapOf("state" to "SHARE_FAILED"))}}
  }
 }
}
