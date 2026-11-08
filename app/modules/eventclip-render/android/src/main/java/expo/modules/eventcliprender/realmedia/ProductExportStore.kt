package expo.modules.eventcliprender.realmedia
import java.io.File
import java.io.RandomAccessFile
import java.util.Properties
import java.util.UUID

/** Serialized journal. Public saved files/project source tree are never addressed here. */
class ProductExportStore(private val root:File,private val checkpoint:(String)->Unit={}) {
 init{require(root.absoluteFile==root.canonicalFile);check(root.mkdirs()||root.isDirectory)}
 private fun child(name:String)=ProductPrivateFiles.child(root,name)
 private val journal get()=child("export-job.properties")
 private fun write(p:Properties)=ProductPrivateFiles.atomic(journal,p)
 fun partial(id:String):File {ProductPrivateFiles.id(id);return child("$id.part.mp4")}
 fun retained(id:String):File {ProductPrivateFiles.id(id);return child("$id.mp4")}
 private fun record(id:String):File {ProductPrivateFiles.id(id);return child("$id.properties")}
 private fun job():Properties?=if(!journal.exists())null else ProductPrivateFiles.read(journal).also{p->require(p.getProperty("schema")=="product-export-v1");ProductPrivateFiles.id(p.getProperty("id"));ProductPrivateFiles.id(p.getProperty("projectId"));require(p.getProperty("fingerprint").matches(Regex("[a-f0-9]{64}")));require(p.getProperty("durationUs").toLong() in 1..FoundationPolicy.MAX_MOVIE_US);require(p.getProperty("phase") in listOf("ACTIVE","VALIDATED","DONE"))}
 fun begin(projectId:String,fingerprint:String,durationUs:Long):String {
  require(job()?.getProperty("phase") in listOf(null,"DONE"));ProductPrivateFiles.id(projectId);require(fingerprint.matches(Regex("[a-f0-9]{64}")));require(durationUs in 1..FoundationPolicy.MAX_MOVIE_US)
  val id=UUID.randomUUID().toString();require(!partial(id).exists()&&!retained(id).exists()&&!record(id).exists())
  write(Properties().apply{setProperty("schema","product-export-v1");setProperty("id",id);setProperty("projectId",projectId);setProperty("fingerprint",fingerprint);setProperty("durationUs",durationUs.toString());setProperty("phase","ACTIVE");setProperty("state","PLANNING")});checkpoint("AFTER_BEGIN");return id
 }
 fun phase(id:String,state:String){require(state in listOf("PLANNING","EXPORTING","CANCEL_REQUESTED"));val j=requireNotNull(job());require(j.getProperty("id")==id&&j.getProperty("phase")=="ACTIVE");j.setProperty("state",state);write(j)}
 fun publish(id:String,tracks:List<ProductTrackSummary>,videoGapUs:Long,cancelled:()->Boolean):Properties {
  val j=requireNotNull(job());require(j.getProperty("id")==id&&j.getProperty("phase")=="ACTIVE")
  ProductExportPolicy.validate(j.getProperty("durationUs").toLong(),tracks,videoGapUs)
  val f=partial(id);require(f.isFile&&f.length()>0);RandomAccessFile(f,"rw").use{it.fd.sync()}
  j.setProperty("sha256",ProductPrivateFiles.hash(f,cancelled));j.setProperty("bytes",f.length().toString());j.setProperty("decodeVerified","false");j.setProperty("trackCount",tracks.size.toString());j.setProperty("maxExpectedVideoGapUs",videoGapUs.toString())
  for((i,t) in tracks.withIndex())for((k,v) in mapOf("mime" to t.mime,"samples" to t.samples,"firstPtsUs" to t.firstPtsUs,"lastPtsUs" to t.lastPtsUs,"maxGapUs" to t.maxGapUs,"durationUs" to t.durationUs,"monotonic" to t.monotonic,"duplicates" to t.duplicates,"width" to t.width,"height" to t.height,"rotation" to t.rotation,"sampleRate" to t.sampleRate,"channels" to t.channels))j.setProperty("track.$i.$k",v.toString())
  if(cancelled())throw ExportStopped()
  j.setProperty("phase","VALIDATED");write(j);checkpoint("AFTER_VALIDATE")
  if(cancelled())throw ExportStopped()
  require(!retained(id).exists());check(f.renameTo(retained(id)));checkpoint("AFTER_RETAIN") // commit point: never delete a validated immutable MP4, even if background/cancel races here
  return finish(j)
 }
 private fun finish(j:Properties):Properties {
  val id=j.getProperty("id");val f=retained(id);require(f.isFile&&f.length()==j.getProperty("bytes").toLong()&&ProductPrivateFiles.hash(f)==j.getProperty("sha256"))
  // Revalidate stored metadata; "validated" phase alone is not trusted.
  require(j.getProperty("trackCount")=="2" && j.getProperty("sha256").matches(Regex("[a-f0-9]{64}")))
  val tracks=(0 until 2).map{i->fun v(k:String)=j.getProperty("track.$i.$k")
   ProductTrackSummary(v("mime"),v("samples").toLong(),v("firstPtsUs").toLong(),v("lastPtsUs").toLong(),v("maxGapUs").toLong(),v("durationUs").toLong(),v("monotonic")=="true",v("duplicates").toInt(),v("width").toInt(),v("height").toInt(),v("rotation").toInt(),v("sampleRate").toInt(),v("channels").toInt())}
  ProductExportPolicy.validate(j.getProperty("durationUs").toLong(),tracks,j.getProperty("maxExpectedVideoGapUs").toLong())
  val result=Properties().apply{putAll(j);setProperty("phase","DONE");setProperty("state","EXPORT_READY");setProperty("decodeVerified","false")}
  ProductPrivateFiles.atomic(record(id),result);checkpoint("AFTER_RECORD")
  ProductPrivateFiles.atomic(child("latest.properties"),Properties().apply{setProperty("id",id)});checkpoint("AFTER_LATEST")
  write(result);return result
 }
 fun stop(id:String,state:String){require(state in listOf("EXPORT_CANCELLED","EXPORT_INTERRUPTED","EXPORT_FAILED","STORAGE_LOW"));val j=job()?:return;require(j.getProperty("id")==id)
  if(j.getProperty("phase")=="VALIDATED"&&retained(id).exists()){finish(j);return}
  // Never remove retained MP4s. Unrecognized final file is an integrity error, not cleanup permission.
  require(!retained(id).exists());val p=partial(id);if(p.exists())check(p.delete());j.setProperty("phase","DONE");j.setProperty("state",state);write(j);checkpoint("AFTER_STOP")
 }
 fun recover(){val j=job()?:return;if(j.getProperty("phase")=="DONE")return;val id=j.getProperty("id");if(j.getProperty("phase")=="VALIDATED"&&retained(id).exists())finish(j)else stop(id,"EXPORT_INTERRUPTED")}
 fun status():Properties?=job()
 fun latest():Properties? {val ref=child("latest.properties");if(!ref.exists())return null;val id=ProductPrivateFiles.read(ref).getProperty("id");val p=ProductPrivateFiles.read(record(id));require(p.getProperty("id")==id&&p.getProperty("state")=="EXPORT_READY"&&p.getProperty("decodeVerified")=="false");val f=retained(id);require(f.isFile&&f.length()==p.getProperty("bytes").toLong());return p} // hash checked before actual playback/share (not every progress poll)
 fun verifiedFile(id:String):File {val p=ProductPrivateFiles.read(record(id));require(p.getProperty("schema")=="product-export-v1"&&p.getProperty("phase")=="DONE"&&p.getProperty("id")==id&&p.getProperty("state")=="EXPORT_READY"&&p.getProperty("trackCount")=="2"&&p.getProperty("decodeVerified")=="false"&&p.getProperty("bytes").toLong()>0&&p.getProperty("sha256").matches(Regex("[a-f0-9]{64}")));return retained(id).also{require(it.isFile&&it.length()>0&&it.length()==p.getProperty("bytes").toLong()&&ProductPrivateFiles.hash(it)==p.getProperty("sha256"))}}
}
class ExportStopped:Exception("EXPORT_CANCELLED")
