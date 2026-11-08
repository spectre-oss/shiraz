// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
public final class ColorGlyphBoundsSmoke {
 public static void main(String[] args){Paint paint=new Paint();paint.setTextSize(16);paint.setSubpixelText(true);Rect bounds=new Rect();String flags="🇦🇫 🇨🇦 🇺🇸";paint.getTextBounds(flags,0,flags.length(),bounds);if(bounds.width()<=0||bounds.height()<=0)throw new AssertionError("flag glyph bounds");Bitmap bitmap=Bitmap.createBitmap(128,40,Bitmap.Config.ARGB_8888);new Canvas(bitmap).drawText(flags,0,24,paint);int ink=0;for(int y=0;y<40;y++)for(int x=0;x<128;x++)if((bitmap.getPixel(x,y)>>>24)>127)ink++;if(ink<20)throw new AssertionError("real color flag raster");System.out.println("COLOR_GLYPH_BOUNDS_OK");}
}
