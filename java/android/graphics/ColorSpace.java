// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** Color metadata for Shiraz's sRGB software raster. Other spaces fail explicitly. */
public abstract class ColorSpace {
 public enum Named {SRGB,LINEAR_SRGB,EXTENDED_SRGB,LINEAR_EXTENDED_SRGB,BT709,BT2020,DCI_P3,DISPLAY_P3,NTSC_1953,SMPTE_C,ADOBE_RGB,PRO_PHOTO_RGB,ACES,ACESCG,CIE_XYZ,CIE_LAB}
 public enum Model {RGB,XYZ,LAB,CMYK}
 private static final Rgb SRGB=new Rgb();
 private ColorSpace(){}
 public static ColorSpace get(Named name){if(name==null)throw new NullPointerException("name");if(name!=Named.SRGB)throw new UnsupportedOperationException("Software raster supports only sRGB: "+name);return SRGB;}
 public String getName(){return "sRGB IEC61966-2.1";}
 public int getId(){return 0;}
 public Model getModel(){return Model.RGB;}
 public int getComponentCount(){return 3;}
 public boolean isSrgb(){return true;}
 public boolean isWideGamut(){return false;}
 public float getMinValue(int component){component(component);return 0;}
 public float getMaxValue(int component){component(component);return 1;}
 private static void component(int value){if(value<0||value>=3)throw new IllegalArgumentException("RGB component");}
 public static final class Rgb extends ColorSpace {private Rgb(){}}
}
