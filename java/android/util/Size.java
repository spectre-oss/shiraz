// SPDX-License-Identifier: GPL-3.0-only
package android.util;
/** Immutable signed integer dimensions. */
public final class Size {
 private final int width,height;
 public Size(int width,int height){this.width=width;this.height=height;}
 public int getWidth(){return width;}public int getHeight(){return height;}
 @Override public boolean equals(Object other){return other instanceof Size&&width==((Size)other).width&&height==((Size)other).height;}
 @Override public int hashCode(){return Integer.rotateLeft(width,16)^height;}
 @Override public String toString(){return width+"x"+height;}
 public static Size parseSize(String value){if(value==null)throw new NullPointerException("size");int separator=value.indexOf('*');if(separator<0)separator=value.indexOf('x');if(separator>=0)try{return new Size(Integer.parseInt(value.substring(0,separator)),Integer.parseInt(value.substring(separator+1)));}catch(NumberFormatException invalid){}throw new NumberFormatException("Invalid Size: "+value);}
}
