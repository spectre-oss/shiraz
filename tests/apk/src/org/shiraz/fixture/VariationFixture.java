// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class VariationFixture {
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 private static long ink(Paint p){Bitmap image=Bitmap.createBitmap(240,80,Bitmap.Config.ARGB_8888);try{new Canvas(image).drawText("Hamburg",2,55,p);long total=0;for(int y=0;y<80;y++)for(int x=0;x<240;x++)total+=image.getPixel(x,y)>>>24;return total;}finally{image.recycle();}}
 public static void check(){
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTypeface(Typeface.create("Adwaita Sans",Typeface.NORMAL));p.setTextSize(40);Paint plain=new Paint(p);float baseline=plain.measureText("Hamburg");long baselineInk=ink(plain);
  check(p.setFontVariationSettings("'wght' 100"),"Variable Adwaita Sans wght axis required for this test");float light=p.measureText("Hamburg");long lightInk=ink(p);check(p.setFontVariationSettings("\"wght\" 900"),"Heavy weight accepted");float heavy=p.measureText("Hamburg");long heavyInk=ink(p);check(heavyInk>lightInk&&Math.abs(heavy-light)>.1f,"Weight changes metrics and real glyph pixels");check(plain.measureText("Hamburg")==baseline&&ink(plain)==baselineInk,"Shared native face resets between paints");
  for(int i=0;i<8;i++){
   check(p.measureText("Hamburg")==heavy&&ink(p)==heavyInk,"Repeated heavy shaping stable");
   p.setTextSize(23);check(p.measureText("Hamburg")>0,"Intervening size shapes");p.setTextSize(40);
   check(p.measureText("Hamburg")==heavy&&ink(p)==heavyInk,"Size restoration retains variation");
   check(plain.measureText("Hamburg")==baseline&&ink(plain)==baselineInk,"Interleaved default shaping stable");
  }
  Paint copy=new Paint(p);p.setFontVariationSettings(null);check(p.getFontVariationSettings()==null&&p.measureText("Hamburg")==baseline,"Null resets variation");check(copy.measureText("Hamburg")==heavy&&ink(copy)==heavyInk,"Copied paint retains variation");check(!copy.setFontVariationSettings("'ZZZZ' 3")&&copy.measureText("Hamburg")==heavy,"Unsupported axes preserve prior settings");
  check(copy.setFontVariationSettings("'ZZZZ' 3, 'wght' 100"),"Supported subset of requested axes");check(copy.measureText("Hamburg")==light,"Supported axis applied");
  String old=copy.getFontVariationSettings();for(String invalid:new String[]{"wght 400","'abc' 3","'wght' NaN","'wght' 1e100","'wght' 3,"}){try{copy.setFontVariationSettings(invalid);throw new AssertionError("Invalid setting accepted: "+invalid);}catch(IllegalArgumentException expected){}}check(old.equals(copy.getFontVariationSettings()),"Malformed settings atomic");
  check(copy.setFontVariationSettings("")&&copy.getFontVariationSettings()==null,"Empty resets");check(Float.isFinite(copy.getFontMetrics().ascent),"Metrics with default coordinates");copy.reset();check(copy.getFontVariationSettings()==null,"Paint reset clears settings");
  System.out.println("VARIATION_METRICS_PIXELS_OK");
 }
}
