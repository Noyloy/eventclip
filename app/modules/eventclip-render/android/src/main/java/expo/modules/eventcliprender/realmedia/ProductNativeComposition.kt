package expo.modules.eventcliprender.realmedia
import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class PreparedProductComposition(val composition:Composition,val workingDirectory:File,val audioPlan:ProductAudioPlan,val moviePlan:MoviePlan,val hasPendingVoiceStart:Boolean)
/** Worker-thread prepare -> main-thread Transformer start. No UI wiring/automatic export. */
object ProductNativeComposition {
  fun prepare(context:Context,store:BirthdayProjectStore,movie:MoviePlan,cancelled:AtomicBoolean,allowPendingVoiceStart:Boolean=false):PreparedProductComposition {
    check(Looper.myLooper()!=Looper.getMainLooper())
    validateProductAudioTopology(movie)
    val project=store.load();require(project.pendingVoiceStart==null || allowPendingVoiceStart)
    val song=project.assets.single{it.kind=="song"};val assets=movie.items.map{item->project.assets.single{it.id==item.id && it.included}}
    require(assets.size==project.assets.count{it.kind!="song" && it.included});require(movie.durationUs<=song.durationUs)
    require((assets+song).all{store.file(it.id).isFile && store.file(it.id).length()==it.bytes}){"PRIVATE_SOURCE_UNREADABLE"}
    val cache=File(context.cacheDir,"eventclip-birthday-render");require(cache.canonicalFile==cache.absoluteFile);cache.mkdirs()
    val work=ProductWorkspaces(cache).allocate()
    try{
      val needed=((movie.durationUs*48000+999999)/1000000)*8+88
      if(work.usableSpace-FoundationPolicy.FREE_RESERVE_BYTES<needed)throw FoundationFailure(ImportCode.STORAGE_LOW)
      val probes=assets.associate {a->a.id to if(a.kind=="video")ProductAudioDecoder.probe(store.file(a.id))else null}
      val audioProbes=probes.mapNotNull{(id,p)->if(p?.audioTrack!=null)OriginalAudioProbe(id,p.audioFirstPtsUs!!,requireNotNull(p.sampleRate),requireNotNull(p.channels))else null}
      val marks=project.voiceRanges.filter{m->audioProbes.any{it.assetId==m.assetId}}.map{VoiceMark(it.assetId,it.startUs,it.endUs)}
      // Marks on an included video with no audio are invalid, not silently disabled.
      require(project.voiceRanges.none{m->assets.any{it.id==m.assetId} && audioProbes.none{it.assetId==m.assetId}}){"VOICE_MARK_HAS_NO_AUDIO"}
      val plan=ProductAudioPlanner.build(movie,audioProbes,marks,movie.items.map{it.id}.toSet()-audioProbes.map{it.assetId}.toSet())
      val originals=ProductPcmBus(movie.durationUs)
      for(item in movie.items){
        if(cancelled.get())throw FoundationFailure(ImportCode.IMPORT_CANCELLED)
        val p=probes[item.id]?:continue;val track=p.audioTrack?:continue
        val origin=requireNotNull(p.videoFirstPtsUs){"VIDEO_TIMESTAMP_UNAVAILABLE"};require(origin!=-1L)
        val sink=ProductPcmTimeline(originals,item.startUs,item.durationUs,origin){0.5}
        ProductAudioDecoder.decode(store.file(item.id),track,sink,cancelled,origin+item.durationUs)
      }
      val originalFile=File(work,"original.wav");originals.writeWav(originalFile)
      val music=ProductPcmBus(movie.durationUs);val songProbe=ProductAudioDecoder.probe(store.file(song.id));val musicOrigin=requireNotNull(songProbe.audioFirstPtsUs)
      ProductAudioDecoder.decode(store.file(song.id),requireNotNull(songProbe.audioTrack),ProductPcmTimeline(music,0,movie.durationUs,musicOrigin){t->0.5*plan.musicGainAt(t)},cancelled,musicOrigin+movie.durationUs)
      val musicFile=File(work,"music.wav");music.writeWav(musicFile)
      if(cancelled.get())throw FoundationFailure(ImportCode.IMPORT_CANCELLED)
      val fit=Presentation.createForWidthAndHeight(720,1280,Presentation.LAYOUT_SCALE_TO_FIT)
      val visuals=movie.items.zip(assets).map{(item,a)->
        val builder=MediaItem.Builder().setUri(store.file(a.id).toURI().toString()).setMimeType(if(a.kind=="image")a.mime else MimeTypes.VIDEO_MP4)
        if(a.kind=="image")builder.setImageDurationMs((item.durationUs+999)/1000)
        else require(item.durationUs==a.durationUs){"WHOLE_VIDEO_DURATION_MISMATCH"}
        EditedMediaItem.Builder(builder.build()).setRemoveAudio(true).setDurationUs(item.durationUs).setFrameRate(30).setEffects(Effects(emptyList(),listOf(fit))).build()
      }
      fun audio(file:File)=EditedMediaItemSequence.Builder(listOf(EditedMediaItem.Builder(MediaItem.Builder().setUri(file.toURI().toString()).setMimeType(MimeTypes.AUDIO_WAV).build()).setRemoveVideo(true).build())).build()
      // Visuals have no audio; exactly ONE original and ONE song audio sequence, both already half-gain.
      val composition=Composition.Builder(EditedMediaItemSequence.Builder(visuals).build(),audio(originalFile),audio(musicFile)).setEffects(Effects(emptyList(),listOf(OverlayEffect(listOf(ProductWatermark()))))).setTransmuxAudio(false).build()
      return PreparedProductComposition(composition,work,plan,movie,project.pendingVoiceStart!=null)
    }catch(e:Exception){ProductWorkspaces(cache).release(work);throw e} // only this newly-created owned UUID work directory
  }
  fun start(context:Context,prepared:PreparedProductComposition,output:File,listener:Transformer.Listener):Transformer {
    check(Looper.myLooper()==Looper.getMainLooper());require(!prepared.hasPendingVoiceStart);require(!output.exists())
    // Controller holds the lease THROUGH audit/atomic retention. Default mux delay21000ms unchanged.
    val transformer=Transformer.Builder(context).setAudioMimeType(MimeTypes.AUDIO_AAC).setVideoMimeType(MimeTypes.VIDEO_H264).addListener(listener).build()
    transformer.start(prepared.composition,output.absolutePath);return transformer
  }
}
