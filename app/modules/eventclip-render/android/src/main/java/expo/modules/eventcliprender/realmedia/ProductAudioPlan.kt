package expo.modules.eventcliprender.realmedia

/** Product-only audio contract. Not connected to diagnostic engine or Android export yet. */
enum class ProductAudioCode { MIX_POLICY_REQUIRED, INCOMPLETE_VOICE_RANGE, INVALID_AUDIO_METADATA }
class ProductAudioFailure(val code:ProductAudioCode):Exception(code.name)
data class OriginalAudioProbe(val assetId:String,val sourceFirstPtsUs:Long,val sampleRate:Int,val channels:Int)
data class VoiceMark(val assetId:String,val startUs:Long,val endUs:Long?)
data class OriginalAudioSpan(val assetId:String,val movieStartUs:Long,val durationUs:Long,val sourceFirstPtsUs:Long,val sourceRate:Int,val sourceChannels:Int) {
  val gain=1.0 // marks, song fade, skip-all never change original gain
  fun movieTimeForSourcePts(sourcePtsUs:Long):Long {
    require(sourcePtsUs>=sourceFirstPtsUs)
    val offset=Math.subtractExact(sourcePtsUs,sourceFirstPtsUs);require(offset<durationUs)
    return movieStartUs+offset
  }
}
data class MusicMark(val startUs:Long,val endUs:Long)
data class ProductAudioPlan(val durationUs:Long,val originals:List<OriginalAudioSpan>,val marks:List<MusicMark>,val finalMusicFadeStartUs:Long?) {
  val commonPostMixGain=0.5 // owner-approved fixed gain, ALL sections including photos/silence
  val outputSampleRate=48000
  val outputChannels=2
  /** Native conversion target:48k stereo; mono duplicated, stereo retained; other layouts reject.
   * No custom resampler / Android conversion correctness is asserted by this pure plan. */
  fun musicGainAt(movieUs:Long):Double {
    if(movieUs<0 || movieUs>=durationUs)return 0.0
    var gain=1.0
    for(m in marks)if(movieUs>=m.startUs && movieUs<m.endUs){
      val edge=minOf(500000L,(m.endUs-m.startUs)/2)
      val depth=if(edge==0L)1.0 else minOf(1.0,(movieUs-m.startUs).toDouble()/edge,(m.endUs-movieUs).toDouble()/edge)
      gain=minOf(gain,1.0-0.85*depth) // ramps INSIDE marks; unmarked music stays normal
    }
    finalMusicFadeStartUs?.let{start->if(movieUs>=start)gain*=((durationUs-movieUs).toDouble()/(durationUs-start)).coerceIn(0.0,1.0)}
    return gain
  }
  fun requireRenderPolicy(){check(commonPostMixGain==0.5)}
}
object ProductAudioPlanner {
  /** Excluded clips aren't in movie items. Every item requires an audio probe or explicit no-audio classification; never silently omit a missing probe. */
  fun build(movie:MoviePlan,probes:List<OriginalAudioProbe>,marks:List<VoiceMark>,explicitSilentAssetIds:Set<String>):ProductAudioPlan {
    val movieIds=movie.items.map{it.id}.toSet()
    if(probes.any{it.assetId in explicitSilentAssetIds} || probes.map{it.assetId}.toSet()+explicitSilentAssetIds!=movieIds)throw ProductAudioFailure(ProductAudioCode.INVALID_AUDIO_METADATA)
    require(probes.map{it.assetId}.distinct().size==probes.size)
    val originals=probes.map {p->
      val item=movie.items.singleOrNull{it.id==p.assetId} ?: throw ProductAudioFailure(ProductAudioCode.INVALID_AUDIO_METADATA)
      if(p.sampleRate !in setOf(8000,11025,12000,16000,22050,24000,32000,44100,48000,88200,96000) || p.channels !in 1..2)throw ProductAudioFailure(ProductAudioCode.INVALID_AUDIO_METADATA)
      OriginalAudioSpan(p.assetId,item.startUs,item.durationUs,p.sourceFirstPtsUs,p.sampleRate,p.channels)
    }
    val musicMarks=marks.map {m->
      val span=originals.singleOrNull{it.assetId==m.assetId} ?: throw ProductAudioFailure(ProductAudioCode.INVALID_AUDIO_METADATA)
      val end=m.endUs ?: throw ProductAudioFailure(ProductAudioCode.INCOMPLETE_VOICE_RANGE)
      if(m.startUs<0 || end<=m.startUs || end>span.durationUs)throw ProductAudioFailure(ProductAudioCode.INCOMPLETE_VOICE_RANGE)
      MusicMark(span.movieStartUs+m.startUs,span.movieStartUs+end)
    }
    return ProductAudioPlan(movie.durationUs,originals,musicMarks,movie.musicFadeStartUs)
  }
  fun continueWithoutAddingMarks(saved:List<VoiceMark>):List<VoiceMark> = saved.toList() // never clear saved marks implicitly
}

fun validateProductAudioTopology(movie:MoviePlan){
 require(movie.durationUs in 1..FoundationPolicy.MAX_MOVIE_US)
 require(movie.items.map{it.id}.distinct().size==movie.items.size)
 var cursor=0L
 for(item in movie.items){require(item.startUs==cursor && item.durationUs>0 && item.durationUs<=movie.durationUs-cursor){"AUDIO_TIMELINE_OVERLAP_OR_GAP"};cursor+=item.durationUs}
 require(cursor==movie.durationUs)
}
