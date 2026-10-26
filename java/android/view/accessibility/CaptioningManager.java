// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;
/** API type for optional caption settings; this runtime has no captioning service. */
public final class CaptioningManager {
 public boolean isEnabled(){return false;}public java.util.Locale getLocale(){return null;}public float getFontScale(){return 1;}
 public CaptionStyle getUserStyle(){return CaptionStyle.DEFAULT;}
 public static final class CaptionStyle {
  public static final CaptionStyle DEFAULT=new CaptionStyle();
  public final int foregroundColor=0xffffffff,backgroundColor=0xff000000,windowColor=0,edgeType=0,edgeColor=0xff000000;
  public android.graphics.Typeface getTypeface(){return null;}
  public boolean hasForegroundColor(){return true;}public boolean hasBackgroundColor(){return true;}public boolean hasWindowColor(){return false;}public boolean hasEdgeType(){return true;}public boolean hasEdgeColor(){return true;}
 }
}
