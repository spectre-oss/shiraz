// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Color;
import android.graphics.Paint;
public class ColorValueSmoke {
 public static void main(String[] args){
  int[] values={0,0x12345678,0xff000000,0xffffffff,0x80ff0000};
  for(int value:values){Color c=Color.valueOf(value);if(c.toArgb()!=value||c.alpha()!=Color.alpha(value)/255f||c.red()!=Color.red(value)/255f||c.green()!=Color.green(value)/255f||c.blue()!=Color.blue(value)/255f)throw new AssertionError("sRGB roundtrip");if(!c.equals(Color.valueOf(value))||c.hashCode()!=Color.valueOf(value).hashCode())throw new AssertionError("value equality");}
  Color c=new Color();if(c.toArgb()!=Color.BLACK||!c.isSrgb()||c.isWideGamut()||c.getComponentCount()!=4)throw new AssertionError("default");
  float[] out={9,9,9,9,9};if(c.getComponents(out)!=out||out[0]!=0||out[3]!=1||out[4]!=9)throw new AssertionError("components");
  c.getComponents()[0]=1;if(c.red()!=0)throw new AssertionError("mutable color");
  try{c.getComponents(new float[3]);throw new AssertionError("short components");}catch(IllegalArgumentException expected){}
  Paint p=new Paint();p.setFontFeatureSettings(null);p.setFontFeatureSettings("");if(p.getFontFeatureSettings()!=null)throw new AssertionError("default features");
  try{p.setFontFeatureSettings("'liga' 0");throw new AssertionError("unsupported features");}catch(UnsupportedOperationException expected){}
  System.out.println("COLOR_VALUE_AND_DEFAULT_FEATURES_OK");
 }
}
