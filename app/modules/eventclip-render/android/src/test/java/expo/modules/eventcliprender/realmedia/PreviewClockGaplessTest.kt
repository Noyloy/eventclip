package expo.modules.eventcliprender.realmedia
import org.junit.Assert.*
import org.junit.Test
class PreviewClockGaplessTest {
 @Test fun noDoubleTrimWithoutVerifiedDecoderContract(){GaplessPolicy.requireVerified(0,0);for(pair in listOf(1024 to 0,0 to 576,2112 to 960))try{GaplessPolicy.requireVerified(pair.first,pair.second);fail()}catch(_:IllegalArgumentException){};assertEquals(23219L,GaplessPolicy.frameTimeUs(1024,44100))}
 @Test fun realClockRequiresReadyRenderedNonSeekingAndMatchingSlot(){assertEquals(500000L,PreviewClock.clipTimeUs(4500,true,true,false,4000000,2000000));assertNull(PreviewClock.clipTimeUs(4500,true,false,false,4000000,2000000));assertNull(PreviewClock.clipTimeUs(4500,true,true,true,4000000,2000000));assertNull(PreviewClock.clipTimeUs(3500,true,true,false,4000000,2000000));assertNull(PreviewClock.clipTimeUs(6000,true,true,false,4000000,2000000));assertEquals(2000000L,PreviewClock.clipTimeUs(6000,true,true,false,4000000,2000000,true))}
 @Test fun oldLeaseCannotReleaseNewOwner(){assertTrue(ProductWork.acquire("one"));assertFalse(ProductWork.acquire("two"));ProductWork.release("two");assertTrue(ProductWork.owns("one"));ProductWork.release("one");assertTrue(ProductWork.acquire("two"));ProductWork.release("one");assertTrue(ProductWork.owns("two"));ProductWork.release("two")}
 @Test fun ninetySeconds44100To48000KeepsEveryOutputFrameWithoutDrift(){val bus=ProductPcmBus(90000000);val sink=ProductPcmTimeline(bus,0,90000000,1234567){.5};val block=FloatArray(441){.5f};for(i in 0 until 9000)sink.block(1234567L+i*10000L,44100,1,block);sink.end();assertEquals(3969000L,sink.receivedFrames);assertEquals(4320000,bus.frames);assertTrue(bus.samples.all{it.toInt()==8191})}
}
