// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MediaRoute2Info implements Parcelable {
    public static final int CONNECTION_STATE_DISCONNECTED = 0;
    public static final int CONNECTION_STATE_CONNECTING = 1;
    public static final int CONNECTION_STATE_CONNECTED = 2;

    public static final int PLAYBACK_VOLUME_FIXED = 0;
    public static final int PLAYBACK_VOLUME_VARIABLE = 1;

    public static final String FEATURE_LIVE_AUDIO = "android.media.route.feature.LIVE_AUDIO";
    public static final String FEATURE_LIVE_VIDEO = "android.media.route.feature.LIVE_VIDEO";
    public static final String FEATURE_REMOTE_PLAYBACK =
            "android.media.route.feature.REMOTE_PLAYBACK";
    public static final String FEATURE_REMOTE_AUDIO_PLAYBACK =
            "android.media.route.feature.REMOTE_AUDIO_PLAYBACK";
    public static final String FEATURE_REMOTE_VIDEO_PLAYBACK =
            "android.media.route.feature.REMOTE_VIDEO_PLAYBACK";

    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_BUILTIN_SPEAKER = 2;
    public static final int TYPE_WIRED_HEADSET = 3;
    public static final int TYPE_WIRED_HEADPHONES = 4;
    public static final int TYPE_BLUETOOTH_A2DP = 8;
    public static final int TYPE_HDMI = 9;
    public static final int TYPE_HDMI_ARC = 10;
    public static final int TYPE_USB_DEVICE = 11;
    public static final int TYPE_USB_ACCESSORY = 12;
    public static final int TYPE_DOCK = 13;
    public static final int TYPE_USB_HEADSET = 22;
    public static final int TYPE_HEARING_AID = 23;
    public static final int TYPE_BLE_HEADSET = 26;
    public static final int TYPE_HDMI_EARC = 29;
    public static final int TYPE_REMOTE_TV = 1001;
    public static final int TYPE_REMOTE_SPEAKER = 1002;
    public static final int TYPE_REMOTE_AUDIO_VIDEO_RECEIVER = 1003;
    public static final int TYPE_REMOTE_TABLET = 1004;
    public static final int TYPE_REMOTE_TABLET_DOCKED = 1005;
    public static final int TYPE_REMOTE_COMPUTER = 1006;
    public static final int TYPE_REMOTE_GAME_CONSOLE = 1007;
    public static final int TYPE_REMOTE_CAR = 1008;
    public static final int TYPE_REMOTE_SMARTWATCH = 1009;
    public static final int TYPE_REMOTE_SMARTPHONE = 1010;
    public static final int TYPE_GROUP = 2000;

    public static final int SUITABILITY_STATUS_SUITABLE_FOR_DEFAULT_TRANSFER = 0;
    public static final int SUITABILITY_STATUS_SUITABLE_FOR_MANUAL_TRANSFER = 1;
    public static final int SUITABILITY_STATUS_NOT_SUITABLE_FOR_TRANSFER = 2;

    private final String mId;
    private final CharSequence mName;
    private final List<String> mFeatures;
    private final int mType;
    private final boolean mIsSystem;
    private final Uri mIconUri;
    private final CharSequence mDescription;
    private final int mConnectionState;
    private final String mClientPackageName;
    private final int mVolumeHandling;
    private final int mVolumeMax;
    private final int mVolume;
    private final Set<String> mDeduplicationIds;
    private final Bundle mExtras;
    private final int mSuitabilityStatus;

    MediaRoute2Info(Builder builder) {
        mId = builder.mId;
        mName = builder.mName;
        mFeatures = Collections.unmodifiableList(new ArrayList<String>(builder.mFeatures));
        mType = builder.mType;
        mIsSystem = builder.mIsSystem;
        mIconUri = builder.mIconUri;
        mDescription = builder.mDescription;
        mConnectionState = builder.mConnectionState;
        mClientPackageName = builder.mClientPackageName;
        mVolumeHandling = builder.mVolumeHandling;
        mVolumeMax = builder.mVolumeMax;
        mVolume = builder.mVolume;
        mDeduplicationIds = Collections.unmodifiableSet(
                new LinkedHashSet<String>(builder.mDeduplicationIds));
        mExtras = builder.mExtras != null ? new Bundle(builder.mExtras) : null;
        mSuitabilityStatus = builder.mSuitabilityStatus;
    }

    private MediaRoute2Info(Parcel in) {
        mId = in.readString();
        mName = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        String[] featuresArr = in.createStringArray();
        List<String> features = new ArrayList<String>();
        if (featuresArr != null) {
            Collections.addAll(features, featuresArr);
        }
        mFeatures = Collections.unmodifiableList(features);
        mType = in.readInt();
        mIsSystem = in.readInt() != 0;
        mIconUri = in.readInt() != 0 ? Uri.CREATOR.createFromParcel(in) : null;
        mDescription = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mConnectionState = in.readInt();
        mClientPackageName = in.readString();
        mVolumeHandling = in.readInt();
        mVolumeMax = in.readInt();
        mVolume = in.readInt();
        String[] dedupArr = in.createStringArray();
        Set<String> dedup = new LinkedHashSet<String>();
        if (dedupArr != null) {
            Collections.addAll(dedup, dedupArr);
        }
        mDeduplicationIds = Collections.unmodifiableSet(dedup);
        mExtras = in.readBundle();
        mSuitabilityStatus = in.readInt();
    }

    public String getId() {
        return mId;
    }

    public CharSequence getName() {
        return mName;
    }

    public List<String> getFeatures() {
        return mFeatures;
    }

    public int getType() {
        return mType;
    }

    public boolean isSystemRoute() {
        return mIsSystem;
    }

    public Uri getIconUri() {
        return mIconUri;
    }

    public CharSequence getDescription() {
        return mDescription;
    }

    public int getConnectionState() {
        return mConnectionState;
    }

    public String getClientPackageName() {
        return mClientPackageName;
    }

    public int getVolumeHandling() {
        return mVolumeHandling;
    }

    public int getVolumeMax() {
        return mVolumeMax;
    }

    public int getVolume() {
        return mVolume;
    }

    public Set<String> getDeduplicationIds() {
        return mDeduplicationIds;
    }

    public Bundle getExtras() {
        return mExtras != null ? new Bundle(mExtras) : null;
    }

    public int getSuitabilityStatus() {
        return mSuitabilityStatus;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MediaRoute2Info)) return false;
        MediaRoute2Info other = (MediaRoute2Info) obj;
        return Objects.equals(mId, other.mId)
                && TextUtils.equals(mName, other.mName)
                && Objects.equals(mFeatures, other.mFeatures)
                && mType == other.mType
                && mIsSystem == other.mIsSystem
                && Objects.equals(mIconUri, other.mIconUri)
                && TextUtils.equals(mDescription, other.mDescription)
                && mConnectionState == other.mConnectionState
                && Objects.equals(mClientPackageName, other.mClientPackageName)
                && mVolumeHandling == other.mVolumeHandling
                && mVolumeMax == other.mVolumeMax
                && mVolume == other.mVolume
                && Objects.equals(mDeduplicationIds, other.mDeduplicationIds)
                && mSuitabilityStatus == other.mSuitabilityStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mName != null ? mName.toString() : null,
                mFeatures,
                Integer.valueOf(mType),
                Boolean.valueOf(mIsSystem),
                mIconUri,
                mDescription != null ? mDescription.toString() : null,
                Integer.valueOf(mConnectionState),
                mClientPackageName,
                Integer.valueOf(mVolumeHandling),
                Integer.valueOf(mVolumeMax),
                Integer.valueOf(mVolume),
                mDeduplicationIds,
                Integer.valueOf(mSuitabilityStatus));
    }

    @Override
    public String toString() {
        return "MediaRoute2Info{id=" + mId + ", name=" + mName
                + ", features=" + mFeatures + ", type=" + mType + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        TextUtils.writeToParcel(mName, dest, flags);
        dest.writeStringArray(mFeatures.toArray(new String[0]));
        dest.writeInt(mType);
        dest.writeInt(mIsSystem ? 1 : 0);
        if (mIconUri != null) {
            dest.writeInt(1);
            mIconUri.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        TextUtils.writeToParcel(mDescription, dest, flags);
        dest.writeInt(mConnectionState);
        dest.writeString(mClientPackageName);
        dest.writeInt(mVolumeHandling);
        dest.writeInt(mVolumeMax);
        dest.writeInt(mVolume);
        dest.writeStringArray(mDeduplicationIds.toArray(new String[0]));
        dest.writeBundle(mExtras);
        dest.writeInt(mSuitabilityStatus);
    }

    public static final Parcelable.Creator<MediaRoute2Info> CREATOR =
            new Parcelable.Creator<MediaRoute2Info>() {
                @Override
                public MediaRoute2Info createFromParcel(Parcel in) {
                    return new MediaRoute2Info(in);
                }

                @Override
                public MediaRoute2Info[] newArray(int size) {
                    return new MediaRoute2Info[size];
                }
            };

    public static final class Builder {
        final String mId;
        final CharSequence mName;
        final List<String> mFeatures = new ArrayList<String>();
        int mType = TYPE_UNKNOWN;
        boolean mIsSystem;
        Uri mIconUri;
        CharSequence mDescription;
        int mConnectionState = CONNECTION_STATE_DISCONNECTED;
        String mClientPackageName;
        int mVolumeHandling = PLAYBACK_VOLUME_FIXED;
        int mVolumeMax;
        int mVolume;
        Set<String> mDeduplicationIds = new LinkedHashSet<String>();
        Bundle mExtras;
        Set<String> mAllowedPackages = new LinkedHashSet<String>();
        int mSuitabilityStatus = SUITABILITY_STATUS_SUITABLE_FOR_DEFAULT_TRANSFER;

        public Builder(String id, CharSequence name) {
            if (TextUtils.isEmpty(id)) {
                throw new IllegalArgumentException("id must not be empty");
            }
            if (TextUtils.isEmpty(name)) {
                throw new IllegalArgumentException("name must not be empty");
            }
            mId = id;
            mName = name;
        }

        public Builder(MediaRoute2Info routeInfo) {
            Objects.requireNonNull(routeInfo, "routeInfo");
            mId = routeInfo.mId;
            mName = routeInfo.mName;
            mFeatures.addAll(routeInfo.mFeatures);
            mType = routeInfo.mType;
            mIsSystem = routeInfo.mIsSystem;
            mIconUri = routeInfo.mIconUri;
            mDescription = routeInfo.mDescription;
            mConnectionState = routeInfo.mConnectionState;
            mClientPackageName = routeInfo.mClientPackageName;
            mVolumeHandling = routeInfo.mVolumeHandling;
            mVolumeMax = routeInfo.mVolumeMax;
            mVolume = routeInfo.mVolume;
            mDeduplicationIds.addAll(routeInfo.mDeduplicationIds);
            mExtras = routeInfo.mExtras != null ? new Bundle(routeInfo.mExtras) : null;
            mSuitabilityStatus = routeInfo.mSuitabilityStatus;
        }

        Builder setSystemRoute(boolean isSystem) {
            mIsSystem = isSystem;
            return this;
        }

        public Builder addFeature(String feature) {
            if (TextUtils.isEmpty(feature)) {
                throw new IllegalArgumentException("feature must not be null or empty");
            }
            mFeatures.add(feature);
            return this;
        }

        public Builder addFeatures(Collection<String> features) {
            Objects.requireNonNull(features, "features");
            for (String f : features) {
                addFeature(f);
            }
            return this;
        }

        public Builder clearFeatures() {
            mFeatures.clear();
            return this;
        }

        public Builder setType(int type) {
            mType = type;
            return this;
        }

        public Builder setIconUri(Uri iconUri) {
            mIconUri = iconUri;
            return this;
        }

        public Builder setDescription(CharSequence description) {
            mDescription = description;
            return this;
        }

        public Builder setConnectionState(int connectionState) {
            mConnectionState = connectionState;
            return this;
        }

        public Builder setClientPackageName(String packageName) {
            mClientPackageName = packageName;
            return this;
        }

        public Builder setVolumeHandling(int volumeHandling) {
            mVolumeHandling = volumeHandling;
            return this;
        }

        public Builder setVolumeMax(int volumeMax) {
            mVolumeMax = volumeMax;
            return this;
        }

        public Builder setVolume(int volume) {
            mVolume = volume;
            return this;
        }

        public Builder setDeduplicationIds(Set<String> idSet) {
            Objects.requireNonNull(idSet, "idSet");
            mDeduplicationIds = new LinkedHashSet<String>(idSet);
            return this;
        }

        public Builder setExtras(Bundle extras) {
            mExtras = extras != null ? new Bundle(extras) : null;
            return this;
        }

        public Builder setVisibilityPublic() {
            mAllowedPackages.clear();
            return this;
        }

        public Builder setVisibilityRestricted(Set<String> allowedPackages) {
            Objects.requireNonNull(allowedPackages, "allowedPackages");
            mAllowedPackages = new LinkedHashSet<String>(allowedPackages);
            return this;
        }

        public Builder setSuitabilityStatus(int suitabilityStatus) {
            mSuitabilityStatus = suitabilityStatus;
            return this;
        }

        public MediaRoute2Info build() {
            if (mFeatures.isEmpty()) {
                throw new IllegalArgumentException("features must not be empty!");
            }
            return new MediaRoute2Info(this);
        }
    }
}
