package expo.modules.eventcliprender.realmedia
import java.io.File
import java.util.Properties
import java.util.UUID

/** Separate from editable project/source cleanup. Pending save journal is the ONLY deletion authority. */
class ProductDeliveryStore(private val root:File,private val checkpoint:(String)->Unit={}) {
 init{require(root.absoluteFile==root.canonicalFile);check(root.mkdirs()||root.isDirectory)}
 private fun child(name:String)=ProductPrivateFiles.child(root,name)
 private val journal get()=child("save-job.properties")
 private fun infoFile(id:String):File{ProductPrivateFiles.id(id);return child("delivery-$id.properties")}
 fun info(id:String):Properties=if(infoFile(id).exists())ProductPrivateFiles.read(infoFile(id)).also{require(it.getProperty("exportId")==id)}else Properties().apply{setProperty("exportId",id);setProperty("infoShown","false");setProperty("firstShareContinued","false");setProperty("saveState","NOT_SAVED")}
 private fun writeInfo(id:String,p:Properties){require(p.getProperty("exportId")==id);ProductPrivateFiles.atomic(infoFile(id),p)}
 fun informationPresented(id:String){val p=info(id);p.setProperty("infoShown","true");writeInfo(id,p)}
 fun firstShareContinued(id:String){val p=info(id);p.setProperty("firstShareContinued","true");writeInfo(id,p)}
 fun begin(source:SaveSource):Properties {
  require(job()?.getProperty("phase") in listOf(null,"DONE"));ProductPrivateFiles.id(source.exportId);require(source.bytes>0&&source.sha256.matches(Regex("[a-f0-9]{64}")))
  val j=Properties().apply{setProperty("schema","product-save-v1");setProperty("saveId",UUID.randomUUID().toString());setProperty("exportId",source.exportId);setProperty("bytes",source.bytes.toString());setProperty("sha256",source.sha256);setProperty("phase","INSERTING")}
  ProductPrivateFiles.atomic(journal,j);checkpoint("SAVE_AFTER_BEGIN");return j
 }
 fun job():Properties?=if(!journal.exists())null else ProductPrivateFiles.read(journal).also{j->require(j.getProperty("schema")=="product-save-v1");ProductPrivateFiles.id(j.getProperty("saveId"));ProductPrivateFiles.id(j.getProperty("exportId"));require(j.getProperty("bytes").toLong()>0&&j.getProperty("sha256").matches(Regex("[a-f0-9]{64}")));require(j.getProperty("phase") in listOf("INSERTING","COPYING","PUBLISHING","DONE"))}
 fun update(j:Properties,phase:String,token:String?=null){require(phase in listOf("COPYING","PUBLISHING"));require(job()?.getProperty("saveId")==j.getProperty("saveId"));j.setProperty("phase",phase);token?.let{j.setProperty("row",it)};ProductPrivateFiles.atomic(journal,j);checkpoint("SAVE_AFTER_$phase")}
 fun saved(j:Properties,token:String){val id=j.getProperty("exportId");val p=info(id);p.setProperty("saveState","SAVED");p.setProperty("savedUri",token);p.setProperty("savedBytes",j.getProperty("bytes"));p.setProperty("savedSha256",j.getProperty("sha256"));p.setProperty("savedSaveId",j.getProperty("saveId"));writeInfo(id,p);checkpoint("SAVE_AFTER_METADATA");j.setProperty("phase","DONE");j.setProperty("result","SAVED");ProductPrivateFiles.atomic(journal,j)}
 fun failed(j:Properties){val id=j.getProperty("exportId");val p=info(id);if(p.getProperty("saveState")!="SAVED"){p.setProperty("saveState","SAVE_FAILED");writeInfo(id,p)};j.setProperty("phase","DONE");j.setProperty("result","SAVE_FAILED");ProductPrivateFiles.atomic(journal,j)}
}
data class SaveSource(val exportId:String,val file:File,val bytes:Long,val sha256:String)
data class PendingSaveRow(val token:String,val saveId:String,val pending:Boolean)
interface SaveOutput:java.io.Closeable {val stream:java.io.OutputStream;fun sync()}
interface SaveDestination {
 fun freeBytes():Long
 fun insert(saveId:String):String
 fun find(saveId:String,token:String?):PendingSaveRow?
 fun open(row:PendingSaveRow):SaveOutput
 fun openRead(row:PendingSaveRow):java.io.InputStream
 fun publish(row:PendingSaveRow)
 /** Must predicate deletion on CURRENT IS_PENDING=1 and exact registered identity. Never delete committed public rows. */
 fun deletePending(row:PendingSaveRow)
}
class SaveCancelled:Exception("SAVE_CANCELLED")
