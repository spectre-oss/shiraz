// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** Outcome payload delivered to {@link ProfilingManager} listeners. */
public final class ProfilingResult implements Parcelable {
    public static final int ERROR_NONE = 0;
    public static final int ERROR_FAILED_RATE_LIMIT_SYSTEM = 1;
    public static final int ERROR_FAILED_RATE_LIMIT_PROCESS = 2;
    public static final int ERROR_FAILED_PROFILING_IN_PROGRESS = 3;
    public static final int ERROR_FAILED_EXECUTING = 4;
    public static final int ERROR_FAILED_POST_PROCESSING = 5;
    public static final int ERROR_FAILED_NO_DISK_SPACE = 6;
    public static final int ERROR_FAILED_INVALID_REQUEST = 7;
    public static final int ERROR_UNKNOWN = 8;

    public static final Parcelable.Creator<ProfilingResult> CREATOR =
            new Parcelable.Creator<ProfilingResult>() {
                @Override
                public ProfilingResult createFromParcel(Parcel in) {
                    return new ProfilingResult(
                            in.readInt(),
                            in.readString(),
                            in.readString(),
                            in.readString());
                }

                @Override
                public ProfilingResult[] newArray(int size) {
                    return new ProfilingResult[size];
                }
            };

    private final int errorCode;
    private final String resultFilePath;
    private final String tag;
    private final String errorMessage;

    public ProfilingResult(
            int errorCode, String resultFilePath, String tag, String errorMessage) {
        this.errorCode = errorCode;
        this.resultFilePath = resultFilePath;
        this.tag = tag;
        this.errorMessage = errorMessage;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(errorCode);
        dest.writeString(resultFilePath);
        dest.writeString(tag);
        dest.writeString(errorMessage);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getResultFilePath() {
        return resultFilePath;
    }

    public String getTag() {
        return tag;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
