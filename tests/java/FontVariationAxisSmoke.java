// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.fonts.FontVariationAxis;
public final class FontVariationAxisSmoke {
 public static void main(String[] args) {
  if(FontVariationAxis.fromFontVariationSettings(null)!=null||FontVariationAxis.fromFontVariationSettings("")!=null)throw new AssertionError("empty");
  FontVariationAxis[] a=FontVariationAxis.fromFontVariationSettings("'wght' 400, \"wdth\" 87.5");
  if(a.length!=2||!a[0].getTag().equals("wght")||a[0].getStyleValue()!=400||a[1].getStyleValue()!=87.5f)throw new AssertionError("axes");
  for(String bad:new String[]{"wght 400","'abc' 400","'wght' nope","'wght' 400,"}) {
   try{FontVariationAxis.fromFontVariationSettings(bad);throw new AssertionError("accepted "+bad);}catch(IllegalArgumentException expected){}
  }
  System.out.println("FONT_VARIATION_AXIS_OK");
 }
}
