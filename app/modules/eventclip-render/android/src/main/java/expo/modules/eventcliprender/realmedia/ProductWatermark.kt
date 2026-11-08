package expo.modules.eventcliprender.realmedia
import android.graphics.*
import kotlin.math.roundToInt
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings

/** One bounded bitmap per configured final output size, after item rotation and entire-frame fit. */
class ProductWatermark:BitmapOverlay() {
 private var width=720;private var height=1280;private var cached:Bitmap?=null
 private var settings=OverlaySettings.Builder().build()
 override fun configure(size:Size){super.configure(size);if(width!=size.width||height!=size.height){cached?.recycle();cached=null};width=size.width;height=size.height}
 override fun getBitmap(presentationTimeUs:Long):Bitmap {cached?.let{return it}
  val text="Made with EventClip";val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);textSize=(.025f*minOf(width,height)).roundToInt().toFloat()}
  val bounds=Rect();p.getTextBounds(text,0,text.length,bounds);val b=ProductWatermarkGeometry.box(width,height,bounds.width(),bounds.height())
  val bitmap=Bitmap.createBitmap(b.width,b.height,Bitmap.Config.ARGB_8888);val c=Canvas(bitmap)
  c.drawRoundRect(0f,0f,b.width.toFloat(),b.height.toFloat(),b.radius.toFloat(),b.radius.toFloat(),Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(140,0,0,0)})
  c.drawText(text,(b.padX-bounds.left).toFloat(),(b.padY-bounds.top).toFloat(),p)
  // GL Y is UP. Match physical backing bottom-right to inset from final frame, not text/RTL.
  settings=OverlaySettings.Builder().setOverlayFrameAnchor(1f,-1f).setBackgroundFrameAnchor(1f-2f*b.inset/width,-1f+2f*b.inset/height).build()
  cached=bitmap;return bitmap
 }
 override fun getOverlaySettings(presentationTimeUs:Long):OverlaySettings {getBitmap(presentationTimeUs);return settings}
 override fun release(){super.release();cached?.recycle();cached=null}
}
