package com.eventclip.spike

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Gainmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import expo.modules.eventcliprender.realmedia.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Synthetic picker-style import + a Birthday export that used an imported photo. No personal media. */
@RunWith(AndroidJUnit4::class)
class ProductImportPickerInstrumentedTest {
 private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
 private val inserted=ArrayList<Uri>()
 private fun assets(name:String)=instrumentation.context.assets.open("product-import/$name").use{it.readBytes()}
 private fun isolated():Pair<Context,BirthdayProjectStore> {
  val target=instrumentation.targetContext
  val id=UUID.randomUUID().toString()
  val isolated=File(target.filesDir,"product-instrument-runs/$id");check(isolated.mkdirs())
  val files=File(isolated,"files").apply{mkdir()}
  val cache=File(isolated,"cache").apply{mkdir()}
  val context=object:ContextWrapper(target){override fun getApplicationContext():Context=this;override fun getFilesDir()=files;override fun getCacheDir()=cache}
  return context to BirthdayProjectStore(File(files,"eventclip-birthday"))
 }
 private fun insert(payload:ByteArray,displayName:String,mime:String?):Uri {
  val resolver=instrumentation.targetContext.contentResolver
  fun tryInsert(type:String?):Uri? {
   val values=ContentValues().apply{
    put(MediaStore.MediaColumns.DISPLAY_NAME,displayName)
    if(type!=null)put(MediaStore.MediaColumns.MIME_TYPE,type)
    put(MediaStore.MediaColumns.IS_PENDING,1)
   }
   return try{resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)}catch(_:IllegalArgumentException){null}
  }
  val uri=tryInsert(mime) ?: tryInsert("image/jpeg") ?: error("insert $displayName")
  inserted.add(uri)
  resolver.openOutputStream(uri)!!.use{it.write(payload)}
  resolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
  return uri
 }
 private fun cleanup(){
  val resolver=instrumentation.targetContext.contentResolver
  for(uri in inserted)runCatching{resolver.delete(uri,null,null)}
  inserted.clear()
 }
 private fun describe(uri:Uri):JSONObject {
  val resolver=instrumentation.targetContext.contentResolver
  val row=JSONObject().put("uri",uri.toString()).put("authority",uri.authority).put("path",uri.path).put("getType",resolver.getType(uri)?:"null")
  resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{c->
   if(c.moveToFirst()){
    val n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);val z=c.getColumnIndex(OpenableColumns.SIZE)
    row.put("displayName",if(n>=0 && !c.isNull(n))c.getString(n)else JSONObject.NULL)
    row.put("size",if(z>=0 && !c.isNull(z))c.getLong(z)else JSONObject.NULL)
   }
  }
  assertEquals("media",uri.authority)
  assertTrue("picker-style URI has no filename extension: $uri",uri.lastPathSegment?.contains('.')!=true)
  return row
 }
 private fun importVisual(store:BirthdayProjectStore,context:Context,uri:Uri):ImportedAsset {
  val before=store.load().assets.map{it.id}.toSet()
  BirthdayImporter(context,store).importSelected(uri,"visual",{})
  val added=store.load().assets.filter{it.id !in before}
  assertEquals("one imported asset",1,added.size)
  return added.single()
 }
 private fun writeGainmapJpeg(dir:File):File? {
  if(Build.VERSION.SDK_INT<34)return null
  val base=Bitmap.createBitmap(48,32,Bitmap.Config.ARGB_8888)
  base.eraseColor(Color.rgb(200,40,80))
  val map=Bitmap.createBitmap(48,32,Bitmap.Config.ARGB_8888)
  map.eraseColor(Color.rgb(160,160,160))
  base.gainmap=Gainmap(map)
  val file=File(dir,"ultrahdr-gainmap.jpg")
  file.outputStream().use{assertTrue(base.compress(Bitmap.CompressFormat.JPEG,90,it))}
  base.recycle();map.recycle()
  val check=BitmapFactory.decodeFile(file.absolutePath) ?: return file
  val has=check.hasGainmap();check.recycle()
  file.setWritable(true)
  return if(has)file else null
 }

 @Test(timeout=900000) fun pickerStyleUris_importStillsAndExportImportedPhoto(){
  val report=JSONObject().put("schema","product-import-picker-v1").put("syntheticOnly",true).put("legacyGainmapOrHeifRejectedAsUnsupportedMedia",true)
  val cases=JSONArray()
  val (context,store)=isolated()
  val artifacts=File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)),"product-import-evidence/${UUID.randomUUID()}").apply{check(mkdirs())}
  try {
   fun case(name:String,payload:ByteArray,displayName:String,mime:String?,expect:String,kind:String="image"):ImportedAsset? {
    val uri=insert(payload,displayName,mime)
    val desc=describe(uri)
    val row=JSONObject().put("name",name).put("source",desc).put("expect",expect)
    return try {
     val asset=importVisual(store,context,uri)
     row.put("code","READY").put("kind",asset.kind).put("mime",asset.mime).put("width",asset.width).put("height",asset.height).put("rotation",asset.rotation).put("bytes",asset.bytes)
     assertEquals(name,kind,asset.kind)
     if(expect=="READY")assertEquals("image",asset.kind)
     cases.put(row);asset
    }catch(e:FoundationFailure){
     row.put("code",e.code.name)
     cases.put(row)
     if(expect=="READY")throw AssertionError("$name expected READY but was ${e.code.name}",e)
     assertEquals(name,expect,e.code.name)
     null
    }
   }
   val sdr=case("sdr-jpeg-no-extension",assets("sdr-48x32.jpg"),"123","image/jpeg","READY")
   case("sdr-jpeg-no-ext-media-uri",assets("sdr-320x180.jpg"),"1000000123","image/jpeg","READY")
   case("sdr-jpeg-wrong-heic-mime",assets("sdr-48x32.jpg"),"camera","image/heic","READY")
   val exif=case("exif-orient6",assets("exif-orient6-48x32.jpg"),"IMG_0001","image/jpeg","READY")
   assertEquals("exif rotation preserved when the JPEG is otherwise baseline",90,exif!!.rotation)
   val motion=case("motion-photo-style",assets("motion-photo-style.jpg"),"MVIMG_0002","image/jpeg","READY")
   assertEquals("image/jpeg",motion!!.mime)
   assertEquals(0,motion.rotation)
   case("png-no-extension",assets("sdr-48x32.png"),"44","image/png","READY")
   val webp=case("webp-no-extension",assets("sdr-48x32.webp"),"55","image/webp","READY")
   assertEquals("image/jpeg",webp!!.mime)
   val heic=case("heic-ftyp-only",assets("heic-ftyp-only.heic"),"66","image/heic","HEIC_UNSUPPORTED")
   assertNull(heic)
   val gainFile=writeGainmapJpeg(artifacts)
   report.put("gainmapFixtureWritten",gainFile!=null)
   if(gainFile!=null){
    val decoded=BitmapFactory.decodeFile(gainFile.absolutePath)
    report.put("gainmapWouldHaveBeenRejectedByOldImporter",decoded!=null && Build.VERSION.SDK_INT>=34 && decoded.hasGainmap())
    decoded?.recycle()
    val imported=case("ultrahdr-gainmap-jpeg",gainFile.readBytes(),"77","image/jpeg","READY")
    assertEquals("image/jpeg",imported!!.mime)
    val stored=BitmapFactory.decodeFile(store.file(imported.id).absolutePath)
    if(Build.VERSION.SDK_INT>=34)assertFalse("stored baseline JPEG must not keep a gainmap",stored.hasGainmap())
    stored.recycle()
   }
   report.put("cases",cases)

   // Birthday export containing an imported photo (picker URI), plus the official M06 synthetic clips.
   val photoId=UUID.randomUUID().toString()
   val importedPhoto=File(artifacts,"imported-photo.jpg")
   importedPhoto.writeBytes(assets("exif-orient6-320x180.jpg"))
   val photoUri=insert(importedPhoto.readBytes(),"photo-no-ext","image/jpeg")
   describe(photoUri)
   val photoAsset=importVisual(store,context,photoUri)
   assertEquals("image",photoAsset.kind)
   assertTrue(store.file(photoAsset.id).isFile)
   val assets=ArrayList<ImportedAsset>()
   assets.add(photoAsset)
   for((name,kind) in listOf("original-a-44100-mono.mp4" to "video","silent.mp4" to "video","original-b-48000-stereo.mp4" to "video","music-48000-stereo.wav" to "song")){
    val assetId=UUID.randomUUID().toString();val f=store.file(assetId)
    instrumentation.context.assets.open("product-m06/$name").use{input->f.outputStream().use{input.copyTo(it)}}
    var duration=0L;var w=320;var h=180
    if(kind!="image"){
     val e=android.media.MediaExtractor()
     try{
      e.setDataSource(f.absolutePath)
      val index=(0 until e.trackCount).first{e.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith(if(kind=="song")"audio/"else"video/")==true}
      val format=e.getTrackFormat(index);duration=format.getLong(android.media.MediaFormat.KEY_DURATION)
      if(kind=="video"){w=format.getInteger(android.media.MediaFormat.KEY_WIDTH);h=format.getInteger(android.media.MediaFormat.KEY_HEIGHT)}
     }finally{e.release()}
    }
    assets.add(ImportedAsset(assetId,kind,f.length(),if(kind=="song")"audio/wav"else"video/mp4",duration/1000,w,h,durationUs=duration))
   }
   val project=BirthdayProject(UUID.randomUUID().toString(),assets,keepDateOrder=false,voiceRanges=listOf(SavedVoiceRange(UUID.randomUUID().toString(),assets[1].id,500000,1500000)))
   store.save(project)
   val movie=ProductExportPolicy.movie(store,project)
   val exportTimeoutMs=InstrumentationRegistry.getArguments().getString("exportTimeoutMs")?.toLongOrNull() ?: 120_000L
   val start=SystemClock.elapsedRealtime()
   val deadline=start+if(InstrumentationRegistry.getArguments().getString("exportTimeoutMs")==null)160_000L else exportTimeoutMs
   val latch=CountDownLatch(1);var terminal:Map<String,Any?>?=null
   var controller:ProductExportController?=null
   instrumentation.runOnMainSync{controller=ProductExportController(context){event->if(event["active"]==false){terminal=event;latch.countDown()}};assertTrue(controller!!.create(project.id,ProductExportPolicy.fingerprint(project)))}
   assertTrue("product export deadline",latch.await(minOf(exportTimeoutMs,deadline-SystemClock.elapsedRealtime()).coerceAtLeast(1),TimeUnit.MILLISECONDS))
   report.put("exportState",terminal?.get("state")).put("reasonCode",terminal?.get("reasonCode")).put("error",terminal?.get("error")).put("importedPhotoMime",photoAsset.mime).put("importedPhotoRotation",photoAsset.rotation)
   assertEquals("EXPORT_READY",terminal?.get("state"))
   val exportStore=ProductExportStore(File(context.filesDir,"eventclip-birthday-exports"))
   val record=requireNotNull(exportStore.latest())
   val output=exportStore.verifiedFile(record.getProperty("id"))
   output.copyTo(File(artifacts,"product-output.mp4"),overwrite=true)
   val actual=NativePcmEvidence.decode(output,deadline)
   report.put("outputBytes",output.length()).put("decodedRate",actual.rate).put("decodedChannels",actual.channels)
   assertEquals(48000,actual.rate);assertEquals(2,actual.channels)
   val photo=actual.amplitude(1000.0,1000000,1100000)
   val aStart=movie.items.single{it.id==assets[1].id}.startUs
   val unmarked=actual.amplitude(1000.0,aStart+100000,aStart+200000)
   report.put("photoMusicAmplitude",photo).put("unmarkedMusicRatio",unmarked/photo)
   assertTrue("photo-only common0.5",photo in .12.. .16)
   assertTrue("unmarked normal music",unmarked/photo in .85..1.15)
   report.put("status","PASS")
   File(artifacts,"report.json").writeText(report.toString(2))
  }catch(e:Throwable){
   report.put("status","FAIL").put("error",e.message).put("cases",cases)
   File(artifacts,"report.json").writeText(report.toString(2))
   throw e
  }finally{cleanup()}
 }
}
