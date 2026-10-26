// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.text.TextPaint;
import android.view.View;
public abstract class ClickableSpan extends CharacterStyle implements UpdateAppearance {
 public abstract void onClick(View widget);
 public void updateDrawState(TextPaint paint){paint.setColor(paint.linkColor);paint.setUnderlineText(true);}
}
