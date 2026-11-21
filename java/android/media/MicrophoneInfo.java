// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.util.Pair;
import java.util.Collections;
import java.util.List;

public final class MicrophoneInfo {
    public static final int LOCATION_UNKNOWN = 0;
    public static final int LOCATION_MAINBODY = 1;
    public static final int LOCATION_MAINBODY_MOVABLE = 2;
    public static final int LOCATION_PERIPHERAL = 3;

    public static final int DIRECTIONALITY_UNKNOWN = 0;
    public static final int DIRECTIONALITY_OMNI = 1;
    public static final int DIRECTIONALITY_BI_DIRECTIONAL = 2;
    public static final int DIRECTIONALITY_CARDIOID = 3;
    public static final int DIRECTIONALITY_HYPER_CARDIOID = 4;
    public static final int DIRECTIONALITY_SUPER_CARDIOID = 5;

    public static final int CHANNEL_MAPPING_DIRECT = 1;
    public static final int CHANNEL_MAPPING_PROCESSED = 2;

    public static final int GROUP_UNKNOWN = -1;
    public static final int INDEX_IN_THE_GROUP_UNKNOWN = -1;
    public static final Coordinate3F POSITION_UNKNOWN =
            new Coordinate3F(-3.4028235e38f, -3.4028235e38f, -3.4028235e38f);
    public static final Coordinate3F ORIENTATION_UNKNOWN = new Coordinate3F(0.0f, 0.0f, 0.0f);
    public static final float SENSITIVITY_UNKNOWN = -3.4028235e38f;
    public static final float SPL_UNKNOWN = -3.4028235e38f;

    private final String mDeviceId;
    private final int mId;
    private final int mType;
    private final String mAddress;
    private final int mLocation;
    private final int mGroup;
    private final int mIndexInTheGroup;
    private final Coordinate3F mPosition;
    private final Coordinate3F mOrientation;
    private final List<Pair<Float, Float>> mFrequencyResponse;
    private final List<Pair<Integer, Integer>> mChannelMapping;
    private final float mSensitivity;
    private final float mMaxSpl;
    private final float mMinSpl;
    private final int mDirectionality;

    MicrophoneInfo(
            String deviceId,
            int id,
            int type,
            String address,
            int location,
            int group,
            int indexInTheGroup,
            Coordinate3F position,
            Coordinate3F orientation,
            List<Pair<Float, Float>> frequencyResponse,
            List<Pair<Integer, Integer>> channelMapping,
            float sensitivity,
            float maxSpl,
            float minSpl,
            int directionality) {
        mDeviceId = deviceId != null ? deviceId : "";
        mId = id;
        mType = type;
        mAddress = address != null ? address : "";
        mLocation = location;
        mGroup = group;
        mIndexInTheGroup = indexInTheGroup;
        mPosition = position != null ? position : POSITION_UNKNOWN;
        mOrientation = orientation != null ? orientation : ORIENTATION_UNKNOWN;
        mFrequencyResponse = frequencyResponse != null
                ? frequencyResponse : Collections.<Pair<Float, Float>>emptyList();
        mChannelMapping = channelMapping != null
                ? channelMapping : Collections.<Pair<Integer, Integer>>emptyList();
        mSensitivity = sensitivity;
        mMaxSpl = maxSpl;
        mMinSpl = minSpl;
        mDirectionality = directionality;
    }

    public String getDescription() {
        return mDeviceId;
    }

    public int getId() {
        return mId;
    }

    public int getType() {
        return mType;
    }

    public String getAddress() {
        return mAddress;
    }

    public int getLocation() {
        return mLocation;
    }

    public int getGroup() {
        return mGroup;
    }

    public int getIndexInTheGroup() {
        return mIndexInTheGroup;
    }

    public Coordinate3F getPosition() {
        return mPosition;
    }

    public Coordinate3F getOrientation() {
        return mOrientation;
    }

    public List<Pair<Float, Float>> getFrequencyResponse() {
        return mFrequencyResponse;
    }

    public List<Pair<Integer, Integer>> getChannelMapping() {
        return mChannelMapping;
    }

    public float getSensitivity() {
        return mSensitivity;
    }

    public float getMaxSpl() {
        return mMaxSpl;
    }

    public float getMinSpl() {
        return mMinSpl;
    }

    public int getDirectionality() {
        return mDirectionality;
    }

    public static final class Coordinate3F {
        public final float x;
        public final float y;
        public final float z;

        Coordinate3F(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Coordinate3F)) return false;
            Coordinate3F other = (Coordinate3F) obj;
            return Float.compare(x, other.x) == 0
                    && Float.compare(y, other.y) == 0
                    && Float.compare(z, other.z) == 0;
        }
    }
}
