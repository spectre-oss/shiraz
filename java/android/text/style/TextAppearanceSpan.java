// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.LocaleList;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;

/** MetricAffectingSpan applying family, style, size, color, shadow, and typography attributes. */
public class TextAppearanceSpan extends MetricAffectingSpan implements ParcelableSpan {
    private final String mFamilyName;
    private final int mStyle;
    private final int mTextSize;
    private final ColorStateList mTextColor;
    private final ColorStateList mTextColorLink;
    private final Typeface mTypeface;
    private final int mTextFontWeight;
    private final LocaleList mTextLocales;
    private final float mShadowRadius;
    private final float mShadowDx;
    private final float mShadowDy;
    private final int mShadowColor;
    private final boolean mHasElegantTextHeight;
    private final boolean mElegantTextHeight;
    private final boolean mHasLetterSpacing;
    private final float mLetterSpacing;
    private final String mFontFeatureSettings;
    private final String mFontVariationSettings;

    public TextAppearanceSpan(Context context, int appearance) {
        this(context, appearance, -1);
    }

    public TextAppearanceSpan(Context context, int appearance, int colorList) {
        String family = null;
        int style = 0;
        int textSize = -1;
        ColorStateList textColor = null;
        ColorStateList linkColor = null;
        if (context != null && colorList >= 0) {
            try {
                textColor = context.getColorStateList(colorList);
            } catch (RuntimeException ignored) {
            }
        }
        mFamilyName = family;
        mStyle = style;
        mTextSize = textSize;
        mTextColor = textColor;
        mTextColorLink = linkColor;
        mTypeface = null;
        mTextFontWeight = -1;
        mTextLocales = null;
        mShadowRadius = 0f;
        mShadowDx = 0f;
        mShadowDy = 0f;
        mShadowColor = 0;
        mHasElegantTextHeight = false;
        mElegantTextHeight = false;
        mHasLetterSpacing = false;
        mLetterSpacing = 0f;
        mFontFeatureSettings = null;
        mFontVariationSettings = null;
    }

    public TextAppearanceSpan(
            String family,
            int style,
            int size,
            ColorStateList color,
            ColorStateList linkColor) {
        mFamilyName = family;
        mStyle = style;
        mTextSize = size;
        mTextColor = color;
        mTextColorLink = linkColor;
        mTypeface = null;
        mTextFontWeight = -1;
        mTextLocales = null;
        mShadowRadius = 0f;
        mShadowDx = 0f;
        mShadowDy = 0f;
        mShadowColor = 0;
        mHasElegantTextHeight = false;
        mElegantTextHeight = false;
        mHasLetterSpacing = false;
        mLetterSpacing = 0f;
        mFontFeatureSettings = null;
        mFontVariationSettings = null;
    }

    public TextAppearanceSpan(Parcel src) {
        mFamilyName = src.readString();
        mStyle = src.readInt();
        mTextSize = src.readInt();
        if (src.readInt() != 0) {
            mTextColor = ColorStateList.CREATOR.createFromParcel(src);
        } else {
            mTextColor = null;
        }
        if (src.readInt() != 0) {
            mTextColorLink = ColorStateList.CREATOR.createFromParcel(src);
        } else {
            mTextColorLink = null;
        }
        mTypeface = null;
        mTextFontWeight = src.readInt();
        if (src.readInt() != 0) {
            String tags = src.readString();
            mTextLocales = LocaleList.forLanguageTags(tags);
        } else {
            mTextLocales = null;
        }
        mShadowRadius = src.readFloat();
        mShadowDx = src.readFloat();
        mShadowDy = src.readFloat();
        mShadowColor = src.readInt();
        mHasElegantTextHeight = src.readBoolean();
        mElegantTextHeight = src.readBoolean();
        mHasLetterSpacing = src.readBoolean();
        mLetterSpacing = src.readFloat();
        mFontFeatureSettings = src.readString();
        mFontVariationSettings = src.readString();
    }

    @Override
    public int getSpanTypeId() {
        return 17;
    }

    @Override
    public int getSpanTypeIdInternal() {
        return getSpanTypeId();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mFamilyName);
        dest.writeInt(mStyle);
        dest.writeInt(mTextSize);
        if (mTextColor != null) {
            dest.writeInt(1);
            mTextColor.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        if (mTextColorLink != null) {
            dest.writeInt(1);
            mTextColorLink.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mTextFontWeight);
        if (mTextLocales != null) {
            dest.writeInt(1);
            dest.writeString(mTextLocales.toLanguageTags());
        } else {
            dest.writeInt(0);
        }
        dest.writeFloat(mShadowRadius);
        dest.writeFloat(mShadowDx);
        dest.writeFloat(mShadowDy);
        dest.writeInt(mShadowColor);
        dest.writeBoolean(mHasElegantTextHeight);
        dest.writeBoolean(mElegantTextHeight);
        dest.writeBoolean(mHasLetterSpacing);
        dest.writeFloat(mLetterSpacing);
        dest.writeString(mFontFeatureSettings);
        dest.writeString(mFontVariationSettings);
    }

    @Override
    public void writeToParcelInternal(Parcel dest, int flags) {
        writeToParcel(dest, flags);
    }

    public String getFamily() {
        return mFamilyName;
    }

    public ColorStateList getTextColor() {
        return mTextColor;
    }

    public ColorStateList getLinkTextColor() {
        return mTextColorLink;
    }

    public int getTextSize() {
        return mTextSize;
    }

    public int getTextStyle() {
        return mStyle;
    }

    public int getTextFontWeight() {
        return mTextFontWeight;
    }

    public LocaleList getTextLocales() {
        return mTextLocales;
    }

    public Typeface getTypeface() {
        return mTypeface;
    }

    public int getShadowColor() {
        return mShadowColor;
    }

    public float getShadowDx() {
        return mShadowDx;
    }

    public float getShadowDy() {
        return mShadowDy;
    }

    public float getShadowRadius() {
        return mShadowRadius;
    }

    public String getFontFeatureSettings() {
        return mFontFeatureSettings;
    }

    public String getFontVariationSettings() {
        return mFontVariationSettings;
    }

    public boolean isElegantTextHeight() {
        return mElegantTextHeight;
    }

    public float getLetterSpacing() {
        return mLetterSpacing;
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        updateMeasureState(ds);
        if (mTextColor != null) {
            ds.setColor(mTextColor.getColorForState(ds.drawableState, 0));
        }
        if (mTextColorLink != null) {
            ds.linkColor = mTextColorLink.getColorForState(ds.drawableState, 0);
        }
        if (mShadowColor != 0) {
            ds.setShadowLayer(mShadowRadius, mShadowDx, mShadowDy, mShadowColor);
        }
    }

    @Override
    public void updateMeasureState(TextPaint ds) {
        Typeface styledTypeface;
        int style = 0;
        if (mTypeface != null) {
            style = mStyle;
            styledTypeface = Typeface.create(mTypeface, style);
        } else if (mFamilyName != null || mStyle != 0) {
            Typeface tf = ds.getTypeface();
            if (tf != null) {
                style = tf.getStyle();
            }
            style |= mStyle;
            if (mFamilyName != null) {
                styledTypeface = Typeface.create(mFamilyName, style);
            } else if (tf == null) {
                styledTypeface = Typeface.defaultFromStyle(style);
            } else {
                styledTypeface = Typeface.create(tf, style);
            }
        } else {
            styledTypeface = null;
        }
        if (styledTypeface != null) {
            Typeface readyTypeface;
            if (mTextFontWeight >= 0) {
                int weight = Math.min(1000, mTextFontWeight);
                boolean italic = (style & Typeface.ITALIC) != 0;
                readyTypeface = ds.setTypeface(Typeface.create(styledTypeface, weight, italic));
            } else {
                readyTypeface = styledTypeface;
            }
            int fake = style & ~readyTypeface.getStyle();
            if ((fake & Typeface.BOLD) != 0) {
                ds.setFakeBoldText(true);
            }
            if ((fake & Typeface.ITALIC) != 0) {
                ds.setTextSkewX(-0.25f);
            }
            ds.setTypeface(readyTypeface);
        }
        if (mTextSize > 0) {
            ds.setTextSize(mTextSize);
        }
        if (mTextLocales != null && !mTextLocales.isEmpty()) {
            ds.setTextLocales(mTextLocales);
        }
        if (mHasElegantTextHeight) {
            ds.setElegantTextHeight(mElegantTextHeight);
        }
        if (mHasLetterSpacing) {
            ds.setLetterSpacing(mLetterSpacing);
        }
        if (mFontFeatureSettings != null) {
            ds.setFontFeatureSettings(mFontFeatureSettings);
        }
        if (mFontVariationSettings != null) {
            ds.setFontVariationSettings(mFontVariationSettings);
        }
    }

    @Override
    public String toString() {
        return "TextAppearanceSpan{"
                + "familyName='" + getFamily() + '\''
                + ", style=" + getTextStyle()
                + ", textSize=" + getTextSize()
                + ", textColor=" + getTextColor()
                + ", textColorLink=" + getLinkTextColor()
                + '}';
    }
}
