package com.eventclip.spike

import android.content.Context
import android.content.ContextWrapper
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import expo.modules.eventcliprender.realmedia.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Contract for the start-of-export swallow that left EXPORT_FAILED with an empty reasonCode
 * and error=null (S23 32s Test Lab, never reached EXPORTING). Pre-create the partial output
 * after begin() and before Transformer.start so the main-thread start() require throws.
 * The product must record reasonCode and the exception text. This does not reproduce a
 * hardware-only Transformer.start throw; it exercises the same controller catch.
 */
@RunWith(AndroidJUnit4::class)
class ProductExportStartFailureInstrumentedTest {
 private val instrumentation get()=InstrumentationRegistry.getInstrumentation()

 private fun copyAsset(store:BirthdayProjectStore,name:String):Pair<String,File> {
  val id=UUID.randomUUID().toString();val f=store.file(id)
  instrumentation.context.assets.open("product-m06/$name").use{input->f.outputStream().use{input.copyTo(it)}}
  return id to f
 }



 @Test(timeout=180000) fun startWhenOutputExists_recordsReasonCodeAndError() {
  val target=instrumentation.targetContext
  val id=UUID.randomUUID().toString()
  val isolated=File(target.filesDir,"product-instrument-runs/$id");check(isolated.mkdirs())
  val files=File(isolated,"files").apply{mkdir()}
  val cache=File(isolated,"cache").apply{mkdir()}
  val context=object:ContextWrapper(target){override fun getApplicationContext():Context=this;override fun getFilesDir()=files;override fun getCacheDir()=cache}
  val store=BirthdayProjectStore(File(files,"eventclip-birthday"))
  val assets=ArrayList<ImportedAsset>()
  for((name,kind) in listOf("photo.png" to "image","silent.mp4" to "video","music-48000-stereo.wav" to "song")){
   val (assetId,f)=copyAsset(store,name)
   var duration=0L;var w=320;var h=180
   if(kind!="image"){
    val e=MediaExtractor()
    try{
     e.setDataSource(f.absolutePath)
     val index=(0 until e.trackCount).first{e.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(if(kind=="song")"audio/"else"video/")==true}
     val format=e.getTrackFormat(index)
     duration=format.getLong(MediaFormat.KEY_DURATION)
     if(kind=="video"){w=format.getInteger(MediaFormat.KEY_WIDTH);h=format.getInteger(MediaFormat.KEY_HEIGHT)}
    }finally{e.release()}
   }
   assets.add(ImportedAsset(assetId,kind,f.length(),if(kind=="image")"image/png"else if(kind=="song")"audio/wav"else"video/mp4",duration/1000,w,h,durationUs=duration))
  }
  val project=BirthdayProject(UUID.randomUUID().toString(),assets,keepDateOrder=false,voiceRanges=emptyList())
  store.save(project)

  val latch=CountDownLatch(1);var terminal:Map<String,Any?>?=null
  var controller:ProductExportController?=null
  try {
   instrumentation.runOnMainSync{
    controller=ProductExportController(context){event->
     if(event["active"]==false){terminal=event;latch.countDown()}
    }
    assertTrue(controller!!.create(project.id,ProductExportPolicy.fingerprint(project)))
   }
   val exportRoot=File(context.filesDir,"eventclip-birthday-exports")
   val journal=File(exportRoot,"export-job.properties")
   val deadline=SystemClock.elapsedRealtime()+60_000L
   var planted=false
   while(SystemClock.elapsedRealtime()<deadline && !planted){
    if(journal.isFile){
     val p=Properties();journal.inputStream().use{p.load(it)}
     val exportId=p.getProperty("id")
     if(!exportId.isNullOrEmpty()){
      val part=File(exportRoot,"$exportId.part.mp4")
      check(part.createNewFile()||part.exists())
      planted=true
     }
    }
    if(!planted)Thread.sleep(5)
   }
   assertTrue("begin() must publish the export journal before start() so the part file can be planted",planted)
   assertTrue("start-failure export should finish quickly",latch.await(60,TimeUnit.SECONDS))
   assertEquals("EXPORT_FAILED",terminal?.get("state"))
   assertEquals("EXPORT_FAILED",terminal?.get("reasonCode"))
   val error=terminal?.get("error") as? String
   assertFalse("error must be recorded, not null/blank",error.isNullOrBlank())
   assertTrue("error should name the start() output-exists require, was: $error",error!!.contains("OUTPUT_ALREADY_EXISTS"))
  }finally{
   instrumentation.runOnMainSync{controller?.requestStop("EXPORT_INTERRUPTED")}
  }
 }
}
