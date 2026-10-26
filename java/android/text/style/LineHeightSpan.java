// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.graphics.Paint;
import android.text.TextPaint;
/** Callback contract for adjusting a line's font metrics. */
public interface LineHeightSpan extends ParagraphStyle, WrapTogetherSpan {
    void chooseHeight(CharSequence text,int start,int end,int spanStartY,int lineY,Paint.FontMetricsInt metrics);
    interface WithDensity extends LineHeightSpan {
        void chooseHeight(CharSequence text,int start,int end,int spanStartY,int lineY,Paint.FontMetricsInt metrics,TextPaint paint);
    }
}
