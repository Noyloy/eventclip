package expo.modules.eventcliprender.realmedia

import androidx.media3.common.C
import androidx.media3.transformer.Transformer
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Field

/** Contract: 21000ms is a mux no-sample stall threshold, not a total export elapsed budget. */
class ProductMuxWatchdogTest {
 @Test fun stallThresholdIs21000AndNotDisabled() {
  assertEquals(21000L,ProductMuxWatchdog.MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS)
  assertEquals(7002,ProductMuxWatchdog.ERROR_CODE_MUXING_TIMEOUT)
  assertNotEquals(C.TIME_UNSET,ProductMuxWatchdog.MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS)
  assertTrue(ProductMuxWatchdog.MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS>0)
  // Movie duration may exceed the stall threshold (32s and the 90s cap both do).
  assertTrue(FoundationPolicy.MAX_MOVIE_US>ProductMuxWatchdog.MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS*1000)
  assertTrue(32_000_000L>ProductMuxWatchdog.MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS*1000)
 }

 @Test fun stallVsElapsedWithFakeClock() {
  val t0=1_000_000L
  // Samples keep arriving every 200ms across a 40s export — never a stall, even past 21s elapsed.
  var last=t0
  for(elapsed in 200L..40_000L step 200L){
   val now=t0+elapsed
   assertFalse("elapsed ${elapsed}ms with regular mux writes is not a stall",ProductMuxWatchdog.stalled(now,last))
   last=now
  }
  assertTrue("40s total elapsed is allowed when samples flowed",t0+40_000L-t0>ProductMuxWatchdog.MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS)
  // 21s with no write after the last sample is a stall, independent of movie duration.
  assertFalse(ProductMuxWatchdog.stalled(last+20_999L,last))
  assertTrue(ProductMuxWatchdog.stalled(last+21_000L,last))
  assertTrue(ProductMuxWatchdog.stalled(last+60_000L,last))
 }

 /**
  * Media3 1.5.1 Transformer clinit sets DEFAULT_MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS to
  * 21000L when Util.isRunningOnEmulator() is true, else 10000L. A physical S23 uses 10000.
  * The host JVM is not an emulator, so DEFAULT is 10000 — the same split that made
  * `require(21000==Transformer.DEFAULT)` throw at start() on S23 (and pass on the emu).
  * apply() must still succeed and set 21000. Do not re-add an equality require.
  */
 @Test fun applySets21000WhenMedia3DeviceDefaultIs10000() {
  // android.jar stubs leave Build.DEVICE null; Util.isRunningOnEmulator then NPEs in Transformer clinit.
  // Install a physical-device DEVICE string before first Transformer use, and onto Util if it already loaded.
  setStaticString(android.os.Build::class.java,"DEVICE","vendordevice")
  try{setStaticString(Class.forName("androidx.media3.common.util.Util"),"DEVICE","vendordevice")}catch(_:Throwable){}
  assertEquals("host JVM must see the physical-device Media3 default, not the emulator 21000",10000L,Transformer.DEFAULT_MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS)
  assertNotEquals(Transformer.DEFAULT_MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS,ProductMuxWatchdog.MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS)
  val builder=emptyBuilder()
  val applied=ProductMuxWatchdog.apply(builder)
  assertSame(builder,applied)
  assertEquals(21000L,delayField(applied))
 }

 @Test fun thirtyTwoSecondMixedPlanWithLongerSongIsLegalAndFades() {
  val inputs=List(7){VisualInput("photo$it","image",0)}+
   listOf(VisualInput("clip-a","video",2_023_242L,true),VisualInput("silent","video",1_000_000L,true))
  val plan=FoundationPolicy.plan(inputs,40_000_000L,emptyList())
  assertEquals(31_023_242L,plan.durationUs)
  assertEquals(32L,plan.displaySecondsCeil)
  assertTrue(plan.musicEndsAtMovieEnd)
  assertEquals(30_023_242L,plan.musicFadeStartUs)
  assertEquals(9,plan.items.size)
  assertEquals(0L,plan.items.first().startUs)
  assertEquals(28_000_000L,plan.items[7].startUs)
  assertEquals(30_023_242L,plan.items[8].startUs)
  validateProductAudioTopology(plan)
  val audio=ProductAudioPlanner.build(plan,listOf(OriginalAudioProbe("clip-a",0,44100,1)),emptyList(),setOf("photo0","photo1","photo2","photo3","photo4","photo5","photo6","silent"))
  assertEquals(31_023_242L,audio.durationUs)
  assertEquals(30_023_242L,audio.finalMusicFadeStartUs)
  assertTrue(audio.marks.isEmpty())
  assertEquals(1.0,audio.musicGainAt(0),0.0)
  assertEquals(0.5,audio.commonPostMixGain*audio.musicGainAt(15_000_000L),0.0)
  // Fade is over the last second; at the final microsecond music gain is ~0.
  assertEquals(0.0,audio.commonPostMixGain*audio.musicGainAt(31_023_241L),0.001)
  assertTrue(audio.commonPostMixGain*audio.musicGainAt(30_523_242L) in 0.20..0.30)
 }

 // Transformer.Builder(Context) pulls Looper and encoder factories the host JVM cannot construct.
 // apply() and setMaxDelayBetweenMuxerSamplesMs only write the delay field, so an uninitialized
 // instance is enough to prove the require does not demand DEFAULT==21000.
 private fun setStaticString(type:Class<*>,name:String,value:String) {
  val field=type.getDeclaredField(name);field.isAccessible=true
  val unsafeClass=Class.forName("sun.misc.Unsafe")
  val uf=unsafeClass.getDeclaredField("theUnsafe");uf.isAccessible=true
  val unsafe=uf.get(null)
  val base=unsafeClass.getMethod("staticFieldBase",Field::class.java).invoke(unsafe,field)
  val offset=unsafeClass.getMethod("staticFieldOffset",Field::class.java).invoke(unsafe,field) as Long
  unsafeClass.getMethod("putObject",Any::class.java,Long::class.javaPrimitiveType,Any::class.java).invoke(unsafe,base,offset,value)
 }
 private fun emptyBuilder():Transformer.Builder {
  val unsafeClass=Class.forName("sun.misc.Unsafe")
  val f=unsafeClass.getDeclaredField("theUnsafe");f.isAccessible=true
  val unsafe=f.get(null)
  return unsafeClass.getMethod("allocateInstance",Class::class.java).invoke(unsafe,Transformer.Builder::class.java) as Transformer.Builder
 }
 private fun delayField(builder:Transformer.Builder):Long {
  val f=Transformer.Builder::class.java.getDeclaredField("maxDelayBetweenMuxerSamplesMs");f.isAccessible=true
  return f.getLong(builder)
 }
}
