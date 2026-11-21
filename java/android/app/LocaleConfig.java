// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Locale;
import java.util.Objects;

public class LocaleConfig implements Parcelable {
    public static final String TAG_LOCALE_CONFIG = "locale-config";
    public static final String TAG_LOCALE = "locale";

    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_NOT_SPECIFIED = 1;
    public static final int STATUS_PARSING_FAILED = 2;

    private final LocaleList mLocales;
    private final int mStatus;
    private final Locale mDefaultLocale;

    public LocaleConfig(Context context) {
        Objects.requireNonNull(context, "context");
        LocaleManager mgr = null;
        try {
            Object svc = context.getSystemService("locale");
            if (svc instanceof LocaleManager) {
                mgr = (LocaleManager) svc;
            }
        } catch (RuntimeException ignored) {
        }
        LocaleConfig override = mgr != null ? mgr.getOverrideLocaleConfig() : null;
        if (override != null) {
            mLocales = override.mLocales;
            mStatus = override.mStatus;
            mDefaultLocale = override.mDefaultLocale;
        } else {
            mLocales = null;
            mStatus = STATUS_NOT_SPECIFIED;
            mDefaultLocale = null;
        }
    }

    public LocaleConfig(LocaleList locales) {
        mLocales = Objects.requireNonNull(locales, "locales");
        mStatus = STATUS_SUCCESS;
        mDefaultLocale = null;
    }

    private LocaleConfig(Parcel in) {
        mStatus = in.readInt();
        String tags = in.readString();
        mLocales = tags != null ? LocaleList.forLanguageTags(tags) : null;
        String defTag = in.readString();
        mDefaultLocale = defTag != null ? Locale.forLanguageTag(defTag) : null;
    }

    public static LocaleConfig fromContextIgnoringOverride(Context context) {
        Objects.requireNonNull(context, "context");
        LocaleConfig cfg = new LocaleConfig(LocaleList.getEmptyLocaleList());
        return new LocaleConfig(cfg.mStatus == STATUS_SUCCESS ? STATUS_NOT_SPECIFIED : cfg.mStatus, null, null);
    }

    private LocaleConfig(int status, LocaleList locales, Locale defaultLocale) {
        mStatus = status;
        mLocales = locales;
        mDefaultLocale = defaultLocale;
    }

    public LocaleList getSupportedLocales() {
        return mLocales;
    }

    public Locale getDefaultLocale() {
        return mDefaultLocale;
    }

    public int getStatus() {
        return mStatus;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStatus);
        dest.writeString(mLocales != null ? mLocales.toLanguageTags() : null);
        dest.writeString(mDefaultLocale != null ? mDefaultLocale.toLanguageTag() : null);
    }

    public static final Parcelable.Creator<LocaleConfig> CREATOR =
            new Parcelable.Creator<LocaleConfig>() {
                @Override
                public LocaleConfig createFromParcel(Parcel in) {
                    return new LocaleConfig(in);
                }

                @Override
                public LocaleConfig[] newArray(int size) {
                    return new LocaleConfig[size];
                }
            };
}
