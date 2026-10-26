// SPDX-License-Identifier: GPL-3.0-only
package android.util;
/** Immutable finite floating-point dimensions. */
public final class SizeF {
 private final float width,height;
 public SizeF(float width,float height){if(!Float.isFinite(width)||!Float.isFinite(height))throw new IllegalArgumentException("SizeF dimensions must be finite");this.width=width;this.height=height;}
 public float getWidth(){return width;}public float getHeight(){return height;}
 @Override public boolean equals(Object other){return other instanceof SizeF&&width==((SizeF)other).width&&height==((SizeF)other).height;}
 @Override public int hashCode(){return Float.floatToIntBits(width)^Float.floatToIntBits(height);}
 @Override public String toString(){return width+"x"+height;}
 public static SizeF parseSizeF(String value){if(value==null)throw new NullPointerException("size");int separator=value.indexOf('*');if(separator<0)separator=value.indexOf('x');if(separator>=0)try{return new SizeF(Float.parseFloat(value.substring(0,separator)),Float.parseFloat(value.substring(separator+1)));}catch(IllegalArgumentException invalid){}throw new NumberFormatException("Invalid SizeF: "+value);}
}
