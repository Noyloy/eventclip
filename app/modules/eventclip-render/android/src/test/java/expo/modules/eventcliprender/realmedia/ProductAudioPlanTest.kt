package expo.modules.eventcliprender.realmedia
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.sin
class ProductAudioPlanTest {
  private fun movie()=FoundationPolicy.plan(listOf(VisualInput("photo","image",0),VisualInput("voice","video",4000000),VisualInput("silent","video",1000000),VisualInput("last","video",2000000)),20000000,emptyList())
  private fun plan()=ProductAudioPlanner.build(movie(),listOf(OriginalAudioProbe("voice",250000,44100,1),OriginalAudioProbe("last",1000000,48000,2)),listOf(VoiceMark("voice",1000000,3000000)),setOf("photo","silent"))
  @Test fun sourceTimestampAnchorsAndSilenceSlots(){
    val p=plan();assertEquals(2,p.originals.size)
    assertEquals(4000000L,p.originals[0].movieStartUs);assertEquals(4500000L,p.originals[0].movieTimeForSourcePts(750000))
    assertEquals(9000000L,p.originals[1].movieStartUs);assertEquals(48000,p.outputSampleRate);assertEquals(2,p.outputChannels)
    assertEquals(4000000L,OriginalAudioSpan("negative-preroll",4000000,1000000,-21333,48000,2).movieTimeForSourcePts(-21333))
    assertTrue(p.originals.none{it.assetId in setOf("photo","silent")});assertTrue(p.originals.all{it.gain==1.0})
  }
  @Test fun onlyMarkedMusicAttenuatesAndFinalFadeNeverTouchesOriginals(){
    val p=plan();assertEquals(1.0,p.musicGainAt(4999999),0.0);assertEquals(0.15,p.musicGainAt(6000000),0.000001)
    assertEquals(1.0,p.musicGainAt(7000000),0.0);assertEquals(0.5,p.musicGainAt(10500000),0.000001)
    assertEquals(1.0,p.originals.last().gain,0.0);assertEquals(0.0,p.musicGainAt(11000000),0.0)
  }
  @Test fun incompleteMarksBlockAndSkipRetainsSavedMarks(){
    val saved=listOf(VoiceMark("voice",1000000,3000000));assertEquals(saved,ProductAudioPlanner.continueWithoutAddingMarks(saved))
    try{ProductAudioPlanner.build(movie(),listOf(OriginalAudioProbe("voice",250000,44100,1)),listOf(VoiceMark("voice",1000000,null)),setOf("photo","silent","last"));fail()}catch(e:ProductAudioFailure){assertEquals(ProductAudioCode.INCOMPLETE_VOICE_RANGE,e.code)}
  }
  @Test fun invalidChannelLayoutIsNotSilentlyDownmixed(){
    try{ProductAudioPlanner.build(movie(),listOf(OriginalAudioProbe("voice",0,48000,6)),emptyList(),setOf("photo","silent","last"));fail()}catch(e:ProductAudioFailure){assertEquals(ProductAudioCode.INVALID_AUDIO_METADATA,e.code)}
  }
  @Test fun unityCoherentToneSumNeedsOwnerPolicyBeforeNativeRender(){
    val p=plan();var peak=0.0;var exceeds=0
    for(i in 0 until 48000){val sample=0.8*sin(2*Math.PI*1000*i/48000);val sum=sample+sample;peak=maxOf(peak,kotlin.math.abs(sum));if(kotlin.math.abs(sum)>1.0)exceeds++}
    assertEquals(1.6,peak,0.000001);assertTrue(exceeds>0)
    assertEquals(0.92,0.8+0.8*p.musicGainAt(6000000),0.000001)
    p.requireRenderPolicy();assertEquals(0.5,p.commonPostMixGain,0.0);assertEquals(0.8,peak*p.commonPostMixGain,0.000001)
  }
}
