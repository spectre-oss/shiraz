// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
/** Bitmap source with independent horizontal and vertical tile modes. */
public final class BitmapShader extends Shader {
 final Bitmap bitmap;final TileMode tileX,tileY;
 public BitmapShader(Bitmap bitmap,TileMode tileX,TileMode tileY){if(bitmap==null||tileX==null||tileY==null)throw new NullPointerException("bitmap/tile mode");bitmap.requireLive();this.bitmap=bitmap;this.tileX=tileX;this.tileY=tileY;}
}
