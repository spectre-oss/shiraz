// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.text.TextPaint;
/** Marks a run as struck through through the paint state. */
public class StrikethroughSpan extends CharacterStyle implements UpdateAppearance {
 public void updateDrawState(TextPaint paint){paint.setStrikeThruText(true);}
}
