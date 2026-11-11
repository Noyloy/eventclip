package expo.modules.eventcliprender.realmedia

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/** Magic-byte media sniff. Independent of URI display name, extension, or getType. */
internal enum class SniffedMedia { JPEG, PNG, WEBP, HEIF, AVIF, MP4, WAV, MP3, UNKNOWN }

internal data class MediaSniff(val kind:SniffedMedia,val pngEightBit:Boolean=true,val jpegHasTrailer:Boolean=false,val brand:String="") {
 val stillImage get()=kind==SniffedMedia.JPEG||kind==SniffedMedia.PNG||kind==SniffedMedia.WEBP||kind==SniffedMedia.HEIF||kind==SniffedMedia.AVIF
}

internal object ProductImageSniff {
 private val HEIF=setOf("heic","heix","heif","heis","heim","hevc","hevx","hevm","mif1","msf1")
 private val AVIF=setOf("avif","avis","avio")
 fun ascii(bytes:ByteArray,off:Int,len:Int):String {
  if(off<0||len<=0||off+len>bytes.size)return ""
  return String(bytes,off,len,StandardCharsets.US_ASCII)
 }
 fun of(file:File):MediaSniff {
  val head=ByteArray(64)
  val n=file.inputStream().use{it.read(head)}
  if(n<=0)return MediaSniff(SniffedMedia.UNKNOWN)
  return of(head.copyOf(n),file)
 }
 fun of(head:ByteArray,file:File?=null):MediaSniff {
  if(head.size>=8 && head[0]==0x89.toByte() && ascii(head,1,3)=="PNG") {
   val eight=head.size>24 && head[24].toInt()==8
   return MediaSniff(SniffedMedia.PNG,pngEightBit=eight)
  }
  if(head.size>=3 && head[0]==0xff.toByte() && head[1]==0xd8.toByte()) {
   return MediaSniff(SniffedMedia.JPEG,jpegHasTrailer=file?.let{jpegHasTrailer(it)}?:false)
  }
  if(head.size>=12 && ascii(head,0,4)=="RIFF" && ascii(head,8,4)=="WEBP") {
   return MediaSniff(SniffedMedia.WEBP)
  }
  if(head.size>=12 && ascii(head,4,4)=="ftyp") {
   val brands=ftypBrands(head)
   val major=brands.firstOrNull()?:""
   return when {
    brands.any{it in AVIF}->MediaSniff(SniffedMedia.AVIF,brand=major)
    brands.any{it in HEIF}->MediaSniff(SniffedMedia.HEIF,brand=major)
    else->MediaSniff(SniffedMedia.MP4,brand=major)
   }
  }
  if(head.size>=12 && ascii(head,0,4)=="RIFF" && ascii(head,8,4)=="WAVE")return MediaSniff(SniffedMedia.WAV)
  if(head.size>=3 && (ascii(head,0,3)=="ID3" || (head[0]==0xff.toByte() && (head[1].toInt() and 0xe0)==0xe0)))return MediaSniff(SniffedMedia.MP3)
  return MediaSniff(SniffedMedia.UNKNOWN)
 }
 fun ftypBrands(head:ByteArray):List<String> {
  if(head.size<16 || ascii(head,4,4)!="ftyp")return emptyList()
  val box=ByteBuffer.wrap(head).order(ByteOrder.BIG_ENDIAN).int
  if(box<16)return emptyList()
  val out=ArrayList<String>()
  var off=8
  val end=minOf(head.size,box,256)
  while(off+4<=end){val b=ascii(head,off,4).trim();if(b.isNotEmpty())out.add(b);off+=4}
  return out
 }
 /** True when a JPEG still is followed by extra payload (typical motion-photo trailer). */
 fun jpegHasTrailer(file:File):Boolean {
  if(file.length()<6)return false
  RandomAccessFile(file,"r").use{raf->
   // Search back from EOF for the last EOI, then look for an embedded ISO-BMFF brand.
   val window=minOf(file.length(),65536L).toInt()
   val buf=ByteArray(window)
   raf.seek(file.length()-window)
   raf.readFully(buf)
   var lastEoi=-1
   for(i in 0 until buf.size-1)if(buf[i]==0xff.toByte() && buf[i+1]==0xd9.toByte())lastEoi=i
   if(lastEoi<0)return false
   val after=buf.size-lastEoi-2
   if(after<=0)return false
   val tail=buf.copyOfRange(lastEoi+2,buf.size)
   if(ascii(tail,0,maxOf(0,minOf(4,tail.size))).isNotEmpty() && tail.size>=8 && (ascii(tail,4,4)=="ftyp" || tail.indexOfSequence("ftyp")>=0))return true
   return after>32
  }
 }
 private fun ByteArray.indexOfSequence(text:String):Int {
  val n=text.encodeToByteArray()
  if(n.size>size)return -1
  for(i in 0..size-n.size){var ok=true;for(j in n.indices)if(this[i+j]!=n[j]){ok=false;break};if(ok)return i}
  return -1
 }
 /** Pre-fix visual path: only PNG/JPEG were stills; any other ftyp went to the video probe. */
 fun legacyVisualWouldUseVideoProbe(head:ByteArray):Boolean {
  val png=head.size>=4 && head[0]==0x89.toByte() && head[1]==0x50.toByte()
  val jpeg=head.size>=2 && head[0]==0xff.toByte() && head[1]==0xd8.toByte()
  if(png||jpeg)return false
  return head.size>=8 && ascii(head,4,4)=="ftyp"
 }
}
