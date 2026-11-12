package expo.modules.eventcliprender.realmedia

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import java.io.File
import java.io.FileOutputStream

/** Decode a still (JPEG/PNG/WebP/HEIF) to the sRGB SDR JPEG/PNG baseline used by export. */
internal data class NormalizedStill(val mime:String,val width:Int,val height:Int,val rotation:Int,val bytes:Long,val transcoded:Boolean)

internal object ProductImageImport {
 const val MAX_EDGE=4096
 /** Long-edge cap for Transformer stills. Import may keep larger SDR JPEGs; prepare always flattens to this. */
 const val EXPORT_MAX_EDGE=1280
 const val JPEG_QUALITY=90
 /**
  * Always write a baseline sRGB SDR JPEG at [EXPORT_MAX_EDGE] into [dest] for the export workspace.
  * Import may leave a 12MP, Ultra HDR, motion-photo, or non-JPEG still on disk; Media3's image
  * pipeline must not see those. Does not replace the imported source file.
  */
 fun exportStill(source:File,dest:File):File {
  val sniff=ProductImageSniff.of(source)
  if(!sniff.stillImage)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
  val orientation=if(sniff.kind==SniffedMedia.JPEG)jpegOrientation(source)else 1
  val owned=ArrayList<Bitmap>()
  try {
   val decoded=decodeSdr(source,sniff);owned.add(decoded)
   var work=flattenSdr(decoded);if(work!==decoded)owned.add(work)
   val oriented=applyOrientation(work,orientation);if(oriented!==work)owned.add(oriented)
   val fitted=fitMaxEdge(oriented,EXPORT_MAX_EDGE);if(fitted!==oriented)owned.add(fitted)
   dest.parentFile?.mkdirs()
   val tmp=File(dest.parentFile,dest.name+".tmp")
   try {
    FileOutputStream(tmp).use{out->
     if(Build.VERSION.SDK_INT>=34)fitted.gainmap=null
     if(!fitted.compress(Bitmap.CompressFormat.JPEG,JPEG_QUALITY,out))throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
     out.fd.sync()
    }
    check(tmp.length()>0 && (dest.delete()||!dest.exists()) && tmp.renameTo(dest))
   }catch(e:Exception){tmp.delete();if(e is FoundationFailure)throw e;throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)}
  }finally{for(b in owned.asReversed())runCatching{if(!b.isRecycled)b.recycle()}}
  return dest
 }
 fun normalize(file:File,sniff:MediaSniff):NormalizedStill {
  if(!sniff.stillImage)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
  val orientation=if(sniff.kind==SniffedMedia.JPEG)jpegOrientation(file)else 1
  val decoded=decodeSdr(file,sniff)
  try {
   val needsTranscode=sniff.kind !in setOf(SniffedMedia.JPEG,SniffedMedia.PNG)
    || (sniff.kind==SniffedMedia.PNG && !sniff.pngEightBit)
    || sniff.jpegHasTrailer
    || (Build.VERSION.SDK_INT>=34 && decoded.hasGainmap())
    || (Build.VERSION.SDK_INT>=26 && decoded.colorSpace!=null && decoded.colorSpace?.isSrgb==false)
    || orientation !in setOf(1,3,6,8)
   if(!needsTranscode){
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeFile(file.absolutePath,bounds)
    val width=if(bounds.outWidth>0)bounds.outWidth else decoded.width
    val height=if(bounds.outHeight>0)bounds.outHeight else decoded.height
    if(width<=0||height<=0)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
    val rotation=when(orientation){6->90;3->180;8->270;else->0}
    val mime=if(sniff.kind==SniffedMedia.PNG)"image/png" else "image/jpeg"
    return NormalizedStill(mime,width,height,rotation,file.length(),false)
   }
   val flat=flattenSdr(decoded)
   val oriented=try{applyOrientation(flat,orientation)}catch(e:Exception){if(flat!==decoded)flat.recycle();throw e}
   try {
    if(oriented!==flat && flat!==decoded)flat.recycle()
    val tmp=File(file.parentFile,file.name+".sdr.jpg")
    try {
     FileOutputStream(tmp).use{out->
      if(Build.VERSION.SDK_INT>=34)oriented.gainmap=null
      if(!oriented.compress(Bitmap.CompressFormat.JPEG,JPEG_QUALITY,out))throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
      out.fd.sync()
     }
     check(tmp.length()>0 && (file.delete()||!file.exists()) && tmp.renameTo(file))
    }catch(e:Exception){tmp.delete();if(e is FoundationFailure)throw e;throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)}
    return NormalizedStill("image/jpeg",oriented.width,oriented.height,0,file.length(),true)
   }finally{if(oriented!==decoded)oriented.recycle()}
  }finally{decoded.recycle()}
 }
 private fun decodeSdr(file:File,sniff:MediaSniff):Bitmap {
  val heif=sniff.kind==SniffedMedia.HEIF||sniff.kind==SniffedMedia.AVIF
  if(Build.VERSION.SDK_INT>=28){
   try {
    return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)){decoder,info,_->
     decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
     decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
     decoder.isMutableRequired=true
     val w=info.size.width;val h=info.size.height
     val edge=maxOf(w,h)
     if(edge>MAX_EDGE){
      val scale=edge.toFloat()/MAX_EDGE
      decoder.setTargetSize(maxOf(1,(w/scale).toInt()),maxOf(1,(h/scale).toInt()))
     }
    }
   }catch(e:FoundationFailure){throw e}catch(_:Exception){
    if(heif)throw FoundationFailure(ImportCode.HEIC_UNSUPPORTED)
    // JPEG/PNG/WebP can fall back to BitmapFactory.
   }
  }else if(heif)throw FoundationFailure(ImportCode.HEIC_UNSUPPORTED)
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
  BitmapFactory.decodeFile(file.absolutePath,bounds)
  if(bounds.outWidth<=0||bounds.outHeight<=0)throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
  val sample=maxOf(1,maxOf(bounds.outWidth,bounds.outHeight)/MAX_EDGE)
  val opts=BitmapFactory.Options().apply{
   inSampleSize=sample
   inMutable=true
   if(Build.VERSION.SDK_INT>=26)inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB)
  }
  return BitmapFactory.decodeFile(file.absolutePath,opts) ?: throw FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)
 }
 private fun srgbBitmap(width:Int,height:Int):Bitmap {
  return if(Build.VERSION.SDK_INT>=26)Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888,true,ColorSpace.get(ColorSpace.Named.SRGB))
  else Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
 }
 private fun flattenSdr(src:Bitmap):Bitmap {
  var work=src
  if(Build.VERSION.SDK_INT>=34 && work.hasGainmap()){
   val copy=srgbBitmap(work.width,work.height)
   android.graphics.Canvas(copy).drawBitmap(work,0f,0f,null)
   copy.gainmap=null
   work=copy
  }
  if(Build.VERSION.SDK_INT>=26 && work.colorSpace!=null && work.colorSpace?.isSrgb==false){
   val copy=srgbBitmap(work.width,work.height)
   android.graphics.Canvas(copy).drawBitmap(work,0f,0f,null)
   if(work!==src)work.recycle()
   work=copy
  }
  return work
 }
 private fun applyOrientation(src:Bitmap,orientation:Int):Bitmap {
  if(orientation==1)return src
  val m=Matrix()
  when(orientation){
   2->m.setScale(-1f,1f)
   3->m.setRotate(180f)
   4->m.setScale(1f,-1f)
   5->{m.setRotate(90f);m.postScale(-1f,1f)}
   6->m.setRotate(90f)
   7->{m.setRotate(270f);m.postScale(-1f,1f)}
   8->m.setRotate(270f)
   else->return src
  }
  return Bitmap.createBitmap(src,0,0,src.width,src.height,m,true)
 }
 private fun fitMaxEdge(src:Bitmap,maxEdge:Int):Bitmap {
  val edge=maxOf(src.width,src.height)
  if(edge<=maxEdge)return src
  val scale=maxEdge.toFloat()/edge
  return Bitmap.createScaledBitmap(src,maxOf(1,(src.width*scale).toInt()),maxOf(1,(src.height*scale).toInt()),true)
 }
 private fun jpegOrientation(file:File):Int {
  return try{
   val value=ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
   if(value in 1..8)value else 1
  }catch(_:Exception){1}
 }
}
