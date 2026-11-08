package expo.modules.eventcliprender
import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Private index of validated outputs. Successful files are immutable per-run; never removed on retry. */
class OutputStore(private val context: Context) {
  private val root = SpikePaths.out(context)
  private val indexFile = File(root, "successful-outputs.json")
  private fun index(): JSONObject = if(indexFile.exists()) JSONObject(indexFile.readText()) else JSONObject()
  private fun save(j: JSONObject) {
    val tmp=File(root,"successful-outputs.tmp")
    tmp.writeText(j.toString(2))
    check(tmp.renameTo(indexFile)) { "Output index commit failed" }
  }
  private fun hash(f: File): String {
    val md=MessageDigest.getInstance("SHA-256")
    f.inputStream().use { input -> val b=ByteArray(65536); while(true){ val n=input.read(b); if(n<0)break; md.update(b,0,n) } }
    return md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
  }
  fun commit(id: String, file: File, runId: String, audit: Map<String, Any?>, source: String = "vc5_current_run"): Map<String, Any?> {
    require(audit["intendedTracksValid"] == true) { "Output audit invalid" }
    val safe=OutputPolicy.resolve(root,id,file.name)
    val meta=linkedMapOf<String,Any?>("id" to id,"basename" to safe.name,"bytes" to safe.length(),
      "sha256" to hash(safe),"runId" to runId,"producerVersionCode" to (if(source.startsWith("imported_vc4")) 4 else 5),
      "producerVersionName" to (if(source.startsWith("imported_vc4")) "1.3.0-diagnostic" else "1.4.0-diagnostic"),
      "source" to source,"recordedAtEpochMs" to System.currentTimeMillis(),"trackAudit" to audit,
      "successful" to true,"decodeVerified" to false)
    val j=index(); j.put(id,JSONObject(meta)); save(j)
    return meta
  }
  /** Import only documented vc4 successful file with matching bytes AND freshly validated current metadata. */
  fun importVc4() {
    val latest=SpikePaths.latestResultsFile(context)
    if(!latest.exists())return
    val old=try { JSONObject(latest.readText()) } catch(_:Exception){ return }
    if(old.optString("schema") != "eventclip-diagnostic-vc4")return
    val priorId="vc4_"+old.optLong("startedAtEpochMs")
    val archive=File(SpikePaths.logs(context),"results_$priorId.json")
    if(!archive.exists())archive.writeText(old.toString(2))
    for((id,sc) in listOf("matched20" to "compose_matched20s_720p","720" to "compose_720p_full")) {
      if(index().has(id))continue
      val metric=old.optJSONObject("scenarios")?.optJSONObject(sc) ?: continue
      if(!metric.optBoolean("ok"))continue
      try {
        val f=OutputPolicy.resolve(root,id,metric.getString("outputBasename"))
        if(f.length()!=metric.optLong("bytes"))continue
        val e=OutputPolicy.expected(id)
        val a=OutputTrackAudit.inspect(f,e.first,e.second,e.third)
        if(a["intendedTracksValid"]==true)commit(id,f,priorId,a,"imported_vc4_previous_run_hash_established_now")
      } catch(_:Exception) { /* absent/invalid old output cannot be selected */ }
    }
  }
  fun selected(id: String): Pair<File,JSONObject> {
    require(id in OutputPolicy.ids) { "Unknown output selection" }
    val m=index().optJSONObject(id) ?: throw IllegalStateException("No successful $id output")
    require(m.optBoolean("successful")) { "Output unsuccessful" }
    val f=OutputPolicy.resolve(root,id,m.getString("basename"))
    require(f.length()==m.getLong("bytes") && hash(f)==m.getString("sha256")) { "Output identity mismatch" }
    return f to m
  }
  fun has(id: String): Boolean = try { selected(id);true } catch(_:Exception){false}
  fun snapshot(): Map<String,Any?> {
    val out=linkedMapOf<String,Any?>()
    for(id in OutputPolicy.ids) {
      try { val m=selected(id).second;out[id]=mapOf("available" to true,"bytes" to m.getLong("bytes"),"sha256" to m.getString("sha256"),"runId" to m.getString("runId"),"source" to m.getString("source"),"producerVersionCode" to m.getInt("producerVersionCode"),"basename" to m.getString("basename")) }
      catch(_:Exception){out[id]=mapOf("available" to false)}
    }
    return out
  }
}
