// SPDX-License-Identifier: GPL-3.0-only
package android.media.projection;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/**
 * Customize the {@link MediaProjection} session requested from {@link MediaProjectionManager}.
 */
public final class MediaProjectionConfig implements Parcelable {
    public static final int CAPTURE_REGION_USER_CHOICE = 0;
    public static final int CAPTURE_REGION_FIXED_DISPLAY = 1;

    private final int mRegionToCapture;

    private MediaProjectionConfig(int captureRegion) {
        mRegionToCapture = captureRegion;
    }

    MediaProjectionConfig(Parcel in) {
        mRegionToCapture = in.readInt();
    }

    public static MediaProjectionConfig createConfigForDefaultDisplay() {
        return new MediaProjectionConfig(CAPTURE_REGION_FIXED_DISPLAY);
    }

    public static MediaProjectionConfig createConfigForUserChoice() {
        return new MediaProjectionConfig(CAPTURE_REGION_USER_CHOICE);
    }

    @Override
    public String toString() {
        return "MediaProjectionConfig{mRegionToCapture=" + mRegionToCapture + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaProjectionConfig)) return false;
        MediaProjectionConfig other = (MediaProjectionConfig) o;
        return mRegionToCapture == other.mRegionToCapture;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mRegionToCapture);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRegionToCapture);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<MediaProjectionConfig> CREATOR =
            new Parcelable.Creator<MediaProjectionConfig>() {
                @Override
                public MediaProjectionConfig createFromParcel(Parcel in) {
                    return new MediaProjectionConfig(in);
                }

                @Override
                public MediaProjectionConfig[] newArray(int size) {
                    return new MediaProjectionConfig[size];
                }
            };
}
