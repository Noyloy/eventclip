package expo.modules.eventcliprender.realmedia

import androidx.media3.common.C
import androidx.media3.transformer.Transformer

/**
 * Product mux-stall watchdog contract.
 *
 * Media3 Transformer aborts with [ERROR_CODE_MUXING_TIMEOUT] (7002) and
 * "Abort: no output sample written in the last %d milliseconds" when no muxer
 * sample is written for [MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS] of *host* time
 * after the muxer becomes ready (all track formats registered). The timer is
 * reset on every accepted muxer write. Passing [C.TIME_UNSET] disables it.
 *
 * This is a no-progress stall detector. It is not a total elapsed-time budget
 * and is not scaled to movie duration. A 32s (or up to 90s) Birthday export
 * may take longer than 21s of wall time as long as muxer samples keep arriving.
 * Official short fixtures (~9s, a few seconds on device) never exercise that
 * distinction. Do not raise this value to hide stalls.
 *
 * Media3 1.5.1 does not treat [Transformer.DEFAULT_MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS]
 * as a compile-time constant. Transformer clinit sets it to 21000L on an emulator
 * and 10000L on a physical device ([androidx.media3.common.util.Util.isRunningOnEmulator]).
 * The product always applies 21000, including on device; do not require equality with
 * that default, and do not fall through to the 10s device default.
 */
internal object ProductMuxWatchdog {
  // Hardcoded 21000 so this object can load on the JVM without Transformer clinit, and so the
  // product stall detector stays 21s on both emulator (Media3 default 21000) and device (10000).
  const val MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS=21000L
  const val ERROR_CODE_MUXING_TIMEOUT=7002
  fun apply(builder:Transformer.Builder):Transformer.Builder {
    require(MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS>0 && MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS!=C.TIME_UNSET)
    return builder.setMaxDelayBetweenMuxerSamplesMs(MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS)
  }
  /** True when [elapsedMs] of host time has passed with no muxer sample; false if samples flowed across a longer export. */
  fun stalled(nowMs:Long,lastSampleMs:Long,maxDelayMs:Long=MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS):Boolean {
    require(maxDelayMs>0 && maxDelayMs!=C.TIME_UNSET)
    return nowMs-lastSampleMs>=maxDelayMs
  }
}
