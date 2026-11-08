package expo.modules.eventcliprender.realmedia
/** Official keys count AUDIO FRAMES (all channels), not individual channel samples.
 * Android decoder trimming/PTS behavior unverified on target; never apply a speculative second trim. */
object GaplessPolicy {
 fun requireVerified(delayFrames:Int,paddingFrames:Int){require(delayFrames>=0&&paddingFrames>=0);require(delayFrames==0&&paddingFrames==0){"AUDIO_GAPLESS_METADATA_UNSUPPORTED"}}
 fun frameTimeUs(frames:Long,rate:Int):Long{require(frames>=0&&rate in 8000..48000);return Math.multiplyExact(frames,1000000L)/rate}
}
