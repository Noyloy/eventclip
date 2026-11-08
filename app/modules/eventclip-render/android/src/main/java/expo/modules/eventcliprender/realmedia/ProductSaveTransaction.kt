package expo.modules.eventcliprender.realmedia
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import java.security.MessageDigest

/** Port-testable byte-for-byte copy. Does NOT render/re-encode/modify private MP4. */
class ProductSaveTransaction(private val store:ProductDeliveryStore,private val destination:SaveDestination,private val checkpoint:(String)->Unit={}) {
 private fun matches(row:PendingSaveRow,j:Properties):Boolean {
  require(row.saveId==j.getProperty("saveId"));val expected=j.getProperty("bytes").toLong();var n=0L;val hash=MessageDigest.getInstance("SHA-256")
  destination.openRead(row).use{input->val b=ByteArray(65536);while(true){val k=input.read(b);if(k<0)break;if(k>0){n+=k;if(n>expected)return false;hash.update(b,0,k)}}}
  return n==expected&&hash.digest().joinToString(""){"%02x".format(it)}==j.getProperty("sha256")
 }
 /** No implicit resume. A committed verified copy wins over interruption; otherwise only pending row is deleted. */
 fun recover(){val j=store.job()?:return;if(j.getProperty("phase")=="DONE")return
  val row=destination.find(j.getProperty("saveId"),j.getProperty("row"))
  if(row!=null&&!row.pending){if(matches(row,j))store.saved(j,row.token)else store.failed(j);return} // never delete public data, including changed copies
  if(row!=null)destination.deletePending(row)
  val remaining=destination.find(j.getProperty("saveId"),j.getProperty("row"))
  if(remaining!=null){if(!remaining.pending&&matches(remaining,j)){store.saved(j,remaining.token);return};error("PENDING_CLEANUP_REQUIRED")}
  store.failed(j)
 }
 fun save(source:SaveSource,cancelled:AtomicBoolean,onBytes:(Long)->Unit):String {
  recover();if(store.info(source.exportId).getProperty("saveState")=="SAVED")return "SAVED" // idempotent, no concurrent/duplicate public copies
  require(source.file.isFile&&source.file.length()==source.bytes&&ProductPrivateFiles.hash(source.file){cancelled.get()}==source.sha256)
  if(cancelled.get())throw SaveCancelled()
  if(destination.freeBytes()<source.bytes+FoundationPolicy.FREE_RESERVE_BYTES)throw FoundationFailure(ImportCode.STORAGE_LOW)
  val j=store.begin(source)
  try {
   val token=destination.insert(j.getProperty("saveId"));checkpoint("SAVE_AFTER_INSERT_BEFORE_RECORD");store.update(j,"COPYING",token)
   val row=requireNotNull(destination.find(j.getProperty("saveId"),token));require(row.pending)
   var count=0L;val digest=MessageDigest.getInstance("SHA-256")
   source.file.inputStream().use{input->destination.open(row).use{out->val b=ByteArray(65536);while(true){if(cancelled.get())throw SaveCancelled();val n=input.read(b);if(n<0)break;if(n==0)continue;require(count+n<=source.bytes)
    if(destination.freeBytes()<FoundationPolicy.FREE_RESERVE_BYTES+n)throw FoundationFailure(ImportCode.STORAGE_LOW)
    out.stream.write(b,0,n);digest.update(b,0,n);count+=n;onBytes(count);checkpoint("SAVE_DURING_COPY")
   };out.stream.flush();out.sync()}}
   require(count==source.bytes&&digest.digest().joinToString(""){"%02x".format(it)}==source.sha256)
   require(matches(row,j));store.update(j,"PUBLISHING");if(cancelled.get())throw SaveCancelled()
   destination.publish(row);checkpoint("SAVE_AFTER_PROVIDER_COMMIT")
   val committed=requireNotNull(destination.find(j.getProperty("saveId"),token));require(!committed.pending&&matches(committed,j))
   store.saved(j,token);return "SAVED"
  }catch(e:Exception){recover();if(store.info(source.exportId).getProperty("saveState")=="SAVED")return "SAVED";throw e}
 }
}
