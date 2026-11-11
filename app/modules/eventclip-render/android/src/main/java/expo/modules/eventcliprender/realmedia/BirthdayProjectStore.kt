package expo.modules.eventcliprender.realmedia

import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class ImportedAsset(val id:String,val kind:String,val bytes:Long,val mime:String,val durationMs:Long=0,val width:Int=0,val height:Int=0,val rotation:Int=0,val captureAtMs:Long=0,val included:Boolean=true,val durationUs:Long=durationMs*1000)
data class SavedVoiceRange(val id:String,val assetId:String,val startUs:Long,val endUs:Long)
data class PendingVoiceStart(val assetId:String,val startUs:Long)
data class BirthdayProject(val id:String,val assets:List<ImportedAsset>,val keepDateOrder:Boolean=true,val voiceRanges:List<SavedVoiceRange> = emptyList(),val pendingVoiceStart:PendingVoiceStart?=null)

/** Single-writer app-private store. Atomic same-directory rename; journal survives process death.
 * Checkpoints emulate PROCESS death, not device power failure / filesystem durability certification.
 * No directory scans/deletions; only journal-owned IDs are eligible for cleanup. */
class BirthdayProjectStore(requestedRoot:File,private val checkpoint:(String)->Unit={}) {
  private val root=ProductPrivateFiles.settledDirectory(requestedRoot)
  val assetDir=ProductPrivateFiles.child(this.root,"assets").also{check(it.mkdirs() || it.isDirectory)}
  private val state=File(this.root,"project.properties")
  private val journal=File(this.root,"transaction.properties")
  var pendingCleanup=false
    private set
  var recoveredInterruptedImport=false
    private set
  private fun safeId(id:String?)=id!=null && id.matches(Regex("[a-f0-9-]{36}")) && runCatching{UUID.fromString(id).toString()==id}.getOrDefault(false)
  private fun owned(f:File):File {require(f.absoluteFile==f.canonicalFile);require(f.parentFile==root || f.parentFile==assetDir);return f}
  fun file(id:String):File {require(safeId(id));return owned(File(assetDir,"$id.asset"))}
  fun partial(id:String):File {require(safeId(id));return owned(File(assetDir,"$id.part"))}
  private fun read(f:File)=Properties().apply{owned(f).inputStream().use{load(it)}}
  private fun atomic(f:File,p:Properties) {
    owned(f);val temp=owned(File(root,f.name+".pending"))
    FileOutputStream(temp).use{p.store(it,"Private project state; no source URI/name");it.fd.sync()}
    check(temp.renameTo(f)) {"Private state commit failed"} // Linux/Android same-filesystem atomic replacement, no delete-before-rename.
  }
  private fun encode(p:BirthdayProject):Properties {
    require(safeId(p.id));require(p.assets.map{it.id}.distinct().size==p.assets.size)
    val j=Properties();j.setProperty("schema","birthday-import-foundation-v2");j.setProperty("id",p.id);j.setProperty("keepDateOrder",p.keepDateOrder.toString());j.setProperty("order",p.assets.joinToString(","){it.id})
    for(a in p.assets){require(safeId(a.id));require(a.bytes>0 && a.kind in setOf("image","video","song"));for((k,v) in mapOf("kind" to a.kind,"bytes" to a.bytes,"mime" to a.mime,"durationMs" to a.durationMs,"durationUs" to a.durationUs,"width" to a.width,"height" to a.height,"rotation" to a.rotation,"captureAtMs" to a.captureAtMs,"included" to a.included))j.setProperty("${a.id}.$k",v.toString())}
    require(p.voiceRanges.map{it.id}.distinct().size==p.voiceRanges.size)
    j.setProperty("voiceRanges",p.voiceRanges.joinToString(","){it.id})
    for(m in p.voiceRanges){require(safeId(m.id));val a=p.assets.single{it.id==m.assetId && it.kind=="video"};require(m.startUs>=0 && m.endUs>m.startUs && m.endUs<=a.durationUs);j.setProperty("mark.${m.id}.asset",m.assetId);j.setProperty("mark.${m.id}.start",m.startUs.toString());j.setProperty("mark.${m.id}.end",m.endUs.toString())}
    p.pendingVoiceStart?.let{m->val a=p.assets.single{it.id==m.assetId && it.kind=="video"};require(m.startUs>=0 && m.startUs<a.durationUs);j.setProperty("pending.asset",m.assetId);j.setProperty("pending.start",m.startUs.toString())}
    return j
  }
  private fun decode(j:Properties):BirthdayProject {
    val id=j.getProperty("id");require(safeId(id))
    val assets=j.getProperty("order","").split(',').filter{it.isNotBlank()}.map { a ->
      require(safeId(a));ImportedAsset(a,j.getProperty("$a.kind"),j.getProperty("$a.bytes").toLong(),j.getProperty("$a.mime"),j.getProperty("$a.durationMs","0").toLong(),j.getProperty("$a.width","0").toInt(),j.getProperty("$a.height","0").toInt(),j.getProperty("$a.rotation","0").toInt(),j.getProperty("$a.captureAtMs","0").toLong(),j.getProperty("$a.included","true").toBoolean(),j.getProperty("$a.durationUs",(j.getProperty("$a.durationMs","0").toLong()*1000).toString()).toLong())
    }
    val ranges=j.getProperty("voiceRanges","").split(',').filter{it.isNotBlank()}.map{SavedVoiceRange(it,j.getProperty("mark.$it.asset"),j.getProperty("mark.$it.start").toLong(),j.getProperty("mark.$it.end").toLong())}
    val pending=j.getProperty("pending.asset")?.let{PendingVoiceStart(it,j.getProperty("pending.start").toLong())}
    return BirthdayProject(id,assets,j.getProperty("keepDateOrder","true").toBoolean(),ranges,pending).also{encode(it)}
  }
  private fun snapshot(j:Properties,key:String,p:BirthdayProject){j.keys.toList().filter{it.toString().startsWith("$key.")}.forEach{j.remove(it)};for((k,v) in encode(p))j.setProperty("$key.$k",v.toString())}
  private fun snapshot(j:Properties,key:String)=decode(Properties().apply{for((k,v) in j)if(k.toString().startsWith("$key."))setProperty(k.toString().removePrefix("$key."),v.toString())})
  private fun same(a:BirthdayProject,b:BirthdayProject)=encode(a)==encode(b)
  private fun allocated(j:Properties):List<String> = j.getProperty("allocated","").split(',').filter{it.isNotBlank()}.also{ids->require(ids.distinct().size==ids.size && ids.all{safeId(it)})}
  private fun validJournal():Properties = read(journal).also {j->
    require(safeId(j.getProperty("jobId")));require(j.getProperty("phase") in setOf("STAGING","PREPARED"));require(j.getProperty("mode") in setOf("APPEND","NEW","EDIT"))
    val old=snapshot(j,"old");val next=snapshot(j,"next");val ids=allocated(j)
    require(ids.none{a->old.assets.any{it.id==a}})
    if(j.getProperty("mode")=="EDIT")require(next.assets.all{a->old.assets.any{it.id==a.id && it==a.copy(included=it.included)}})
    else require(next.assets.all{a->old.assets.any{it==a} || a.id in ids})
    if(j.getProperty("mode")=="NEW"){require(j.getProperty("confirmation")=="REPLACE_PROJECT");require(old.id!=next.id);require(next.assets.all{it.id in ids})}else require(old.id==next.id)
  }
  private fun cleanup(j:Properties,committed:Boolean) {
    val old=snapshot(j,"old");val next=snapshot(j,"next")
    val preserve=(if(committed)next else old).assets.map{it.id}.toSet()
    val remove=if(committed)(old.assets.map{it.id}+allocated(j)).filterNot{it in preserve}else allocated(j)
    for(id in remove.distinct()) {for(f in listOf(partial(id),file(id))){if(f.exists() && !f.delete())throw java.io.IOException("Private cleanup deferred");checkpoint("AFTER_DELETE")}}
    for(id in allocated(j)) {val f=partial(id);if(f.exists() && !f.delete())throw java.io.IOException("Private cleanup deferred")}
    checkpoint("BEFORE_JOURNAL_DELETE");if(!journal.delete())throw java.io.IOException("Private cleanup deferred")
  }
  /** Recovery only at worker startup/between jobs, never concurrently with active import. */
  fun recover() {
    if(!state.exists()) {val previous=owned(File(root,"project.previous"));if(previous.exists())check(previous.renameTo(state))}
    if(!journal.exists())return
    val j=validJournal();require(state.exists());val current=decode(read(state));val old=snapshot(j,"old");val next=snapshot(j,"next")
    try {when {same(current,old)->{recoveredInterruptedImport=j.getProperty("mode")!="EDIT";cleanup(j,false)};j.getProperty("phase")=="PREPARED" && same(current,next)->cleanup(j,true);else->error("Private recovery identity mismatch")}}catch(_:java.io.IOException){pendingCleanup=true}
  }
  fun load():BirthdayProject {
    recover()
    if(!state.exists()){val p=BirthdayProject(UUID.randomUUID().toString(),emptyList());atomic(state,encode(p));return p}
    return decode(read(state))
  }
  fun save(p:BirthdayProject){require(!journal.exists());atomic(state,encode(p))}
  fun beginImport(replaceProject:Boolean=false,confirmation:String="",expectedNewBytes:Long=0):BirthdayProject {
    val old=load();check(!journal.exists()){ "Private cleanup pending" };require(expectedNewBytes>=0)
    if(replaceProject)require(confirmation=="REPLACE_PROJECT")
    if(expectedNewBytes>assetDir.usableSpace-FoundationPolicy.FREE_RESERVE_BYTES)throw FoundationFailure(ImportCode.STORAGE_LOW)
    val next=if(replaceProject)BirthdayProject(UUID.randomUUID().toString(),emptyList())else old
    val j=Properties();j.setProperty("jobId",UUID.randomUUID().toString());j.setProperty("phase","STAGING");j.setProperty("mode",if(replaceProject)"NEW"else"APPEND");j.setProperty("confirmation",confirmation);j.setProperty("allocated","");snapshot(j,"old",old);snapshot(j,"next",next)
    atomic(journal,j);checkpoint("AFTER_BEGIN");return next
  }
  fun activeImportProject():BirthdayProject=snapshot(validJournal(),"next")
  fun allocateImportId():String {
    val j=validJournal();require(j.getProperty("phase")=="STAGING");val id=UUID.randomUUID().toString();require(!file(id).exists() && !partial(id).exists());j.setProperty("allocated",(allocated(j)+id).joinToString(","));atomic(journal,j);checkpoint("AFTER_ALLOCATE");return id
  }
  fun acceptImported(a:ImportedAsset,replaceSong:Boolean):BirthdayProject {
    val j=validJournal();require(a.id in allocated(j));val p=snapshot(j,"next");val next=p.copy(assets=p.assets.filterNot{replaceSong && it.kind=="song"}+a);validateCaps(next);snapshot(j,"next",next);atomic(journal,j);checkpoint("AFTER_ACCEPT");return next
  }
  private fun validateCaps(p:BirthdayProject) {
    if(p.assets.count{it.kind!="song"}>FoundationPolicy.MAX_VISUAL)throw FoundationFailure(ImportCode.ITEM_LIMIT)
    var remaining=FoundationPolicy.MAX_SOURCE_BYTES
    for(a in p.assets){if(a.bytes<=0 || a.bytes>remaining)throw FoundationFailure(ImportCode.SOURCE_SIZE_LIMIT);remaining-=a.bytes}
    require(p.assets.count{it.kind=="song"}<=1)
  }
  fun commitImport(cancelled:AtomicBoolean=AtomicBoolean(false)):BirthdayProject {
    val j=validJournal();val p=snapshot(j,"next");validateCaps(p)
    if(j.getProperty("mode")=="NEW")require(p.assets.any{it.kind!="song"})
    for(a in p.assets)require(file(a.id).isFile && file(a.id).length()==a.bytes)
    if(cancelled.get())throw FoundationFailure(ImportCode.IMPORT_CANCELLED)
    j.setProperty("phase","PREPARED");atomic(journal,j);checkpoint("AFTER_PREPARE")
    if(cancelled.get())throw FoundationFailure(ImportCode.IMPORT_CANCELLED)
    atomic(state,encode(p));checkpoint("AFTER_COMMIT") // Cancellation after this linearization point cannot undo committed media.
    try{cleanup(j,true)}catch(_:java.io.IOException){pendingCleanup=true};return p
  }
  fun abortImport(){if(journal.exists())recover()}
  private fun edit(next:(BirthdayProject)->BirthdayProject):BirthdayProject {
    val old=load();check(!journal.exists()){ "Private cleanup pending" };val n=next(old);val j=Properties();j.setProperty("jobId",UUID.randomUUID().toString());j.setProperty("phase","PREPARED");j.setProperty("mode","EDIT");j.setProperty("allocated","");snapshot(j,"old",old);snapshot(j,"next",n);atomic(journal,j);checkpoint("AFTER_PREPARE");atomic(state,encode(n));checkpoint("AFTER_COMMIT");try{cleanup(j,true)}catch(_:java.io.IOException){pendingCleanup=true};return n
  }
  fun include(id:String,included:Boolean):BirthdayProject=edit {p->require(p.assets.any{it.id==id});p.copy(assets=p.assets.map{if(it.id==id)it.copy(included=included)else it})}
  fun move(id:String,delta:Int):BirthdayProject=edit {p->val xs=p.assets.toMutableList();val i=xs.indexOfFirst{it.id==id};require(i>=0 && delta in -1..1);val a=xs.removeAt(i);xs.add((i+delta).coerceIn(0,xs.size),a);p.copy(assets=xs,keepDateOrder=false)}
  fun remove(id:String,confirmation:String):BirthdayProject {require(confirmation=="REMOVE_SOURCE");return edit{p->require(p.assets.any{it.id==id});p.copy(assets=p.assets.filterNot{it.id==id},voiceRanges=p.voiceRanges.filterNot{it.assetId==id},pendingVoiceStart=p.pendingVoiceStart?.takeUnless{it.assetId==id})}}
  fun startVoiceMark(assetId:String,atUs:Long):BirthdayProject=edit{p->
    require(p.pendingVoiceStart==null);val a=p.assets.single{it.id==assetId && it.kind=="video" && it.included};require(atUs>=0 && atUs<a.durationUs)
    p.copy(pendingVoiceStart=PendingVoiceStart(assetId,atUs))
  }
  fun finishVoiceMark(atUs:Long):BirthdayProject=edit{p->
    val pending=requireNotNull(p.pendingVoiceStart);val a=p.assets.single{it.id==pending.assetId};require(atUs>pending.startUs && atUs<=a.durationUs)
    p.copy(voiceRanges=p.voiceRanges+SavedVoiceRange(UUID.randomUUID().toString(),a.id,pending.startUs,atUs),pendingVoiceStart=null)
  }
  fun discardPendingVoiceStart():BirthdayProject=edit{it.copy(pendingVoiceStart=null)}
  fun removeVoiceRange(id:String):BirthdayProject=edit{p->require(p.voiceRanges.any{it.id==id});p.copy(voiceRanges=p.voiceRanges.filterNot{it.id==id})}
  fun dateOrder(enabled:Boolean):BirthdayProject=edit{it.copy(keepDateOrder=enabled)}
  fun ordered(p:BirthdayProject):List<ImportedAsset> = if(p.keepDateOrder)p.assets.sortedWith(compareBy<ImportedAsset>{if(it.captureAtMs>0)it.captureAtMs else Long.MAX_VALUE})else p.assets
  fun deleteProject(confirmation:String){require(confirmation=="DELETE_PROJECT");edit{BirthdayProject(it.id,emptyList())}} // only referenced sources, never recursive root/output deletion
}
