package com.eventclip.spike
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.transformer.CompositionPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import expo.modules.eventcliprender.realmedia.*
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Synthetic PRODUCT gate. Compiling/building this class is NOT running/passing it. */
@RunWith(AndroidJUnit4::class)
class ProductM06InstrumentedTest {
 private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
 private data class Run(val context:Context,val artifacts:File,val store:BirthdayProjectStore,val project:BirthdayProject,val movie:MoviePlan,val originals:List<ImportedAsset>)
 private fun stage(shifted:Boolean=false):Run {
  val target=instrumentation.targetContext;val id=UUID.randomUUID().toString();val isolated=File(target.filesDir,"product-instrument-runs/$id");check(isolated.mkdirs());val files=File(isolated,"files").apply{mkdir()};val cache=File(isolated,"cache").apply{mkdir()}
  val context=object:ContextWrapper(target){override fun getApplicationContext():Context=this;override fun getFilesDir()=files;override fun getCacheDir()=cache}
  val artifacts=File(requireNotNull(target.getExternalFilesDir(null)),"product-m06-evidence/$id").apply{check(mkdirs())}
  val store=BirthdayProjectStore(File(files,"eventclip-birthday"));val assets=ArrayList<ImportedAsset>()
  for((name,kind) in listOf("photo.png" to "image",(if(shifted)"original-a-sourcepts-plus2s.mp4"else"original-a-44100-mono.mp4") to "video","silent.mp4" to "video","original-b-48000-stereo.mp4" to "video","music-48000-stereo.wav" to "song")){
   val assetId=UUID.randomUUID().toString();val f=store.file(assetId);instrumentation.context.assets.open("product-m06/$name").use{input->f.outputStream().use{input.copyTo(it)}}
   var duration=0L;var w=320;var h=180
   if(kind!="image"){val e=MediaExtractor();try{e.setDataSource(f.absolutePath);val index=(0 until e.trackCount).first{e.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(if(kind=="song")"audio/"else"video/")==true};val format=e.getTrackFormat(index);duration=format.getLong(MediaFormat.KEY_DURATION);if(kind=="video"){w=format.getInteger(MediaFormat.KEY_WIDTH);h=format.getInteger(MediaFormat.KEY_HEIGHT)}}finally{e.release()}}
   assets.add(ImportedAsset(assetId,kind,f.length(),if(kind=="image")"image/png"else if(kind=="song")"audio/wav"else"video/mp4",duration/1000,w,h,durationUs=duration))
  }
  val a=assets[1];val p=BirthdayProject(UUID.randomUUID().toString(),assets,keepDateOrder=false,voiceRanges=listOf(SavedVoiceRange(UUID.randomUUID().toString(),a.id,500000,1500000)))
  store.save(p);val movie=ProductExportPolicy.movie(store,p);require(movie.durationUs in 8000000..14000000)
  return Run(context,artifacts,store,p,movie,listOf(assets[1],assets[3]))
 }
 private fun expectedOriginal(run:Run,a:ImportedAsset):EvidencePcm {
  val p=ProductAudioDecoder.probe(run.store.file(a.id));val slot=run.movie.items.single{it.id==a.id};val bus=ProductPcmBus(run.movie.durationUs)
  ProductAudioDecoder.decode(run.store.file(a.id),requireNotNull(p.audioTrack),ProductPcmTimeline(bus,slot.startUs,slot.durationUs,requireNotNull(p.videoFirstPtsUs)){0.5},AtomicBoolean(false),requireNotNull(p.videoFirstPtsUs)+slot.durationUs)
  bus.writeWav(File(run.artifacts,if(a==run.originals[0])"original-a-expected-half.wav"else"original-b-expected-half.wav"))
  return EvidencePcm(FloatArray(bus.samples.size){bus.samples[it]/32768f},48000,2,0,0,JSONObject().put("basis","actual product MediaCodec source decode/conversion at relative0.5, NOT actual output"))
 }
 @Test(timeout=600000) fun productTwoBus720p_actualDecodeGate(){renderGate(false)}
 @Test(timeout=600000) fun productNonzeroSourcePts_actualDecodeGate(){renderGate(true)}
 private fun renderGate(shifted:Boolean){
  val start=SystemClock.elapsedRealtime()
  // Default 120s latch / 160s deadline unchanged for S23. Optional exportTimeoutMs is for slow local software emulators only.
  val exportTimeoutMs=InstrumentationRegistry.getArguments().getString("exportTimeoutMs")?.toLongOrNull() ?: 120_000L
  val deadline=start+if(InstrumentationRegistry.getArguments().getString("exportTimeoutMs")==null)160_000L else exportTimeoutMs
  val run=stage(shifted);val report=JSONObject().put("schema","product-m06-native-v1").put("status","RUNNING").put("syntheticOnly",true).put("realSpeech",false).put("diagnosticEngineUsed",false).put("commonGain",0.5).put("manualGaplessTrim",false).put("planDurationUs",run.movie.durationUs)
  var controller:ProductExportController?=null
  try{
   val a=run.originals[0];val b=run.originals[1];val aSlot=run.movie.items.single{it.id==a.id};val bSlot=run.movie.items.single{it.id==b.id}
   if(shifted)assertTrue("nonzero source video PTS fixture",ProductAudioDecoder.probe(run.store.file(a.id)).videoFirstPtsUs!!>1000000)
   val latch=CountDownLatch(1);var terminal:Map<String,Any?>?=null;val events=JSONArray()
   instrumentation.runOnMainSync{controller=ProductExportController(run.context){event->synchronized(events){events.put(JSONObject().put("state",event["state"]).put("active",event["active"]).put("reasonCode",event["reasonCode"]).put("phase",event["phase"]).put("error",event["error"]))};if(event["phase"]=="AUDITING"){File(run.context.filesDir,"eventclip-birthday-exports").listFiles()?.filter{it.name.endsWith(".part.mp4")&&it.isFile}?.forEach{it.copyTo(File(run.artifacts,"export-partial.mp4"),overwrite=true)}};if(event["active"]==false){terminal=event;latch.countDown()}};assertTrue(controller!!.create(run.project.id,ProductExportPolicy.fingerprint(run.project)))}
   assertTrue("product export deadline",latch.await(minOf(exportTimeoutMs,deadline-SystemClock.elapsedRealtime()).coerceAtLeast(1),TimeUnit.MILLISECONDS));report.put("events",events).put("terminal",terminal?.get("state")).put("reasonCode",terminal?.get("reasonCode")).put("error",terminal?.get("error"))
   val retainedPartial=File(run.artifacts,"export-partial.mp4")
   if(retainedPartial.isFile){try{val tracks=ProductOutputAudit.inspect(retainedPartial,AtomicBoolean(false));val gap=run.movie.items.maxOf{item->val asset=run.project.assets.single{it.id==item.id};if(asset.kind=="video")ProductOutputAudit.expectedVideoGap(run.store.file(asset.id),AtomicBoolean(false))else 33334L};val row=JSONArray();for(t in tracks)row.put(JSONObject().put("mime",t.mime).put("samples",t.samples).put("firstPtsUs",t.firstPtsUs).put("lastPtsUs",t.lastPtsUs).put("maxGapUs",t.maxGapUs).put("durationUs",t.durationUs).put("monotonic",t.monotonic).put("duplicates",t.duplicates).put("width",t.width).put("height",t.height).put("rotation",t.rotation).put("sampleRate",t.sampleRate).put("channels",t.channels));report.put("retainedPartialBytes",retainedPartial.length()).put("retainedPartialTracks",row).put("expectedVideoGapUs",gap);try{ProductExportPolicy.validate(run.movie.durationUs,tracks,gap);report.put("retainedPartialValidate","PASS")}catch(e:Exception){report.put("retainedPartialValidate",e.message)}}catch(e:Exception){report.put("retainedPartialInspect",e.message)}}
   val rawSource=NativePcmEvidence.decode(run.store.file(a.id),deadline);rawSource.writeFloat(File(run.artifacts,"original-a-raw-codec.f32le"));report.put("sourceARaw",rawSource.format)
   assertEquals("actual product export (gapless rejection is a BLOCK, not pass)","EXPORT_READY",terminal?.get("state"))
   val referenceA=expectedOriginal(run,a);val referenceB=expectedOriginal(run,b)
   val store=ProductExportStore(File(run.context.filesDir,"eventclip-birthday-exports"));val record=requireNotNull(store.latest());val output=store.verifiedFile(record.getProperty("id"));output.copyTo(File(run.artifacts,"product-output.mp4"));File(run.artifacts,"export-manifest.properties").writeText(record.entries.joinToString("\n"){"${it.key}=${it.value}"})
   val actual=NativePcmEvidence.decode(output,deadline);actual.writeFloat(File(run.artifacts,"output-decoded.f32le"));report.put("outputDecode",actual.format).put("outputSha256",ProductPrivateFiles.hash(output)).put("outputBytes",output.length())
   assertEquals(48000,actual.rate);assertEquals(2,actual.channels);assertTrue("decoded PTS discontinuity",actual.maxPtsDiscontinuityUs<=2000);assertTrue("decoded duration",abs(actual.frames*1000000L/actual.rate-run.movie.durationUs)<=100000)
   fun amp(freq:Double,at:Long,span:Long=100000)=actual.amplitude(freq,at,at+span)
   val photo=amp(1000.0,1000000);val unmarked=amp(1000.0,aSlot.startUs+100000);val marked=amp(1000.0,aSlot.startUs+980000,40000);val silentStart=run.movie.items[2].startUs;val silent=amp(1000.0,silentStart+250000)
   val voiceUnmarked=amp(440.0,aSlot.startUs+200000)/referenceA.amplitude(440.0,aSlot.startUs+200000,aSlot.startUs+300000)
   val voiceMarked=amp(440.0,aSlot.startUs+900000)/referenceA.amplitude(440.0,aSlot.startUs+900000,aSlot.startUs+1000000)
   val finalAt=run.movie.durationUs-200000;val finalVoice=amp(660.0,finalAt)/referenceB.amplitude(660.0,finalAt,finalAt+100000);val finalMusic=amp(1000.0,finalAt)/photo
   val lag=NativePcmEvidence.envelopeLag(actual,referenceA,aSlot.startUs+1000000)
   val peak=actual.peak();val saturated=actual.samples.count{abs(it)>=.999969f};val jumps=JSONArray();for(item in run.movie.items.drop(1))jumps.put(JSONObject().put("atUs",item.startUs).put("maxAdjacentStep",actual.jump(item.startUs)))
   report.put("photoMusicAmplitude",photo).put("unmarkedMusicRatio",unmarked/photo).put("markedMusicRatio",marked/photo).put("silentMusicRatio",silent/photo).put("originalUnmarkedVsExpectedHalf",voiceUnmarked).put("originalMarkedVsExpectedHalf",voiceMarked).put("finalOriginalVsExpectedHalf",finalVoice).put("finalMusicRatio",finalMusic).put("originalEnvelopeLagMs",lag.first).put("envelopeCorrelation",lag.second).put("decodedSamplePeak",peak).put("saturationSamples",saturated).put("boundaryJumps",jumps).put("aacTruePeakSafetyProven",false)
   assertTrue("photo-only common0.5",photo in .12.. .16);assertTrue("unmarked normal music",unmarked/photo in .85..1.15);assertTrue("silent slot common0.5",silent/photo in .85..1.15);assertTrue("marked music attenuated only",marked/photo in .10.. .28)
   assertTrue("original unmarked audible at relative0.5",voiceUnmarked in .8..1.2);assertTrue("original marked unaffected",voiceMarked in .8..1.2);assertTrue("final original unaffected by music fade",finalVoice in .8..1.2);assertTrue("final fade music only",finalMusic in .05.. .30)
   assertTrue("silent clip no original",amp(440.0,silentStart+250000)<.005&&amp(660.0,silentStart+250000)<.005);assertTrue("effective envelope timing",abs(lag.first)<=30&&lag.second>.9);assertTrue("finite decoded peak below saturation",peak.isFinite()&&peak<.99);assertEquals(0,saturated);for(i in 0 until jumps.length())assertTrue("boundary step",jumps.getJSONObject(i).getDouble("maxAdjacentStep")<.12)
   report.put("status","PASS_SYNTHETIC_NATIVE_ONLY")
  }catch(t:Throwable){report.put("status","FAILED_OR_BLOCKED").put("failureClass",t.javaClass.simpleName);throw t}
  finally{instrumentation.runOnMainSync{controller?.requestStop("EXPORT_INTERRUPTED")};report.put("elapsedMs",SystemClock.elapsedRealtime()-start);File(run.artifacts,"metrics.json").writeText(report.toString(2))}
 }
 @Test(timeout=30000) fun gaplessImpulseRawProbe_guardRemains(){
  val run=stage();val report=JSONObject().put("status","PROBE_ONLY_NOT_PRODUCT_SUPPORT");val rows=JSONArray();val deadline=SystemClock.elapsedRealtime()+25000
  try{for(name in listOf("impulses-lame.mp3","impulses-aac.m4a")){val file=File(run.context.cacheDir,name);instrumentation.context.assets.open("product-m06/$name").use{input->file.outputStream().use{input.copyTo(it)}};val pcm=NativePcmEvidence.decode(file,deadline);pcm.writeFloat(File(run.artifacts,"$name-decoded.f32le"));val peaks=(0 until pcm.frames).filter{abs(pcm.samples[it*pcm.channels])>.1};val row=pcm.format.put("fixture",name).put("firstAboveThresholdFrame",peaks.firstOrNull()).put("lastAboveThresholdFrame",peaks.lastOrNull()).put("knownInputFrames",44100).put("knownInputImpulseFrames",JSONArray(listOf(4410,39690)))
    val delay=row.getInt("declaredDelayFrames");val padding=row.getInt("declaredPaddingFrames");val rejected=try{GaplessPolicy.requireVerified(delay,padding);false}catch(_:IllegalArgumentException){true};if(delay!=0||padding!=0)assertTrue("product guard unchanged",rejected);row.put("productGuardRejected",rejected).put("exactlyOnceTrimEstablished",false);rows.put(row)}
  }finally{report.put("results",rows);File(run.artifacts,"gapless-probe.json").writeText(report.toString(2))}
 }
 @Test(timeout=50000) fun sharedCompositionPlayer_surfaceAndRealClockSmoke(){
  val run=stage();val prepared=ProductNativeComposition.prepare(run.context,run.store,run.movie,AtomicBoolean(false),true);val report=JSONObject().put("status","RUNNING");var player:CompositionPlayer?=null;val rendered=CountDownLatch(1);val ready=CountDownLatch(1);var scenario:ActivityScenario<ProductSurfaceSmokeActivity>?=null
  try{scenario=ActivityScenario.launch(Intent(instrumentation.targetContext,ProductSurfaceSmokeActivity::class.java));scenario.onActivity{activity->player=CompositionPlayer.Builder(activity).setLooper(Looper.getMainLooper()).build().also{p->p.addListener(object:Player.Listener{override fun onRenderedFirstFrame(){rendered.countDown()};override fun onPlaybackStateChanged(state:Int){if(state==Player.STATE_READY)ready.countDown()}});p.setVideoSurfaceView(activity.surface);p.setComposition(prepared.composition);p.prepare();p.play()}}
   assertTrue("real native READY",ready.await(25,TimeUnit.SECONDS));assertTrue("actual SurfaceView rendered frame",rendered.await(10,TimeUnit.SECONDS));val observed=CountDownLatch(1);var position=0L;Handler(Looper.getMainLooper()).postDelayed({position=player!!.currentPosition;player!!.pause();observed.countDown()},1200);assertTrue(observed.await(3,TimeUnit.SECONDS));assertTrue("real player clock advanced",position>=500)
   report.put("observedPositionMs",position).put("status","PASS_SURFACE_CLOCK_SMOKE_ONLY").put("humanVisualOrAudioAcceptance",false)
  }finally{instrumentation.runOnMainSync{player?.release()};scenario?.close();ProductWorkspaces(requireNotNull(prepared.workingDirectory.parentFile)).release(prepared.workingDirectory);File(run.artifacts,"preview-smoke.json").writeText(report.toString(2))}
 }
}
