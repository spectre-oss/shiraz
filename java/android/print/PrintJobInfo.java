// SPDX-License-Identifier: GPL-3.0-only
package android.print;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class PrintJobInfo implements Parcelable {
    public static final int STATE_CREATED = 1;
    public static final int STATE_QUEUED = 2;
    public static final int STATE_STARTED = 3;
    public static final int STATE_BLOCKED = 4;
    public static final int STATE_COMPLETED = 5;
    public static final int STATE_FAILED = 6;
    public static final int STATE_CANCELED = 7;

    private PrintJobId mId;
    private String mLabel;
    private PrinterId mPrinterId;
    private int mState;
    private long mCreationTime;
    private int mCopies;
    private PageRange[] mPages;
    private PrintAttributes mAttributes;
    private Bundle mAdvancedOptions;
    private String mTag;
    private float mProgress;
    private CharSequence mStatus;

    PrintJobInfo() {
        this.mState = STATE_CREATED;
        this.mCopies = 1;
        this.mCreationTime = System.currentTimeMillis();
        this.mLabel = "";
        this.mAttributes = new PrintAttributes.Builder().build();
    }

    PrintJobInfo(PrintJobInfo other) {
        this.mId = other.mId;
        this.mLabel = other.mLabel;
        this.mPrinterId = other.mPrinterId;
        this.mState = other.mState;
        this.mCreationTime = other.mCreationTime;
        this.mCopies = other.mCopies;
        this.mPages = other.mPages == null ? null : other.mPages.clone();
        this.mAttributes = other.mAttributes == null
                ? new PrintAttributes.Builder().build() : other.mAttributes.copy();
        this.mAdvancedOptions = other.mAdvancedOptions == null
                ? null : new Bundle(other.mAdvancedOptions);
        this.mTag = other.mTag;
        this.mProgress = other.mProgress;
        this.mStatus = other.mStatus;
    }

    private PrintJobInfo(Parcel parcel) {
        this.mId = parcel.readTypedObject(PrintJobId.CREATOR);
        this.mLabel = parcel.readString();
        this.mPrinterId = parcel.readTypedObject(PrinterId.CREATOR);
        this.mState = parcel.readInt();
        this.mCreationTime = parcel.readLong();
        this.mCopies = parcel.readInt();
        this.mPages = parcel.createTypedArray(PageRange.CREATOR);
        this.mAttributes = parcel.readTypedObject(PrintAttributes.CREATOR);
        this.mAdvancedOptions = parcel.readBundle(null);
        this.mTag = parcel.readString();
        this.mProgress = parcel.readFloat();
        String statusStr = parcel.readString();
        this.mStatus = statusStr;
    }

    public PrintJobId getId() {
        return mId;
    }

    void setId(PrintJobId id) {
        this.mId = id;
    }

    public String getLabel() {
        return mLabel;
    }

    void setLabel(String label) {
        this.mLabel = label;
    }

    public PrinterId getPrinterId() {
        return mPrinterId;
    }

    void setPrinterId(PrinterId printerId) {
        this.mPrinterId = printerId;
    }

    public int getState() {
        return mState;
    }

    void setState(int state) {
        this.mState = state;
    }

    public long getCreationTime() {
        return mCreationTime;
    }

    void setCreationTime(long creationTime) {
        this.mCreationTime = creationTime;
    }

    public int getCopies() {
        return mCopies;
    }

    public PageRange[] getPages() {
        return mPages == null ? null : mPages.clone();
    }

    void setPagesInternal(PageRange[] pages) {
        this.mPages = pages == null ? null : pages.clone();
    }

    public PrintAttributes getAttributes() {
        return mAttributes;
    }

    void setAttributesInternal(PrintAttributes attributes) {
        this.mAttributes = attributes;
    }

    void setTag(String tag) {
        this.mTag = tag;
    }

    String getTag() {
        return mTag;
    }

    public boolean hasAdvancedOption(String key) {
        return mAdvancedOptions != null && mAdvancedOptions.containsKey(key);
    }

    public String getAdvancedStringOption(String key) {
        if (mAdvancedOptions != null) {
            return mAdvancedOptions.getString(key);
        }
        return null;
    }

    public int getAdvancedIntOption(String key) {
        if (mAdvancedOptions != null) {
            return mAdvancedOptions.getInt(key);
        }
        return 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        Objects.requireNonNull(parcel, "parcel");
        parcel.writeTypedObject(mId, flags);
        parcel.writeString(mLabel);
        parcel.writeTypedObject(mPrinterId, flags);
        parcel.writeInt(mState);
        parcel.writeLong(mCreationTime);
        parcel.writeInt(mCopies);
        parcel.writeTypedArray(mPages, flags);
        parcel.writeTypedObject(mAttributes, flags);
        parcel.writeBundle(mAdvancedOptions);
        parcel.writeString(mTag);
        parcel.writeFloat(mProgress);
        parcel.writeString(mStatus == null ? null : mStatus.toString());
    }

    @Override
    public String toString() {
        return "PrintJobInfo{label=" + mLabel
                + ", id=" + mId
                + ", state=" + stateToString(mState)
                + ", printer=" + mPrinterId
                + ", creationTime=" + mCreationTime
                + ", copies=" + mCopies
                + ", attributes=" + mAttributes
                + ", pages=" + Arrays.toString(mPages) + "}";
    }

    private static String stateToString(int state) {
        switch (state) {
            case STATE_CREATED:
                return "STATE_CREATED";
            case STATE_QUEUED:
                return "STATE_QUEUED";
            case STATE_STARTED:
                return "STATE_STARTED";
            case STATE_BLOCKED:
                return "STATE_BLOCKED";
            case STATE_COMPLETED:
                return "STATE_COMPLETED";
            case STATE_FAILED:
                return "STATE_FAILED";
            case STATE_CANCELED:
                return "STATE_CANCELED";
            default:
                return "STATE_UNKNOWN";
        }
    }

    public static final Parcelable.Creator<PrintJobInfo> CREATOR =
            new Parcelable.Creator<PrintJobInfo>() {
                @Override
                public PrintJobInfo createFromParcel(Parcel parcel) {
                    return new PrintJobInfo(parcel);
                }

                @Override
                public PrintJobInfo[] newArray(int size) {
                    return new PrintJobInfo[size];
                }
            };

    public static final class Builder {
        private final PrintJobInfo mPrototype;

        public Builder(PrintJobInfo prototype) {
            mPrototype = prototype != null
                    ? new PrintJobInfo(prototype)
                    : new PrintJobInfo();
        }

        public void setCopies(int copies) {
            if (copies < 1) {
                throw new IllegalArgumentException("Copies must be greater than or equal to 1.");
            }
            mPrototype.mCopies = copies;
        }

        public void setAttributes(PrintAttributes attributes) {
            mPrototype.mAttributes = Objects.requireNonNull(attributes, "attributes");
        }

        public void setPages(PageRange[] pages) {
            Objects.requireNonNull(pages, "pages");
            mPrototype.mPages = pages.clone();
        }

        public void putAdvancedOption(String key, String value) {
            Objects.requireNonNull(key, "key cannot be null");
            if (mPrototype.mAdvancedOptions == null) {
                mPrototype.mAdvancedOptions = new Bundle();
            }
            mPrototype.mAdvancedOptions.putString(key, value);
        }

        public void putAdvancedOption(String key, int value) {
            Objects.requireNonNull(key, "key cannot be null");
            if (mPrototype.mAdvancedOptions == null) {
                mPrototype.mAdvancedOptions = new Bundle();
            }
            mPrototype.mAdvancedOptions.putInt(key, value);
        }

        public PrintJobInfo build() {
            return new PrintJobInfo(mPrototype);
        }
    }
}
