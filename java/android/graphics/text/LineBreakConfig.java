// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.text;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class LineBreakConfig implements Parcelable {
    public static final int LINE_BREAK_STYLE_UNSPECIFIED = -1;
    public static final int LINE_BREAK_STYLE_NONE = 0;
    public static final int LINE_BREAK_STYLE_LOOSE = 1;
    public static final int LINE_BREAK_STYLE_NORMAL = 2;
    public static final int LINE_BREAK_STYLE_STRICT = 3;
    public static final int LINE_BREAK_STYLE_NO_BREAK = 4;
    public static final int LINE_BREAK_STYLE_AUTO = 5;

    public static final int LINE_BREAK_WORD_STYLE_UNSPECIFIED = -1;
    public static final int LINE_BREAK_WORD_STYLE_NONE = 0;
    public static final int LINE_BREAK_WORD_STYLE_PHRASE = 1;
    public static final int LINE_BREAK_WORD_STYLE_AUTO = 2;

    public static final int HYPHENATION_UNSPECIFIED = -1;
    public static final int HYPHENATION_DISABLED = 0;
    public static final int HYPHENATION_ENABLED = 1;

    public static final Parcelable.Creator<LineBreakConfig> CREATOR =
            new Parcelable.Creator<LineBreakConfig>() {
                @Override
                public LineBreakConfig createFromParcel(Parcel in) {
                    int lineBreakStyle = in.readInt();
                    int lineBreakWordStyle = in.readInt();
                    int hyphenation = in.readInt();
                    return new LineBreakConfig(lineBreakStyle, lineBreakWordStyle, hyphenation);
                }

                @Override
                public LineBreakConfig[] newArray(int size) {
                    return new LineBreakConfig[size];
                }
            };

    private final int lineBreakStyle;
    private final int lineBreakWordStyle;
    private final int hyphenation;

    LineBreakConfig(int lineBreakStyle, int lineBreakWordStyle, int hyphenation) {
        this.lineBreakStyle = lineBreakStyle;
        this.lineBreakWordStyle = lineBreakWordStyle;
        this.hyphenation = hyphenation;
    }

    public int getLineBreakStyle() {
        return lineBreakStyle;
    }

    public int getLineBreakWordStyle() {
        return lineBreakWordStyle;
    }

    public int getHyphenation() {
        return hyphenation;
    }

    public LineBreakConfig merge(LineBreakConfig config) {
        if (config == null) {
            throw new NullPointerException("config");
        }
        return new Builder()
                .setLineBreakStyle(lineBreakStyle)
                .setLineBreakWordStyle(lineBreakWordStyle)
                .setHyphenation(hyphenation)
                .merge(config)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LineBreakConfig)) {
            return false;
        }
        LineBreakConfig that = (LineBreakConfig) o;
        return lineBreakStyle == that.lineBreakStyle
                && lineBreakWordStyle == that.lineBreakWordStyle
                && hyphenation == that.hyphenation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(lineBreakStyle),
                Integer.valueOf(lineBreakWordStyle),
                Integer.valueOf(hyphenation));
    }

    @Override
    public String toString() {
        return "LineBreakConfig{mLineBreakStyle="
                + lineBreakStyle
                + ", mLineBreakWordStyle="
                + lineBreakWordStyle
                + ", mHyphenation= "
                + hyphenation
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(lineBreakStyle);
        dest.writeInt(lineBreakWordStyle);
        dest.writeInt(hyphenation);
    }

    public static final class Builder {
        private int lineBreakStyle = LINE_BREAK_STYLE_UNSPECIFIED;
        private int lineBreakWordStyle = LINE_BREAK_WORD_STYLE_UNSPECIFIED;
        private int hyphenation = HYPHENATION_UNSPECIFIED;

        public Builder() {
        }

        public Builder merge(LineBreakConfig config) {
            if (config == null) {
                throw new NullPointerException("config");
            }
            if (config.lineBreakStyle != LINE_BREAK_STYLE_UNSPECIFIED) {
                lineBreakStyle = config.lineBreakStyle;
            }
            if (config.lineBreakWordStyle != LINE_BREAK_WORD_STYLE_UNSPECIFIED) {
                lineBreakWordStyle = config.lineBreakWordStyle;
            }
            if (config.hyphenation != HYPHENATION_UNSPECIFIED) {
                hyphenation = config.hyphenation;
            }
            return this;
        }

        public Builder setLineBreakStyle(int lineBreakStyle) {
            if (lineBreakStyle < LINE_BREAK_STYLE_UNSPECIFIED
                    || lineBreakStyle > LINE_BREAK_STYLE_AUTO) {
                throw new IllegalArgumentException("Invalid lineBreakStyle: " + lineBreakStyle);
            }
            this.lineBreakStyle = lineBreakStyle;
            return this;
        }

        public Builder setLineBreakWordStyle(int lineBreakWordStyle) {
            if (lineBreakWordStyle < LINE_BREAK_WORD_STYLE_UNSPECIFIED
                    || lineBreakWordStyle > LINE_BREAK_WORD_STYLE_AUTO) {
                throw new IllegalArgumentException(
                        "Invalid lineBreakWordStyle: " + lineBreakWordStyle);
            }
            this.lineBreakWordStyle = lineBreakWordStyle;
            return this;
        }

        public Builder setHyphenation(int hyphenation) {
            if (hyphenation < HYPHENATION_UNSPECIFIED || hyphenation > HYPHENATION_ENABLED) {
                throw new IllegalArgumentException("Invalid hyphenation: " + hyphenation);
            }
            this.hyphenation = hyphenation;
            return this;
        }

        public LineBreakConfig build() {
            return new LineBreakConfig(lineBreakStyle, lineBreakWordStyle, hyphenation);
        }
    }
}
