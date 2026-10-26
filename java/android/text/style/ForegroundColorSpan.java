// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.text.TextPaint;
public class ForegroundColorSpan extends CharacterStyle implements UpdateAppearance {
 private final int color;
 public ForegroundColorSpan(int color){this.color=color;}
 public int getForegroundColor(){return color;}
 public void updateDrawState(TextPaint paint){paint.setColor(color);}
}
