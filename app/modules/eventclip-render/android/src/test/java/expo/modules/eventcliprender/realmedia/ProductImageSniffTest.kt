package expo.modules.eventcliprender.realmedia

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** JVM, no Robolectric: picker URIs often have no extension, so type comes from bytes. */
class ProductImageSniffTest {
 private fun bytes(name:String)=requireNotNull(javaClass.classLoader!!.getResourceAsStream("product-import/$name")){name}.use{it.readBytes()}
 /** 32-byte ISO-BMFF ftyp (isom/mp42). Built in-test so a raw .mp4 fixture is not published. */
 private fun mp4Ftyp():ByteArray {
  val b=ByteArray(32)
  b[3]=24; "ftyp".encodeToByteArray().copyInto(b,4); "isom".encodeToByteArray().copyInto(b,8); "isom".encodeToByteArray().copyInto(b,16); "mp42".encodeToByteArray().copyInto(b,20)
  return b
 }
 private fun temp(name:String,payload:ByteArray):File {
  val f=Files.createTempFile("sniff-",name).toFile();f.writeBytes(payload);f.deleteOnExit();return f
 }
 @Test fun jpegPngWebpAreStillsIndependentOfName(){
  assertEquals(SniffedMedia.JPEG,ProductImageSniff.of(bytes("sdr-48x32.jpg")).kind)
  assertEquals(SniffedMedia.JPEG,ProductImageSniff.of(bytes("exif-orient6-48x32.jpg")).kind)
  assertEquals(SniffedMedia.PNG,ProductImageSniff.of(bytes("sdr-48x32.png")).kind)
  assertTrue(ProductImageSniff.of(bytes("sdr-48x32.png")).pngEightBit)
  assertEquals(SniffedMedia.WEBP,ProductImageSniff.of(bytes("sdr-48x32.webp")).kind)
  assertTrue(ProductImageSniff.of(bytes("sdr-48x32.jpg")).stillImage)
  assertFalse(ProductImageSniff.legacyVisualWouldUseVideoProbe(bytes("sdr-48x32.jpg")))
  // Pre-fix WebP was not a still and had no ftyp, so it was rejected as UNSUPPORTED_MEDIA before any probe.
  assertFalse(ProductImageSniff.legacyVisualWouldUseVideoProbe(bytes("sdr-48x32.webp")))
  assertTrue(ProductImageSniff.of(bytes("sdr-48x32.webp")).stillImage)
 }
 @Test fun motionPhotoJpegIsStillWithTrailer(){
  val payload=bytes("motion-photo-style.jpg")
  val sniff=ProductImageSniff.of(temp("motion.jpg",payload))
  assertEquals(SniffedMedia.JPEG,sniff.kind)
  assertTrue(sniff.jpegHasTrailer)
  assertTrue(sniff.stillImage)
  assertFalse(ProductImageSniff.legacyVisualWouldUseVideoProbe(payload))
  assertFalse(ProductImageSniff.of(temp("plain.jpg",bytes("sdr-48x32.jpg"))).jpegHasTrailer)
 }
 @Test fun heifAvifFtypIsImageNotVideo_oldPathUsedVideoProbe(){
  val heic=bytes("heic-ftyp-only.heic")
  val avif=bytes("avif-ftyp-only.avif")
  val mp4=mp4Ftyp()
  assertTrue("old visual path treated any ftyp, including HEIC, as a video",ProductImageSniff.legacyVisualWouldUseVideoProbe(heic))
  assertTrue(ProductImageSniff.legacyVisualWouldUseVideoProbe(avif))
  assertTrue(ProductImageSniff.legacyVisualWouldUseVideoProbe(mp4))
  val heif=ProductImageSniff.of(heic)
  val av=ProductImageSniff.of(avif)
  val video=ProductImageSniff.of(mp4)
  assertEquals(SniffedMedia.HEIF,heif.kind)
  assertEquals("heic",heif.brand)
  assertTrue(heif.stillImage)
  assertEquals(SniffedMedia.AVIF,av.kind)
  assertTrue(av.stillImage)
  assertEquals(SniffedMedia.MP4,video.kind)
  assertEquals("isom",video.brand)
  assertFalse(video.stillImage)
 }
 @Test fun authorityAllowlistAcceptsPhotoPickerAndMediaAndRejectsForeign(){
  FoundationPolicy.validateLocalProvider("content","media")
  FoundationPolicy.validateLocalProvider("content","com.android.providers.media.photopicker")
  FoundationPolicy.validateLocalProvider("content","com.android.providers.media.documents")
  try{FoundationPolicy.validateLocalProvider("content","com.eventclip.spike.test.picker");fail()}catch(e:FoundationFailure){assertEquals(ImportCode.ACCESS_UNREADABLE,e.code)}
  try{FoundationPolicy.validateLocalProvider("file",null);fail()}catch(e:FoundationFailure){assertEquals(ImportCode.ACCESS_UNREADABLE,e.code)}
  try{FoundationPolicy.validateLocalProvider("content","com.google.android.apps.photos.contentprovider");fail()}catch(e:FoundationFailure){assertEquals(ImportCode.ACCESS_UNREADABLE,e.code)}
 }
}
