// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.text.TextPaint;
public class BackgroundColorSpan extends CharacterStyle implements UpdateAppearance {
 private final int color;
 public BackgroundColorSpan(int color){this.color=color;}
 public int getBackgroundColor(){return color;}
 public void updateDrawState(TextPaint paint){paint.bgColor=color;}
}
