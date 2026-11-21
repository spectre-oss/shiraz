// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.ims;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class SipDetails implements Parcelable {
    public static final int METHOD_UNKNOWN = 0;
    public static final int METHOD_REGISTER = 1;
    public static final int METHOD_PUBLISH = 2;
    public static final int METHOD_SUBSCRIBE = 3;

    private final int mMethod;
    private final int mCseq;
    private final int mResponseCode;
    private final String mResponsePhrase;
    private final int mReasonHeaderCause;
    private final String mReasonHeaderText;
    private final String mCallId;

    SipDetails(
            int method,
            int cseq,
            int responseCode,
            String responsePhrase,
            int reasonHeaderCause,
            String reasonHeaderText,
            String callId) {
        mMethod = method;
        mCseq = cseq;
        mResponseCode = responseCode;
        mResponsePhrase = responsePhrase != null ? responsePhrase : "";
        mReasonHeaderCause = reasonHeaderCause;
        mReasonHeaderText = reasonHeaderText != null ? reasonHeaderText : "";
        mCallId = callId;
    }

    private SipDetails(Parcel in) {
        mMethod = in.readInt();
        mCseq = in.readInt();
        mResponseCode = in.readInt();
        mResponsePhrase = in.readString();
        mReasonHeaderCause = in.readInt();
        mReasonHeaderText = in.readString();
        mCallId = in.readString();
    }

    public int getMethod() {
        return mMethod;
    }

    public int getCSeq() {
        return mCseq;
    }

    public int getResponseCode() {
        return mResponseCode;
    }

    public String getResponsePhrase() {
        return mResponsePhrase;
    }

    public int getReasonHeaderCause() {
        return mReasonHeaderCause;
    }

    public String getReasonHeaderText() {
        return mReasonHeaderText;
    }

    public String getCallId() {
        return mCallId;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMethod);
        dest.writeInt(mCseq);
        dest.writeInt(mResponseCode);
        dest.writeString(mResponsePhrase);
        dest.writeInt(mReasonHeaderCause);
        dest.writeString(mReasonHeaderText);
        dest.writeString(mCallId);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SipDetails)) return false;
        SipDetails that = (SipDetails) o;
        return mMethod == that.mMethod
                && mCseq == that.mCseq
                && mResponseCode == that.mResponseCode
                && Objects.equals(mResponsePhrase, that.mResponsePhrase)
                && mReasonHeaderCause == that.mReasonHeaderCause
                && Objects.equals(mReasonHeaderText, that.mReasonHeaderText)
                && Objects.equals(mCallId, that.mCallId);
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mMethod),
                Integer.valueOf(mCseq),
                Integer.valueOf(mResponseCode),
                mResponsePhrase,
                Integer.valueOf(mReasonHeaderCause),
                mReasonHeaderText,
                mCallId);
    }

    public String toString() {
        return "SipDetails { methodType= " + mMethod
                + ", cSeq=" + mCseq
                + ", ResponseCode=" + mResponseCode
                + ", ResponsePhrase=" + mResponsePhrase
                + ", ReasonHeaderCause=" + mReasonHeaderCause
                + ", ReasonHeaderText=" + mReasonHeaderText
                + ", callId=" + mCallId + "}";
    }

    public static final Parcelable.Creator<SipDetails> CREATOR =
            new Parcelable.Creator<SipDetails>() {
                public SipDetails createFromParcel(Parcel source) {
                    return new SipDetails(source);
                }

                public SipDetails[] newArray(int size) {
                    return new SipDetails[size];
                }
            };
}
