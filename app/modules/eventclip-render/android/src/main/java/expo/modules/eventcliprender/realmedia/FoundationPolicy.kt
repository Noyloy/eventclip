package expo.modules.eventcliprender.realmedia

import java.io.File
import java.io.InputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

enum class ImportCode { COPYING, CHECKING, READY, IMPORT_CANCELLED, ACCESS_UNREADABLE, UNSUPPORTED_MEDIA, HEIC_UNSUPPORTED, PROTECTED_AUDIO, COPY_FAILED, STORAGE_LOW, ITEM_LIMIT, SOURCE_SIZE_LIMIT, DURATION_LIMIT, SONG_TOO_SHORT }
class FoundationFailure(val code: ImportCode) : Exception(code.name)
enum class ExportCode { PLANNING, READY_TO_CREATE, EXPORTING, CANCEL_REQUESTED, EXPORT_CANCELLED, EXPORT_INTERRUPTED, EXPORT_FAILED, EXPORT_READY, SAVING, SAVED, SAVE_FAILED, STORAGE_LOW }
data class VisualInput(val id:String,val kind:String,val durationUs:Long,val included:Boolean=true)
data class PlannedItem(val id:String,val startUs:Long,val durationUs:Long) {
  val startSec get()=startUs/1000000.0
  val durationSec get()=durationUs/1000000.0
}
data class MoviePlan(val items:List<PlannedItem>,val durationUs:Long,val musicFadeStartUs:Long?,val musicEndsAtMovieEnd:Boolean) {
  val durationSec get()=durationUs/1000000.0
  val musicFadeStartSec get()=musicFadeStartUs?.div(1000000.0)
  val displaySecondsCeil get()=(durationUs+999999L)/1000000L
}
/** Owner guardrails, not measured device capacity. All arrangement comparisons use exact microseconds. */
object FoundationPolicy {
  const val MAX_VISUAL=30
  const val MAX_SOURCE_BYTES=1073741824L
  const val MAX_MOVIE_US=90000000L
  const val FREE_RESERVE_BYTES=67108864L
  fun plan(inputs:List<VisualInput>,songUs:Long,beatsUs:List<Long>):MoviePlan {
    if(inputs.size>MAX_VISUAL)throw FoundationFailure(ImportCode.ITEM_LIMIT)
    require(songUs>0)
    val beats=beatsUs.filter{it>=0}.sorted()
    val items=ArrayList<PlannedItem>();var start=0L
    for(v in inputs.filter{it.included}) {
      require(v.kind=="image" || v.kind=="video")
      val duration=if(v.kind=="image") {
        val nearest=beats.filter{it>=start+3000000L && it<=start+5000000L}.minWithOrNull(compareBy<Long>{kotlin.math.abs(it-(start+4000000L))}.thenBy{it})
        nearest?.minus(start) ?: 4000000L
      }else{require(v.durationUs>0);v.durationUs}
      if(duration>MAX_MOVIE_US-start)throw FoundationFailure(ImportCode.DURATION_LIMIT)
      items.add(PlannedItem(v.id,start,duration));start+=duration
    }
    require(items.isNotEmpty())
    if(songUs<start)throw FoundationFailure(ImportCode.SONG_TOO_SHORT)
    val longer=songUs>start
    return MoviePlan(items,start,if(longer)maxOf(0L,start-1000000L)else null,longer)
  }
  fun finalBudgetBeforeCopy(assets:List<ImportedAsset>,replacingSong:Boolean):Long = assets.filterNot{replacingSong && it.kind=="song"}.sumOf{it.bytes}
  fun validateLocalProvider(scheme: String?, authority: String?) {
    if(scheme != "content" || authority !in setOf("media","com.android.providers.media.photopicker","com.android.providers.media.documents","com.android.externalstorage.documents","com.android.providers.downloads.documents")) throw FoundationFailure(ImportCode.ACCESS_UNREADABLE)
    // Authority allowlist rejects direct remote providers, but cannot prove picker-backed media was local.
  }
  fun copyBounded(input: InputStream, partial: File, existingBytes: Long, freeBytes: ()->Long, cancelled: AtomicBoolean): Long {
    var copied=0L
    try {
      FileOutputStream(partial).use { out ->
        val buf=ByteArray(65536)
        while(true) {
          if(cancelled.get())throw FoundationFailure(ImportCode.IMPORT_CANCELLED)
          val n=input.read(buf)
          if(n<0)break
          if(n==0)continue
          if(existingBytes+copied+n > MAX_SOURCE_BYTES)throw FoundationFailure(ImportCode.SOURCE_SIZE_LIMIT)
          if(freeBytes() < FREE_RESERVE_BYTES+n)throw FoundationFailure(ImportCode.STORAGE_LOW)
          out.write(buf,0,n);copied+=n
        }
        out.fd.sync()
      }
      if(cancelled.get())throw FoundationFailure(ImportCode.IMPORT_CANCELLED)
      if(copied==0L)throw FoundationFailure(ImportCode.COPY_FAILED)
      return copied
    }catch(e:Exception){partial.delete();throw e}
  }
}
