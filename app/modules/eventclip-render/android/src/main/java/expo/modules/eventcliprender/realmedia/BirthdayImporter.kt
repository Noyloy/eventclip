package expo.modules.eventcliprender.realmedia

import android.content.Context
import android.net.Uri
import android.database.Cursor
import android.provider.OpenableColumns
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodecList
import android.graphics.BitmapFactory
import android.os.Build
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class BirthdayImporter(private val context:Context, private val store:BirthdayProjectStore) {
  val cancelled=AtomicBoolean(false)
  private fun metadata(file:File,kind:String,id:String,bytes:Long,captured:Long):ImportedAsset {
    if(kind=="visual") {
      val head=file.inputStream().use{val b=ByteArray(32);it.read(b);b}
      val png=head[0]==0x89.toByte() && head[1]==0x50.toByte()
      val jpeg=head[0]==0xff.toByte() && head[1]==0xd8.toByte()
      if(png || jpeg) {
        if(png && head[24].toInt()!=8)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
        val o=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.absolutePath,o)
        if(o.outWidth<=0 || o.outHeight<=0)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
        val sample=BitmapFactory.Options().apply{inSampleSize=maxOf(1,maxOf(o.outWidth,o.outHeight)/256)}
        val bmp=BitmapFactory.decodeFile(file.absolutePath,sample) ?: throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
        try {
          if(Build.VERSION.SDK_INT>=34 && bmp.hasGainmap())throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
          // Conservative initial target: sRGB SDR only; no silent tone mapping.
          if(Build.VERSION.SDK_INT>=26 && bmp.colorSpace?.isSrgb==false)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
        } finally {bmp.recycle()}
        var rotation=0
        if(jpeg)try {
          val ex=android.media.ExifInterface(file.absolutePath)
          val orientation=ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)
          rotation=when(orientation){6->90;3->180;8->270;1->0;else->throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)}
        }catch(e:FoundationFailure){throw e}catch(_:Exception){}
        return ImportedAsset(id,"image",bytes,if(png)"image/png"else"image/jpeg",0,o.outWidth,o.outHeight,rotation,captured)
      }
    }
    val signature=file.inputStream().use{val b=ByteArray(12);it.read(b);b}
    val mp4=String(signature,4,4,Charsets.US_ASCII)=="ftyp"
    val wav=String(signature,0,4,Charsets.US_ASCII)=="RIFF" && String(signature,8,4,Charsets.US_ASCII)=="WAVE"
    val mp3=String(signature,0,3,Charsets.US_ASCII)=="ID3" || (signature[0]==0xff.toByte() && (signature[1].toInt() and 0xe0)==0xe0)
    if(kind=="visual" && !mp4)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
    if(kind=="song" && !mp4 && !wav && !mp3)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
    val ex=MediaExtractor()
    try {
      ex.setDataSource(file.absolutePath)
      if(ex.drmInitData!=null)throw FoundationFailure(if(kind=="song")ImportCode.PROTECTED_AUDIO else ImportCode.UNSUPPORTED_MEDIA)
      var video:MediaFormat?=null;var audio:MediaFormat?=null
      for(i in 0 until ex.trackCount){
        val f=ex.getTrackFormat(i);val mime=f.getString(MediaFormat.KEY_MIME)?:""
        if(mime.startsWith("video/")){if(video!=null)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA);video=f}
        if(mime.startsWith("audio/")){if(audio!=null)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA);audio=f}
      }
      val decoder=MediaCodecList(MediaCodecList.REGULAR_CODECS)
      if(kind=="song"){
        if(video!=null || audio==null)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
        val f=audio!!;val mime=f.getString(MediaFormat.KEY_MIME)?:""
        if(mime !in setOf("audio/mpeg","audio/mp4a-latm","audio/raw") || decoder.findDecoderForFormat(f)==null)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
        val durUs=f.getLong(MediaFormat.KEY_DURATION);val dur=durUs/1000
        if(durUs<=0)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
        return ImportedAsset(id,"song",bytes,mime,dur,durationUs=durUs)
      }
      val f=video ?: throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
      if(f.getString(MediaFormat.KEY_MIME)!="video/avc" || decoder.findDecoderForFormat(f)==null)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
      val transfer=if(f.containsKey("color-transfer"))f.getInteger("color-transfer")else 0
      val profile=if(f.containsKey("profile"))f.getInteger("profile")else 0
      if(transfer !in setOf(0,3) || profile !in setOf(0,1,2,4,8,65536,524288))throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
      if(audio!=null && (audio!!.getString(MediaFormat.KEY_MIME)!="audio/mp4a-latm" || decoder.findDecoderForFormat(audio!!)==null))throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
      val durUs=f.getLong(MediaFormat.KEY_DURATION);val dur=durUs/1000
      if(durUs<=0)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
      if(durUs>FoundationPolicy.MAX_MOVIE_US)throw FoundationFailure(ImportCode.DURATION_LIMIT)
      return ImportedAsset(id,"video",bytes,"video/avc",dur,f.getInteger(MediaFormat.KEY_WIDTH),f.getInteger(MediaFormat.KEY_HEIGHT),if(f.containsKey("rotation-degrees"))f.getInteger("rotation-degrees")else 0,captured,durationUs=durUs)
    }catch(e:FoundationFailure){throw e}catch(_:Exception){throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)}finally{ex.release()}
  }
  @Volatile private var openSignal:android.os.CancellationSignal?=null
  fun cancelPending(){cancelled.set(true);openSignal?.cancel()}
  fun importSelected(uri:Uri,kind:String,status:(String)->Unit,stagedBatch:Boolean=false):BirthdayProject {
    FoundationPolicy.validateLocalProvider(uri.scheme,uri.authority)
    if(!stagedBatch)store.beginImport()
    try {
      val project=store.activeImportProject()
      if(kind=="visual" && project.assets.count{it.kind!="song"}>=FoundationPolicy.MAX_VISUAL)throw FoundationFailure(ImportCode.ITEM_LIMIT)
      val budget=FoundationPolicy.finalBudgetBeforeCopy(project.assets,kind=="song")
      var captured=0L
      val signal=android.os.CancellationSignal();openSignal=signal
      if(cancelled.get())throw FoundationFailure(ImportCode.IMPORT_CANCELLED)
      try {context.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE,"datetaken"),null,null,null,signal)?.use { c ->
        if(c.moveToFirst()){
          val sz=c.getColumnIndex(OpenableColumns.SIZE)
          if(sz>=0 && !c.isNull(sz)){
            val size=c.getLong(sz)
            if(size>FoundationPolicy.MAX_SOURCE_BYTES-budget)throw FoundationFailure(ImportCode.SOURCE_SIZE_LIMIT)
            if(size>0 && store.assetDir.usableSpace<FoundationPolicy.FREE_RESERVE_BYTES+size)throw FoundationFailure(ImportCode.STORAGE_LOW)
          }
          val date=c.getColumnIndex("datetaken");if(date>=0 && !c.isNull(date))captured=c.getLong(date)
        }
      }}catch(e:FoundationFailure){throw e}catch(_:Exception){if(cancelled.get())throw FoundationFailure(ImportCode.IMPORT_CANCELLED)}
      val id=store.allocateImportId();val partial=store.partial(id);val final=store.file(id)
      status(ImportCode.COPYING.name)
      val afd=context.contentResolver.openAssetFileDescriptor(uri,"r",signal) ?: throw FoundationFailure(ImportCode.ACCESS_UNREADABLE)
      val bytes=afd.use { descriptor->descriptor.createInputStream().use{FoundationPolicy.copyBounded(it,partial,budget,{store.assetDir.usableSpace},cancelled)} }
      status(ImportCode.CHECKING.name)
      val asset=metadata(partial,kind,id,bytes,captured)
      if(cancelled.get())throw FoundationFailure(ImportCode.IMPORT_CANCELLED)
      check(!final.exists() && partial.renameTo(final))
      val next=store.acceptImported(asset,kind=="song")
      if(!stagedBatch)store.commitImport(cancelled)
      status(if(stagedBatch)"CHECKING" else ImportCode.READY.name) // staged copies aren't READY current-project assets until batch commit
      return next
    }catch(e:Exception){
      if(!stagedBatch)store.abortImport()
      if(cancelled.get())throw FoundationFailure(ImportCode.IMPORT_CANCELLED)
      when(e){is FoundationFailure->throw e;is SecurityException,is java.io.FileNotFoundException->throw FoundationFailure(ImportCode.ACCESS_UNREADABLE);else->throw FoundationFailure(ImportCode.COPY_FAILED)}
    }finally{openSignal=null}
  }
}
