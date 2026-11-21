// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

import android.graphics.text.LineBreakConfig;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.ParcelableSpan;
import java.util.Objects;

/** Span overriding line-break style, word style, or hyphenation for a range of text. */
public final class LineBreakConfigSpan implements ParcelableSpan {
    private static final LineBreakConfig NO_HYPHENATION_CONFIG =
            new LineBreakConfig.Builder()
                    .setHyphenation(LineBreakConfig.HYPHENATION_DISABLED)
                    .build();
    private static final LineBreakConfig NO_BREAK_CONFIG =
            new LineBreakConfig.Builder()
                    .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NO_BREAK)
                    .build();

    public static final Parcelable.Creator<LineBreakConfigSpan> CREATOR =
            new Parcelable.Creator<LineBreakConfigSpan>() {
                @Override
                public LineBreakConfigSpan createFromParcel(Parcel source) {
                    LineBreakConfig lbc = LineBreakConfig.CREATOR.createFromParcel(source);
                    return new LineBreakConfigSpan(lbc);
                }

                @Override
                public LineBreakConfigSpan[] newArray(int size) {
                    return new LineBreakConfigSpan[size];
                }
            };

    private final LineBreakConfig mLineBreakConfig;

    public LineBreakConfigSpan(LineBreakConfig lineBreakConfig) {
        mLineBreakConfig = Objects.requireNonNull(lineBreakConfig, "lineBreakConfig");
    }

    public LineBreakConfig getLineBreakConfig() {
        return mLineBreakConfig;
    }

    public static LineBreakConfigSpan createNoBreakSpan() {
        return new LineBreakConfigSpan(NO_BREAK_CONFIG);
    }

    public static LineBreakConfigSpan createNoHyphenationSpan() {
        return new LineBreakConfigSpan(NO_HYPHENATION_CONFIG);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LineBreakConfigSpan)) return false;
        LineBreakConfigSpan that = (LineBreakConfigSpan) o;
        return Objects.equals(mLineBreakConfig, that.mLineBreakConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLineBreakConfig);
    }

    @Override
    public String toString() {
        return "LineBreakConfigSpan{mLineBreakConfig=" + mLineBreakConfig + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        mLineBreakConfig.writeToParcel(dest, flags);
    }

    @Override
    public void writeToParcelInternal(Parcel dest, int flags) {
        writeToParcel(dest, flags);
    }

    @Override
    public int getSpanTypeId() {
        return 30;
    }

    @Override
    public int getSpanTypeIdInternal() {
        return getSpanTypeId();
    }
}
