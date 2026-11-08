package expo.modules.eventcliprender.realmedia
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.nio.ByteBuffer
import java.nio.ByteOrder
class ProductPcmBusTest {
 @Test fun duplicateOverlapAndGapTimelinesReject(){
  validateProductAudioTopology(MoviePlan(listOf(PlannedItem("a",0,1000000),PlannedItem("b",1000000,1000000)),2000000,null,false))
  for(items in listOf(listOf(PlannedItem("a",0,1000000),PlannedItem("a",1000000,1000000)),listOf(PlannedItem("a",0,1000000),PlannedItem("b",500000,1500000)),listOf(PlannedItem("a",0,500000),PlannedItem("b",1000000,1000000))))try{validateProductAudioTopology(MoviePlan(items,2000000,null,false));fail()}catch(_:IllegalArgumentException){}
 }
 @Test fun exactHalfBusesNeverSaturateBeforeSumEvenFullScale(){val a=ProductPcmBus(1000000);val b=ProductPcmBus(1000000);a.put(0,1f,-1f,.5);b.put(0,1f,-1f,.5);assertEquals(32766,a.samples[0]+b.samples[0]);assertEquals(-32766,a.samples[1]+b.samples[1])}
 @Test fun mono44100ToStereo48000ConstantAndChunkBoundaryInvariant(){
  fun convert(chunk:Boolean):ShortArray{val b=ProductPcmBus(1000000);val w=ProductPcmTimeline(b,0,1000000,250000){.5};if(chunk){w.block(250000,44100,1,FloatArray(2205){.8f});w.block(300000,44100,1,FloatArray(2205){.8f})}else w.block(250000,44100,1,FloatArray(4410){.8f});w.end();return b.samples}
  assertArrayEquals(convert(false),convert(true));val x=convert(false);assertEquals(x[0],x[1]);assertTrue(x[9598]>13000);assertEquals(0,x[9600].toInt())
 }
 @Test fun photoAndNoAudioGapsStaySilentAndNonzeroPtsAligns(){val b=ProductPcmBus(3000000);val w=ProductPcmTimeline(b,1000000,1000000,500000){.5};w.block(750000,48000,2,FloatArray(4800*2){.6f});w.end();assertEquals(0,b.samples[2*59999].toInt());assertTrue(b.samples[2*60000]>9000);assertEquals(0,b.samples[2*64800].toInt())}
 @Test fun timestampDiscontinuityCreatesSilenceNotStretchedAudio(){val b=ProductPcmBus(1000000);val w=ProductPcmTimeline(b,0,1000000,0){.5};w.block(0,48000,1,FloatArray(4800){.8f});w.block(200000,48000,1,FloatArray(4800){.8f});w.end();assertTrue(b.samples[2*4799]>13000);assertEquals(0,b.samples[2*7200].toInt());assertTrue(b.samples[2*9600]>13000)}
 @Test fun overlapsAndMidstreamFormatChangeFailRatherThanMute(){val b=ProductPcmBus(1000000);val w=ProductPcmTimeline(b,0,1000000,0){.5};w.block(0,48000,1,FloatArray(100){.1f});try{w.block(0,48000,1,FloatArray(100));fail()}catch(_:IllegalArgumentException){};try{w.block(5000,44100,1,FloatArray(100));fail()}catch(_:IllegalArgumentException){}}
 @Test fun musicGainOnlyAndFinalVoiceUnchangedWithCommonHalf(){val p=ProductAudioPlan(3000000,emptyList(),listOf(MusicMark(500000,1500000)),2000000);val voice=ProductPcmBus(3000000);val music=ProductPcmBus(3000000);for(i in 0 until voice.frames){val t=i*1000000L/48000;voice.put(i,.8f,.8f,.5);music.put(i,.8f,.8f,.5*p.musicGainAt(t))};assertEquals(voice.samples[0],voice.samples[2*120000]);assertTrue(music.samples[2*48000]<music.samples[0]/5);assertTrue(music.samples[2*120000]<music.samples[0])}
 @Test fun wavHeaderAndDurationReadbackRealFilesystem(){val dir=Files.createTempDirectory("pcm-bus-test").toFile();try{val b=ProductPcmBus(1000000);val file=java.io.File(dir,"synthetic.wav");b.writeWav(file);val bytes=file.readBytes();assertEquals(44+48000*4,bytes.size);val h=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);assertEquals(48000,h.getInt(24));assertEquals(2,h.getShort(22).toInt());assertEquals(48000*4,h.getInt(40))}finally{dir.deleteRecursively()}}
}
