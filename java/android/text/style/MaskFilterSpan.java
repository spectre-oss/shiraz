// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

import android.graphics.MaskFilter;
import android.text.TextPaint;

/** CharacterStyle span applying a MaskFilter (such as BlurMaskFilter) to text drawing. */
public class MaskFilterSpan extends CharacterStyle implements UpdateAppearance {
    private final MaskFilter mFilter;

    public MaskFilterSpan(MaskFilter filter) {
        mFilter = filter;
    }

    public MaskFilter getMaskFilter() {
        return mFilter;
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        ds.setMaskFilter(mFilter);
    }

    @Override
    public String toString() {
        return "MaskFilterSpan{filter=" + getMaskFilter() + '}';
    }
}
