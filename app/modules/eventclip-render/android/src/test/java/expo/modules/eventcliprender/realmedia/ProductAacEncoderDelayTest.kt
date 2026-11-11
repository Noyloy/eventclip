package expo.modules.eventcliprender.realmedia
import org.junit.Assert.*
import org.junit.Test

class ProductAacEncoderDelayTest {
 @Test fun aacUsesSignaledDelayOtherwise2048AndNonAacIsZero(){
  assertTrue(ProductAacEncoderDelay.applies("audio/mp4a-latm"))
  assertTrue(ProductAacEncoderDelay.applies("audio/mp4a-latm"))
  assertEquals(2048,ProductAacEncoderDelay.delaySamples("audio/mp4a-latm",0))
    assertEquals(1024,ProductAacEncoderDelay.delaySamples("audio/mp4a-latm",1024))
  assertEquals(2048,ProductAacEncoderDelay.delaySamples("audio/mp4a-latm",2048))
  assertEquals(0,ProductAacEncoderDelay.delaySamples("audio/opus",0))
  assertEquals(0,ProductAacEncoderDelay.delaySamples("audio/raw",2048))
  assertFalse(ProductAacEncoderDelay.applies("video/avc"))
 }
 @Test fun delayUsIsExactFor2048At48kAndMatchesTwoAacFrames(){
  assertEquals(42666L,ProductAacEncoderDelay.delayUs(2048,48000))
  assertEquals(21333L,ProductAacEncoderDelay.delayUs(1024,48000))
  assertEquals(2*ProductAacEncoderDelay.FRAME_SAMPLES,ProductAacEncoderDelay.DEFAULT_DELAY_SAMPLES)
 }
}
