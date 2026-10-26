// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.text.TextPaint;
/** Marks a run as underlined through the paint state. */
public class UnderlineSpan extends CharacterStyle implements UpdateAppearance {
 public void updateDrawState(TextPaint paint){paint.setUnderlineText(true);}
}
