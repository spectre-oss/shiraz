// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TvRecordingInfo implements Parcelable {
    public static final int RECORDING_SCHEDULED = 1;
    public static final int RECORDING_IN_PROGRESS = 2;
    public static final int RECORDING_ALL = 3;

    public static final int SUNDAY = 1;
    public static final int MONDAY = 2;
    public static final int TUESDAY = 4;
    public static final int WEDNESDAY = 8;
    public static final int THURSDAY = 16;
    public static final int FRIDAY = 32;
    public static final int SATURDAY = 64;

    private final String mRecordingId;
    private final long mStartPaddingMillis;
    private final long mEndPaddingMillis;
    private final int mRepeatDays;
    private String mName;
    private String mDescription;
    private final long mScheduledStartTimeMillis;
    private final long mScheduledDurationMillis;
    private final Uri mChannelUri;
    private final Uri mProgramUri;
    private final List<TvContentRating> mContentRatings;
    private final Uri mRecordingUri;
    private final long mRecordingStartTimeMillis;
    private final long mRecordingDurationMillis;

    public TvRecordingInfo(
            String recordingId,
            long startPadding,
            long endPadding,
            int repeatDays,
            String name,
            String description,
            long scheduledStartTime,
            long scheduledDuration,
            Uri channelUri,
            Uri programUri,
            List<TvContentRating> contentRatings,
            Uri recordingUri,
            long recordingStartTime,
            long recordingDuration) {
        mRecordingId = Objects.requireNonNull(recordingId);
        mStartPaddingMillis = startPadding;
        mEndPaddingMillis = endPadding;
        mRepeatDays = repeatDays;
        mName = Objects.requireNonNull(name);
        mDescription = Objects.requireNonNull(description);
        mScheduledStartTimeMillis = scheduledStartTime;
        mScheduledDurationMillis = scheduledDuration;
        mChannelUri = Objects.requireNonNull(channelUri);
        mProgramUri = programUri;
        mContentRatings = contentRatings != null
                ? new ArrayList<TvContentRating>(contentRatings)
                : new ArrayList<TvContentRating>();
        mRecordingUri = recordingUri;
        mRecordingStartTimeMillis = recordingStartTime;
        mRecordingDurationMillis = recordingDuration;
    }

    public String getRecordingId() {
        return mRecordingId;
    }

    public long getStartPaddingMillis() {
        return mStartPaddingMillis;
    }

    public long getEndPaddingMillis() {
        return mEndPaddingMillis;
    }

    public int getRepeatDays() {
        return mRepeatDays;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = Objects.requireNonNull(name);
    }

    public String getDescription() {
        return mDescription;
    }

    public void setDescription(String description) {
        mDescription = Objects.requireNonNull(description);
    }

    public long getScheduledStartTimeMillis() {
        return mScheduledStartTimeMillis;
    }

    public long getScheduledDurationMillis() {
        return mScheduledDurationMillis;
    }

    public Uri getChannelUri() {
        return mChannelUri;
    }

    public Uri getProgramUri() {
        return mProgramUri;
    }

    public List<TvContentRating> getContentRatings() {
        return Collections.unmodifiableList(mContentRatings);
    }

    public Uri getRecordingUri() {
        return mRecordingUri;
    }

    public long getRecordingStartTimeMillis() {
        return mRecordingStartTimeMillis;
    }

    public long getRecordingDurationMillis() {
        return mRecordingDurationMillis;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mRecordingId);
        dest.writeLong(mStartPaddingMillis);
        dest.writeLong(mEndPaddingMillis);
        dest.writeInt(mRepeatDays);
        dest.writeString(mName);
        dest.writeString(mDescription);
        dest.writeLong(mScheduledStartTimeMillis);
        dest.writeLong(mScheduledDurationMillis);
        dest.writeString(mChannelUri != null ? mChannelUri.toString() : null);
        dest.writeString(mProgramUri != null ? mProgramUri.toString() : null);
        dest.writeInt(mContentRatings.size());
        for (int i = 0; i < mContentRatings.size(); i++) {
            dest.writeString(mContentRatings.get(i).flattenToString());
        }
        dest.writeString(mRecordingUri != null ? mRecordingUri.toString() : null);
        dest.writeLong(mRecordingStartTimeMillis);
        dest.writeLong(mRecordingDurationMillis);
    }

    public static final Parcelable.Creator<TvRecordingInfo> CREATOR =
            new Parcelable.Creator<TvRecordingInfo>() {
                @Override
                public TvRecordingInfo createFromParcel(Parcel in) {
                    String recordingId = in.readString();
                    long startPad = in.readLong();
                    long endPad = in.readLong();
                    int repeatDays = in.readInt();
                    String name = in.readString();
                    String desc = in.readString();
                    long schedStart = in.readLong();
                    long schedDur = in.readLong();
                    String chanStr = in.readString();
                    Uri chanUri = chanStr != null ? Uri.parse(chanStr) : null;
                    String progStr = in.readString();
                    Uri progUri = progStr != null ? Uri.parse(progStr) : null;
                    int ratingCount = Math.max(0, in.readInt());
                    List<TvContentRating> ratings = new ArrayList<TvContentRating>(ratingCount);
                    for (int i = 0; i < ratingCount; i++) {
                        String flat = in.readString();
                        if (flat != null) {
                            ratings.add(TvContentRating.unflattenFromString(flat));
                        }
                    }
                    String recStr = in.readString();
                    Uri recUri = recStr != null ? Uri.parse(recStr) : null;
                    long recStart = in.readLong();
                    long recDur = in.readLong();
                    return new TvRecordingInfo(
                            recordingId, startPad, endPad, repeatDays,
                            name, desc, schedStart, schedDur,
                            chanUri, progUri, ratings,
                            recUri, recStart, recDur);
                }

                @Override
                public TvRecordingInfo[] newArray(int size) {
                    return new TvRecordingInfo[size];
                }
            };
}
