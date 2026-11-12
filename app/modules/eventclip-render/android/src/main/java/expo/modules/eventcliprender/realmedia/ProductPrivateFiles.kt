package expo.modules.eventcliprender.realmedia
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID
import java.security.MessageDigest

/** Private UUID-only ownership. Never discover/delete arbitrary cache children. */
object ProductPrivateFiles {
 fun id(id:String)=require(runCatching{UUID.fromString(id).toString()==id}.getOrDefault(false))

 /** Settle an official app-private directory. Android filesDir/cacheDir may be reached via
  *  /data/user/<id> -> /data/data (same package tree). Lexical ".." and a leaf that resolves
  *  outside its settled parent remain rejected. Returned path is canonical, so later child
  *  ownership checks stay strict. */
 fun settledDirectory(dir:File):File {
  val absolute=dir.absoluteFile
  require(absolute.path.split(File.separatorChar).none{it==".."})
  val parent=requireNotNull(absolute.parentFile)
  val settledParent=if(parent.exists())parent.canonicalFile else settledDirectory(parent)
  require(settledParent.isDirectory)
  val target=File(settledParent,absolute.name)
  if(target.exists()){
   val settled=target.canonicalFile
   require(settled.isDirectory && settled.name==absolute.name && settled.parentFile==settledParent)
   return settled
  }
  check(target.mkdirs()||target.isDirectory)
  require(target.canonicalFile==target.absoluteFile && target.parentFile==settledParent)
  return target.canonicalFile
 }
 fun child(root:File,name:String):File {require(root.absoluteFile==root.canonicalFile);return File(root,name).also{require(it.absoluteFile==it.canonicalFile && it.parentFile==root)}}
 fun read(f:File)=Properties().apply{f.inputStream().use{load(it)}}
 fun atomic(f:File,p:Properties){val tmp=child(requireNotNull(f.parentFile),f.name+".pending");FileOutputStream(tmp).use{p.store(it,"EventClip private identity; no original paths");it.fd.sync()};check(tmp.renameTo(f))}
 fun hash(f:File,cancelled:()->Boolean={false}):String {val h=MessageDigest.getInstance("SHA-256");f.inputStream().use{input->val b=ByteArray(65536);while(true){if(cancelled())throw ExportStopped();val n=input.read(b);if(n<0)break;if(n>0)h.update(b,0,n)}};return h.digest().joinToString(""){"%02x".format(it)}}
}
class ProductWorkspaces(requestedRoot:File) {
  private val root=ProductPrivateFiles.settledDirectory(requestedRoot)
 private val registry=ProductPrivateFiles.child(root,"owned-workspaces.properties")
 private fun ids():List<String> = if(!registry.exists())emptyList()else ProductPrivateFiles.read(registry).getProperty("ids","").split(',').filter{it.isNotEmpty()}.also{xs->require(xs.distinct().size==xs.size);xs.forEach{ProductPrivateFiles.id(it)}}
 private fun save(xs:List<String>)=ProductPrivateFiles.atomic(registry,Properties().apply{setProperty("ids",xs.joinToString(","))})
 fun allocate():File{val id=UUID.randomUUID().toString();val dir=ProductPrivateFiles.child(root,id);require(!dir.exists());save(ids()+id);check(dir.mkdir());return dir}
 fun release(dir:File){require(dir.parentFile==root);ProductPrivateFiles.id(dir.name);require(dir.name in ids());ProductPrivateFiles.child(root,dir.name)
  // Exact registered names only: the two PCM buses plus per-item export stills (itemId.export.jpg).
  // Never recursive deletion; an unexpected child still fails the directory delete.
  for(name in listOf("original.wav","music.wav")){val f=ProductPrivateFiles.child(dir,name);if(f.exists())check(f.delete())}
  dir.listFiles()?.forEach{f->
   if(f.isFile && (f.name.endsWith(".export.jpg")||f.name.endsWith(".export.jpg.tmp"))){
    val stem=f.name.removeSuffix(".tmp").removeSuffix(".export.jpg")
    ProductPrivateFiles.id(stem)
    ProductPrivateFiles.child(dir,f.name)
    check(f.delete())
   }
  }
  if(dir.exists())check(dir.delete()) // unrelated child causes safe failure, never recursive deletion
  save(ids()-dir.name)
 }
 fun recover(){for(id in ids())release(ProductPrivateFiles.child(root,id))}
}
