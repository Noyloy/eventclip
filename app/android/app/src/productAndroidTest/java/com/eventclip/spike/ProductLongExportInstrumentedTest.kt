package com.eventclip.spike

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import expo.modules.eventcliprender.realmedia.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sin

/**
 * ~32s Birthday export with a longer song, mixed synthetic photos + clips, no voice marks.
 * Official M06 is ~9s and finishes in a few seconds on device, so it cannot catch a mistaken
 * 21s *total* elapsed timeout. This test records wall time and requires EXPORT_READY even when
 * the software emulator takes far longer than 21s. Synthetic fixtures only.
 */
@RunWith(AndroidJUnit4::class)
class ProductLongExportInstrumentedTest {
 private val instrumentation get()=InstrumentationRegistry.getInstrumentation()

 private fun copyAsset(store:BirthdayProjectStore,name:String,fromDir:String="product-m06"):Pair<String,File> {
  val id=UUID.randomUUID().toString();val f=store.file(id)
  instrumentation.context.assets.open("$fromDir/$name").use{input->f.outputStream().use{input.copyTo(it)}}
  return id to f
 }

 private fun writeToneWav(file:File,durationUs:Long,hz:Double=1000.0,amplitude:Float=0.4f) {
  val bus=ProductPcmBus(durationUs)
  val step=2.0*Math.PI*hz/bus.rate
  for(i in 0 until bus.frames){
   val s=(amplitude*sin(i*step)).toFloat()
   bus.put(i,s,s,0.5)
  }
  bus.writeWav(file)
 }

 private fun writeLargeJpeg(file:File,width:Int=4032,height:Int=3024,color:Int=Color.rgb(200,40,40)):Pair<Int,Int> {
  val bmp=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
  try {
   bmp.eraseColor(color)
   FileOutputStream(file).use{out->check(bmp.compress(Bitmap.CompressFormat.JPEG,85,out))}
  }finally{bmp.recycle()}
  return width to height
 }

 private class VideoMeta(val durationUs:Long,val width:Int,val height:Int)
 private fun videoMeta(file:File):VideoMeta {
  val e=MediaExtractor()
  try{
   e.setDataSource(file.absolutePath)
   val index=(0 until e.trackCount).first{e.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true}
   val format=e.getTrackFormat(index)
   return VideoMeta(format.getLong(MediaFormat.KEY_DURATION),format.getInteger(MediaFormat.KEY_WIDTH),format.getInteger(MediaFormat.KEY_HEIGHT))
  }finally{e.release()}
 }

 @Test(timeout=900000) fun mixedPhotosAndClips_32sLongerSong_exportReady() {
  val start=SystemClock.elapsedRealtime()
  val exportTimeoutMs=InstrumentationRegistry.getArguments().getString("exportTimeoutMs")?.toLongOrNull() ?: 840_000L
  val target=instrumentation.targetContext
  val id=UUID.randomUUID().toString()
  val isolated=File(target.filesDir,"product-instrument-runs/$id");check(isolated.mkdirs())
  val files=File(isolated,"files").apply{mkdir()}
  val cache=File(isolated,"cache").apply{mkdir()}
  val context=object:ContextWrapper(target){override fun getApplicationContext():Context=this;override fun getFilesDir()=files;override fun getCacheDir()=cache}
  val artifacts=File(requireNotNull(target.getExternalFilesDir(null)),"product-32s-evidence/$id").apply{check(mkdirs())}
  val store=BirthdayProjectStore(File(files,"eventclip-birthday"))
  val assets=ArrayList<ImportedAsset>()
  val report=JSONObject().put("schema","product-32s-export-v1").put("syntheticOnly",true).put("status","RUNNING")
  var controller:ProductExportController?=null
  try {
   fun addStill(assetId:String,file:File,mime:String,width:Int,height:Int,rotation:Int=0):ImportedAsset {
    val asset=ImportedAsset(assetId,"image",file.length(),mime,0,width,height,rotation)
    assets.add(asset);return asset
   }
   fun addAssetImage(name:String,fromDir:String="product-import",mime:String,width:Int,height:Int,rotation:Int=0):ImportedAsset {
    val (assetId,f)=copyAsset(store,name,fromDir)
    return addStill(assetId,f,mime,width,height,rotation)
   }
   // Stage as post-import files: JPEG/PNG may be kept; WebP is transcoded to JPEG like vc10 import.
   addAssetImage("sdr-320x180.jpg",mime="image/jpeg",width=320,height=180)
   addAssetImage("exif-orient6-320x180.jpg",mime="image/jpeg",width=180,height=320,rotation=90)
   addAssetImage("motion-photo-style.jpg",mime="image/jpeg",width=48,height=32)
   run {
    val (assetId,webp)=copyAsset(store,"sdr-48x32.webp","product-import")
    val bmp=android.graphics.BitmapFactory.decodeFile(webp.absolutePath) ?: error("webp fixture")
    try {
     val jpeg=File(webp.parentFile,webp.name+".jpg")
     FileOutputStream(jpeg).use{out->check(bmp.compress(Bitmap.CompressFormat.JPEG,90,out))}
     check((webp.delete()||!webp.exists()) && jpeg.renameTo(webp))
    }finally{bmp.recycle()}
    addStill(assetId,webp,"image/jpeg",48,32)
   }
   addAssetImage("sdr-48x32.png",mime="image/png",width=48,height=32)
   val largeId=UUID.randomUUID().toString();val large=store.file(largeId)
   writeLargeJpeg(large)
   addStill(largeId,large,"image/jpeg",4032,3024)
   val midId=UUID.randomUUID().toString();val mid=store.file(midId)
   writeLargeJpeg(mid,640,480,Color.rgb(40,40,200))
   addStill(midId,mid,"image/jpeg",640,480)

   val (clipAId,clipA)=copyAsset(store,"original-a-44100-mono.mp4")
   val aMeta=videoMeta(clipA);val aDur=aMeta.durationUs;val aW=aMeta.width;val aH=aMeta.height
   assets.add(ImportedAsset(clipAId,"video",clipA.length(),"video/mp4",aDur/1000,aW,aH,durationUs=aDur))
   val (silentId,silent)=copyAsset(store,"silent.mp4")
   val sMeta=videoMeta(silent);val sDur=sMeta.durationUs;val sW=sMeta.width;val sH=sMeta.height
   assets.add(ImportedAsset(silentId,"video",silent.length(),"video/mp4",sDur/1000,sW,sH,durationUs=sDur))

   val songId=UUID.randomUUID().toString();val song=store.file(songId)
   writeToneWav(song,40_000_000L)
   assets.add(ImportedAsset(songId,"song",song.length(),"audio/wav",40_000,0,0,durationUs=40_000_000L))

   val project=BirthdayProject(UUID.randomUUID().toString(),assets,keepDateOrder=false,voiceRanges=emptyList())
   store.save(project)
   val movie=ProductExportPolicy.movie(store,project)
   val expectedDurationUs=7*4_000_000L+aDur+sDur
   assertEquals(expectedDurationUs,movie.durationUs)
   assertEquals((expectedDurationUs+999_999L)/1_000_000L,movie.displaySecondsCeil)
   assertEquals("Movie length 0:32 as shown on the create screen",32L,movie.displaySecondsCeil)
   assertTrue(movie.musicEndsAtMovieEnd)
   assertEquals(expectedDurationUs-1_000_000L,movie.musicFadeStartUs)
   assertTrue(movie.items.none{it.durationUs<=0})
   report.put("planDurationUs",movie.durationUs).put("displaySecondsCeil",movie.displaySecondsCeil).put("itemCount",movie.items.size).put("musicFadeStartUs",movie.musicFadeStartUs)

   report.put("largePhotoId",largeId).put("exportMaxEdgeExpected",1280)

   val latch=CountDownLatch(1);var terminal:Map<String,Any?>?=null;val events=JSONArray()
   instrumentation.runOnMainSync{
    controller=ProductExportController(context){event->
     synchronized(events){events.put(JSONObject().put("state",event["state"]).put("active",event["active"]).put("reasonCode",event["reasonCode"]?:JSONObject.NULL).put("phase",event["phase"]).put("error",event["error"]?:JSONObject.NULL).put("progressPercent",event["progressPercent"]?:JSONObject.NULL).put("outBytes",event["outBytes"]?:JSONObject.NULL))}
     if(event["active"]==false){terminal=event;latch.countDown()}
    }
    assertTrue(controller!!.create(project.id,ProductExportPolicy.fingerprint(project)))
   }
   assertTrue("32s product export deadline",latch.await(exportTimeoutMs,TimeUnit.MILLISECONDS))
   val elapsed=SystemClock.elapsedRealtime()-start
   report.put("events",events).put("terminal",terminal?.get("state")).put("reasonCode",terminal?.get("reasonCode")).put("error",terminal?.get("error")).put("elapsedMs",elapsed).put("watchdogMs",21000L)
   if(elapsed>21000L)report.put("elapsedExceededStallThreshold",true)
   assertEquals("32s mixed export must reach EXPORT_READY (21s is a mux stall detector, not a total timeout). error=${terminal?.get("error")}","EXPORT_READY",terminal?.get("state"))
   val exportStore=ProductExportStore(File(context.filesDir,"eventclip-birthday-exports"))
   val record=requireNotNull(exportStore.latest())
   val output=exportStore.verifiedFile(record.getProperty("id"))
   output.copyTo(File(artifacts,"product-output.mp4"))
   File(artifacts,"export-manifest.properties").writeText(record.entries.joinToString("\n"){"${it.key}=${it.value}"})
   val tracks=ProductOutputAudit.inspect(output,AtomicBoolean(false))
   val gap=movie.items.maxOf{item->val a=project.assets.single{it.id==item.id};if(a.kind=="video")ProductOutputAudit.expectedVideoGap(store.file(a.id),AtomicBoolean(false))else 33334L}
   ProductExportPolicy.validate(movie.durationUs,tracks,gap)
   val video=tracks.single{it.mime=="video/avc"}
   val audio=tracks.single{it.mime=="audio/mp4a-latm"}
   assertEquals(720,video.width);assertEquals(1280,video.height);assertEquals(0,video.rotation)
   assertEquals(48000,audio.sampleRate);assertEquals(2,audio.channels)
   report.put("outputBytes",output.length()).put("outputSha256",ProductPrivateFiles.hash(output)).put("videoWidth",video.width).put("videoHeight",video.height).put("videoRotation",video.rotation).put("videoSamples",video.samples).put("audioSamples",audio.samples).put("status","PASS")
  }catch(t:Throwable){report.put("status","FAILED").put("failureClass",t.javaClass.simpleName).put("failureMessage",t.message);throw t}
  finally{
   instrumentation.runOnMainSync{controller?.requestStop("EXPORT_INTERRUPTED")}
   report.put("elapsedMs",SystemClock.elapsedRealtime()-start)
   File(artifacts,"metrics.json").writeText(report.toString(2))
  }
 }
}
