// SPDX-License-Identifier: GPL-3.0-only
package android.print;

import android.os.Parcel;
import android.os.Parcelable;
import android.print.PrintAttributes.Margins;
import android.print.PrintAttributes.MediaSize;
import android.print.PrintAttributes.Resolution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PrinterCapabilitiesInfo implements Parcelable {
    private static final int PROPERTY_MEDIA_SIZE = 0;
    private static final int PROPERTY_RESOLUTION = 1;
    private static final int PROPERTY_COLOR_MODE = 2;
    private static final int PROPERTY_DUPLEX_MODE = 3;
    private static final int PROPERTY_COUNT = 4;

    private Margins mMinMargins = Margins.NO_MARGINS;
    private final List<MediaSize> mMediaSizes = new ArrayList<MediaSize>();
    private final List<Resolution> mResolutions = new ArrayList<Resolution>();
    private int mColorModes;
    private int mDuplexModes;
    private final int[] mDefaults = new int[PROPERTY_COUNT];

    PrinterCapabilitiesInfo() {
        Arrays.fill(mDefaults, -1);
    }

    private PrinterCapabilitiesInfo(PrinterCapabilitiesInfo prototype) {
        copyFrom(prototype);
    }

    private PrinterCapabilitiesInfo(Parcel parcel) {
        this.mMinMargins = parcel.readInt() == 1 ? Margins.createFromParcel(parcel) : Margins.NO_MARGINS;
        int mediaCount = parcel.readInt();
        for (int i = 0; i < mediaCount; i++) {
            this.mMediaSizes.add(MediaSize.createFromParcel(parcel));
        }
        int resCount = parcel.readInt();
        for (int i = 0; i < resCount; i++) {
            this.mResolutions.add(Resolution.createFromParcel(parcel));
        }
        this.mColorModes = parcel.readInt();
        this.mDuplexModes = parcel.readInt();
        for (int i = 0; i < PROPERTY_COUNT; i++) {
            this.mDefaults[i] = parcel.readInt();
        }
    }

    private void copyFrom(PrinterCapabilitiesInfo other) {
        if (this == other) {
            return;
        }
        this.mMinMargins = other.mMinMargins;
        this.mMediaSizes.clear();
        this.mMediaSizes.addAll(other.mMediaSizes);
        this.mResolutions.clear();
        this.mResolutions.addAll(other.mResolutions);
        this.mColorModes = other.mColorModes;
        this.mDuplexModes = other.mDuplexModes;
        System.arraycopy(other.mDefaults, 0, this.mDefaults, 0, PROPERTY_COUNT);
    }

    public List<MediaSize> getMediaSizes() {
        return Collections.unmodifiableList(mMediaSizes);
    }

    public List<Resolution> getResolutions() {
        return Collections.unmodifiableList(mResolutions);
    }

    public Margins getMinMargins() {
        return mMinMargins;
    }

    public int getColorModes() {
        return mColorModes;
    }

    public int getDuplexModes() {
        return mDuplexModes;
    }

    public PrintAttributes getDefaults() {
        PrintAttributes.Builder builder = new PrintAttributes.Builder();
        builder.setMinMargins(mMinMargins);
        int mediaSizeIndex = mDefaults[PROPERTY_MEDIA_SIZE];
        if (mediaSizeIndex >= 0 && mediaSizeIndex < mMediaSizes.size()) {
            builder.setMediaSize(mMediaSizes.get(mediaSizeIndex));
        }
        int resolutionIndex = mDefaults[PROPERTY_RESOLUTION];
        if (resolutionIndex >= 0 && resolutionIndex < mResolutions.size()) {
            builder.setResolution(mResolutions.get(resolutionIndex));
        }
        int colorMode = mDefaults[PROPERTY_COLOR_MODE];
        if (colorMode > 0) {
            builder.setColorMode(colorMode);
        }
        int duplexMode = mDefaults[PROPERTY_DUPLEX_MODE];
        if (duplexMode > 0) {
            builder.setDuplexMode(duplexMode);
        }
        return builder.build();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        Objects.requireNonNull(parcel, "parcel");
        if (mMinMargins != null) {
            parcel.writeInt(1);
            mMinMargins.writeToParcel(parcel);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeInt(mMediaSizes.size());
        for (int i = 0; i < mMediaSizes.size(); i++) {
            mMediaSizes.get(i).writeToParcel(parcel);
        }
        parcel.writeInt(mResolutions.size());
        for (int i = 0; i < mResolutions.size(); i++) {
            mResolutions.get(i).writeToParcel(parcel);
        }
        parcel.writeInt(mColorModes);
        parcel.writeInt(mDuplexModes);
        for (int i = 0; i < PROPERTY_COUNT; i++) {
            parcel.writeInt(mDefaults[i]);
        }
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + (mMinMargins == null ? 0 : mMinMargins.hashCode());
        result = 31 * result + mMediaSizes.hashCode();
        result = 31 * result + mResolutions.hashCode();
        result = 31 * result + mColorModes;
        result = 31 * result + mDuplexModes;
        result = 31 * result + Arrays.hashCode(mDefaults);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PrinterCapabilitiesInfo other = (PrinterCapabilitiesInfo) obj;
        return Objects.equals(mMinMargins, other.mMinMargins)
                && Objects.equals(mMediaSizes, other.mMediaSizes)
                && Objects.equals(mResolutions, other.mResolutions)
                && mColorModes == other.mColorModes
                && mDuplexModes == other.mDuplexModes
                && Arrays.equals(mDefaults, other.mDefaults);
    }

    @Override
    public String toString() {
        return "PrinterCapabilitiesInfo{minMargins=" + mMinMargins
                + ", mediaSizes=" + mMediaSizes
                + ", resolutions=" + mResolutions
                + ", colorModes=" + mColorModes
                + ", duplexModes=" + mDuplexModes + "}";
    }

    public static final Parcelable.Creator<PrinterCapabilitiesInfo> CREATOR =
            new Parcelable.Creator<PrinterCapabilitiesInfo>() {
                @Override
                public PrinterCapabilitiesInfo createFromParcel(Parcel parcel) {
                    return new PrinterCapabilitiesInfo(parcel);
                }

                @Override
                public PrinterCapabilitiesInfo[] newArray(int size) {
                    return new PrinterCapabilitiesInfo[size];
                }
            };

    public static final class Builder {
        private final PrinterCapabilitiesInfo mPrototype;

        public Builder(PrinterId printerId) {
            if (printerId == null) {
                throw new IllegalArgumentException("printerId cannot be null.");
            }
            mPrototype = new PrinterCapabilitiesInfo();
        }

        public Builder addMediaSize(MediaSize mediaSize, boolean isDefault) {
            Objects.requireNonNull(mediaSize, "mediaSize cannot be null");
            int insertionIndex = mPrototype.mMediaSizes.size();
            mPrototype.mMediaSizes.add(mediaSize);
            if (isDefault) {
                if (mPrototype.mDefaults[PROPERTY_MEDIA_SIZE] != -1) {
                    throw new IllegalArgumentException("Default media size is already set!");
                }
                mPrototype.mDefaults[PROPERTY_MEDIA_SIZE] = insertionIndex;
            }
            return this;
        }

        public Builder addResolution(Resolution resolution, boolean isDefault) {
            Objects.requireNonNull(resolution, "resolution cannot be null");
            int insertionIndex = mPrototype.mResolutions.size();
            mPrototype.mResolutions.add(resolution);
            if (isDefault) {
                if (mPrototype.mDefaults[PROPERTY_RESOLUTION] != -1) {
                    throw new IllegalArgumentException("Default resolution is already set!");
                }
                mPrototype.mDefaults[PROPERTY_RESOLUTION] = insertionIndex;
            }
            return this;
        }

        public Builder setMinMargins(Margins margins) {
            if (margins == null) {
                throw new IllegalArgumentException("margins cannot be null");
            }
            mPrototype.mMinMargins = margins;
            return this;
        }

        public Builder setColorModes(int colorModes, int defaultColorMode) {
            if ((colorModes & ~PrintAttributes.VALID_COLOR_MODES) != 0 || colorModes == 0) {
                throw new IllegalArgumentException("Invalid color modes: " + colorModes);
            }
            PrintAttributes.enforceValidColorMode(defaultColorMode);
            if ((colorModes & defaultColorMode) == 0) {
                throw new IllegalArgumentException(
                        "Default color mode not in color modes.");
            }
            mPrototype.mColorModes = colorModes;
            mPrototype.mDefaults[PROPERTY_COLOR_MODE] = defaultColorMode;
            return this;
        }

        public Builder setDuplexModes(int duplexModes, int defaultDuplexMode) {
            if ((duplexModes & ~PrintAttributes.VALID_DUPLEX_MODES) != 0 || duplexModes == 0) {
                throw new IllegalArgumentException("Invalid duplex modes: " + duplexModes);
            }
            PrintAttributes.enforceValidDuplexMode(defaultDuplexMode);
            if ((duplexModes & defaultDuplexMode) == 0) {
                throw new IllegalArgumentException(
                        "Default duplex mode not in duplex modes.");
            }
            mPrototype.mDuplexModes = duplexModes;
            mPrototype.mDefaults[PROPERTY_DUPLEX_MODE] = defaultDuplexMode;
            return this;
        }

        public PrinterCapabilitiesInfo build() {
            if (mPrototype.mMediaSizes.isEmpty()) {
                throw new IllegalStateException("Cannot build without media sizes.");
            }
            if (mPrototype.mDefaults[PROPERTY_MEDIA_SIZE] == -1) {
                throw new IllegalStateException("Default media size not set.");
            }
            if (mPrototype.mResolutions.isEmpty()) {
                throw new IllegalStateException("Cannot build without resolutions.");
            }
            if (mPrototype.mDefaults[PROPERTY_RESOLUTION] == -1) {
                throw new IllegalStateException("Default resolution not set.");
            }
            if (mPrototype.mColorModes == 0 || mPrototype.mDefaults[PROPERTY_COLOR_MODE] == -1) {
                throw new IllegalStateException("Color modes not set.");
            }
            if (mPrototype.mDuplexModes == 0) {
                mPrototype.mDuplexModes = PrintAttributes.DUPLEX_MODE_NONE;
                mPrototype.mDefaults[PROPERTY_DUPLEX_MODE] = PrintAttributes.DUPLEX_MODE_NONE;
            }
            if (mPrototype.mMinMargins == null) {
                mPrototype.mMinMargins = Margins.NO_MARGINS;
            }
            return new PrinterCapabilitiesInfo(mPrototype);
        }
    }
}
