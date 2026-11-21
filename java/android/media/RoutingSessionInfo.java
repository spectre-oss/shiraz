// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RoutingSessionInfo implements Parcelable {
    public static final int TRANSFER_REASON_FALLBACK = 0;
    public static final int TRANSFER_REASON_SYSTEM_REQUEST = 1;
    public static final int TRANSFER_REASON_APP = 2;

    private final String mId;
    private final CharSequence mName;
    private final String mClientPackageName;
    private final List<String> mSelectedRoutes;
    private final List<String> mSelectableRoutes;
    private final List<String> mDeselectableRoutes;
    private final List<String> mTransferableRoutes;
    private final int mVolumeHandling;
    private final int mVolumeMax;
    private final int mVolume;
    private final Bundle mControlHints;
    private final int mTransferReason;
    private final UserHandle mTransferInitiatorUserHandle;
    private final String mTransferInitiatorPackageName;

    RoutingSessionInfo(Builder builder) {
        mId = builder.mId;
        mName = builder.mName;
        mClientPackageName = builder.mClientPackageName;
        mSelectedRoutes = Collections.unmodifiableList(
                new ArrayList<String>(builder.mSelectedRoutes));
        mSelectableRoutes = Collections.unmodifiableList(
                new ArrayList<String>(builder.mSelectableRoutes));
        mDeselectableRoutes = Collections.unmodifiableList(
                new ArrayList<String>(builder.mDeselectableRoutes));
        mTransferableRoutes = Collections.unmodifiableList(
                new ArrayList<String>(builder.mTransferableRoutes));
        mVolumeHandling = builder.mVolumeHandling;
        mVolumeMax = builder.mVolumeMax;
        mVolume = builder.mVolume;
        mControlHints = builder.mControlHints != null ? new Bundle(builder.mControlHints) : null;
        mTransferReason = builder.mTransferReason;
        mTransferInitiatorUserHandle = builder.mTransferInitiatorUserHandle;
        mTransferInitiatorPackageName = builder.mTransferInitiatorPackageName;
    }

    private RoutingSessionInfo(Parcel in) {
        mId = in.readString();
        mName = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mClientPackageName = in.readString();
        mSelectedRoutes = readUnmodifiableStringList(in);
        mSelectableRoutes = readUnmodifiableStringList(in);
        mDeselectableRoutes = readUnmodifiableStringList(in);
        mTransferableRoutes = readUnmodifiableStringList(in);
        mVolumeHandling = in.readInt();
        mVolumeMax = in.readInt();
        mVolume = in.readInt();
        mControlHints = in.readBundle();
        mTransferReason = in.readInt();
        mTransferInitiatorUserHandle = null;
        mTransferInitiatorPackageName = in.readString();
    }

    private static List<String> readUnmodifiableStringList(Parcel in) {
        String[] arr = in.createStringArray();
        List<String> list = new ArrayList<String>();
        if (arr != null) {
            Collections.addAll(list, arr);
        }
        return Collections.unmodifiableList(list);
    }

    public String getId() {
        return mId;
    }

    public CharSequence getName() {
        return mName;
    }

    public String getClientPackageName() {
        return mClientPackageName;
    }

    public List<String> getSelectedRoutes() {
        return mSelectedRoutes;
    }

    public List<String> getSelectableRoutes() {
        return mSelectableRoutes;
    }

    public List<String> getDeselectableRoutes() {
        return mDeselectableRoutes;
    }

    public List<String> getTransferableRoutes() {
        return mTransferableRoutes;
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

    public Bundle getControlHints() {
        return mControlHints != null ? new Bundle(mControlHints) : null;
    }

    public int getTransferReason() {
        return mTransferReason;
    }

    String getTransferInitiatorPackageName() {
        return mTransferInitiatorPackageName;
    }

    UserHandle getTransferInitiatorUserHandle() {
        return mTransferInitiatorUserHandle;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        TextUtils.writeToParcel(mName, dest, flags);
        dest.writeString(mClientPackageName);
        dest.writeStringArray(mSelectedRoutes.toArray(new String[0]));
        dest.writeStringArray(mSelectableRoutes.toArray(new String[0]));
        dest.writeStringArray(mDeselectableRoutes.toArray(new String[0]));
        dest.writeStringArray(mTransferableRoutes.toArray(new String[0]));
        dest.writeInt(mVolumeHandling);
        dest.writeInt(mVolumeMax);
        dest.writeInt(mVolume);
        dest.writeBundle(mControlHints);
        dest.writeInt(mTransferReason);
        dest.writeString(mTransferInitiatorPackageName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RoutingSessionInfo)) return false;
        RoutingSessionInfo other = (RoutingSessionInfo) obj;
        return Objects.equals(mId, other.mId)
                && TextUtils.equals(mName, other.mName)
                && Objects.equals(mClientPackageName, other.mClientPackageName)
                && Objects.equals(mSelectedRoutes, other.mSelectedRoutes)
                && Objects.equals(mSelectableRoutes, other.mSelectableRoutes)
                && Objects.equals(mDeselectableRoutes, other.mDeselectableRoutes)
                && Objects.equals(mTransferableRoutes, other.mTransferableRoutes)
                && mVolumeHandling == other.mVolumeHandling
                && mVolumeMax == other.mVolumeMax
                && mVolume == other.mVolume
                && mTransferReason == other.mTransferReason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mName != null ? mName.toString() : null,
                mClientPackageName,
                mSelectedRoutes,
                mSelectableRoutes,
                mDeselectableRoutes,
                mTransferableRoutes,
                Integer.valueOf(mVolumeHandling),
                Integer.valueOf(mVolumeMax),
                Integer.valueOf(mVolume),
                Integer.valueOf(mTransferReason));
    }

    @Override
    public String toString() {
        return "RoutingSessionInfo{id=" + mId + ", clientPackageName=" + mClientPackageName
                + ", selectedRoutes=" + mSelectedRoutes + "}";
    }

    public static final Parcelable.Creator<RoutingSessionInfo> CREATOR =
            new Parcelable.Creator<RoutingSessionInfo>() {
                @Override
                public RoutingSessionInfo createFromParcel(Parcel in) {
                    return new RoutingSessionInfo(in);
                }

                @Override
                public RoutingSessionInfo[] newArray(int size) {
                    return new RoutingSessionInfo[size];
                }
            };

    public static final class Builder {
        final String mId;
        CharSequence mName;
        final String mClientPackageName;
        final List<String> mSelectedRoutes = new ArrayList<String>();
        final List<String> mSelectableRoutes = new ArrayList<String>();
        final List<String> mDeselectableRoutes = new ArrayList<String>();
        final List<String> mTransferableRoutes = new ArrayList<String>();
        int mVolumeHandling = MediaRoute2Info.PLAYBACK_VOLUME_FIXED;
        int mVolumeMax;
        int mVolume;
        Bundle mControlHints;
        int mTransferReason = TRANSFER_REASON_FALLBACK;
        UserHandle mTransferInitiatorUserHandle;
        String mTransferInitiatorPackageName;

        public Builder(String id, String clientPackageName) {
            if (TextUtils.isEmpty(id)) {
                throw new IllegalArgumentException("id must not be empty");
            }
            mId = id;
            mClientPackageName = Objects.requireNonNull(clientPackageName, "clientPackageName");
        }

        public Builder(RoutingSessionInfo sessionInfo) {
            Objects.requireNonNull(sessionInfo, "sessionInfo");
            mId = sessionInfo.mId;
            mName = sessionInfo.mName;
            mClientPackageName = sessionInfo.mClientPackageName;
            mSelectedRoutes.addAll(sessionInfo.mSelectedRoutes);
            mSelectableRoutes.addAll(sessionInfo.mSelectableRoutes);
            mDeselectableRoutes.addAll(sessionInfo.mDeselectableRoutes);
            mTransferableRoutes.addAll(sessionInfo.mTransferableRoutes);
            mVolumeHandling = sessionInfo.mVolumeHandling;
            mVolumeMax = sessionInfo.mVolumeMax;
            mVolume = sessionInfo.mVolume;
            mControlHints = sessionInfo.mControlHints != null
                    ? new Bundle(sessionInfo.mControlHints) : null;
            mTransferReason = sessionInfo.mTransferReason;
            mTransferInitiatorUserHandle = sessionInfo.mTransferInitiatorUserHandle;
            mTransferInitiatorPackageName = sessionInfo.mTransferInitiatorPackageName;
        }

        public Builder setName(CharSequence name) {
            mName = name;
            return this;
        }

        public Builder clearSelectedRoutes() {
            mSelectedRoutes.clear();
            return this;
        }

        public Builder addSelectedRoute(String routeId) {
            if (TextUtils.isEmpty(routeId)) {
                throw new IllegalArgumentException("routeId must not be empty");
            }
            mSelectedRoutes.add(routeId);
            return this;
        }

        public Builder removeSelectedRoute(String routeId) {
            if (TextUtils.isEmpty(routeId)) {
                throw new IllegalArgumentException("routeId must not be empty");
            }
            mSelectedRoutes.remove(routeId);
            return this;
        }

        public Builder clearSelectableRoutes() {
            mSelectableRoutes.clear();
            return this;
        }

        public Builder addSelectableRoute(String routeId) {
            if (TextUtils.isEmpty(routeId)) {
                throw new IllegalArgumentException("routeId must not be empty");
            }
            mSelectableRoutes.add(routeId);
            return this;
        }

        public Builder removeSelectableRoute(String routeId) {
            if (TextUtils.isEmpty(routeId)) {
                throw new IllegalArgumentException("routeId must not be empty");
            }
            mSelectableRoutes.remove(routeId);
            return this;
        }

        public Builder clearDeselectableRoutes() {
            mDeselectableRoutes.clear();
            return this;
        }

        public Builder addDeselectableRoute(String routeId) {
            if (TextUtils.isEmpty(routeId)) {
                throw new IllegalArgumentException("routeId must not be empty");
            }
            mDeselectableRoutes.add(routeId);
            return this;
        }

        public Builder removeDeselectableRoute(String routeId) {
            if (TextUtils.isEmpty(routeId)) {
                throw new IllegalArgumentException("routeId must not be empty");
            }
            mDeselectableRoutes.remove(routeId);
            return this;
        }

        public Builder clearTransferableRoutes() {
            mTransferableRoutes.clear();
            return this;
        }

        public Builder addTransferableRoute(String routeId) {
            if (TextUtils.isEmpty(routeId)) {
                throw new IllegalArgumentException("routeId must not be empty");
            }
            mTransferableRoutes.add(routeId);
            return this;
        }

        public Builder removeTransferableRoute(String routeId) {
            if (TextUtils.isEmpty(routeId)) {
                throw new IllegalArgumentException("routeId must not be empty");
            }
            mTransferableRoutes.remove(routeId);
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

        public Builder setControlHints(Bundle controlHints) {
            mControlHints = controlHints != null ? new Bundle(controlHints) : null;
            return this;
        }

        public Builder setTransferReason(int transferReason) {
            mTransferReason = transferReason;
            return this;
        }

        public Builder setTransferInitiator(
                UserHandle transferInitiatorUserHandle,
                String transferInitiatorPackageName) {
            mTransferInitiatorUserHandle = transferInitiatorUserHandle;
            mTransferInitiatorPackageName = transferInitiatorPackageName;
            return this;
        }

        public RoutingSessionInfo build() {
            if (mSelectedRoutes.isEmpty()) {
                throw new IllegalArgumentException("selectedRoutes must not be empty");
            }
            return new RoutingSessionInfo(this);
        }
    }
}
