// SPDX-License-Identifier: GPL-3.0-only
package android.print;

import android.app.PendingIntent;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class PrinterInfo implements Parcelable {
    public static final int STATUS_IDLE = 1;
    public static final int STATUS_BUSY = 2;
    public static final int STATUS_UNAVAILABLE = 3;

    private final PrinterId mId;
    private final String mName;
    private final int mStatus;
    private final int mIconResourceId;
    private final boolean mHasCustomPrinterIcon;
    private final String mDescription;
    private final PendingIntent mInfoIntent;
    private final PrinterCapabilitiesInfo mCapabilities;

    private PrinterInfo(
            PrinterId printerId,
            String name,
            int status,
            int iconResourceId,
            boolean hasCustomPrinterIcon,
            String description,
            PendingIntent infoIntent,
            PrinterCapabilitiesInfo capabilities) {
        this.mId = printerId;
        this.mName = name;
        this.mStatus = status;
        this.mIconResourceId = iconResourceId;
        this.mHasCustomPrinterIcon = hasCustomPrinterIcon;
        this.mDescription = description;
        this.mInfoIntent = infoIntent;
        this.mCapabilities = capabilities;
    }

    private PrinterInfo(Parcel parcel) {
        this.mId = parcel.readTypedObject(PrinterId.CREATOR);
        this.mName = parcel.readString();
        this.mStatus = parcel.readInt();
        this.mIconResourceId = parcel.readInt();
        this.mHasCustomPrinterIcon = parcel.readBoolean();
        this.mDescription = parcel.readString();
        this.mInfoIntent = parcel.readInt() != 0
                ? PendingIntent.CREATOR.createFromParcel(parcel) : null;
        this.mCapabilities = parcel.readTypedObject(PrinterCapabilitiesInfo.CREATOR);
    }

    public PrinterId getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public int getStatus() {
        return mStatus;
    }

    public String getDescription() {
        return mDescription;
    }

    public PrinterCapabilitiesInfo getCapabilities() {
        return mCapabilities;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        Objects.requireNonNull(parcel, "parcel");
        parcel.writeTypedObject(mId, flags);
        parcel.writeString(mName);
        parcel.writeInt(mStatus);
        parcel.writeInt(mIconResourceId);
        parcel.writeBoolean(mHasCustomPrinterIcon);
        parcel.writeString(mDescription);
        if (mInfoIntent == null) {
            parcel.writeInt(0);
        } else {
            int pos = parcel.dataPosition();
            int size = parcel.dataSize();
            try {
                parcel.writeInt(1);
                mInfoIntent.writeToParcel(parcel, flags);
            } catch (UnsupportedOperationException localOnly) {
                parcel.setDataPosition(pos);
                parcel.setDataSize(size);
                parcel.writeInt(0);
            }
        }
        parcel.writeTypedObject(mCapabilities, flags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mName,
                Integer.valueOf(mStatus),
                Integer.valueOf(mIconResourceId),
                Boolean.valueOf(mHasCustomPrinterIcon),
                mDescription,
                mInfoIntent,
                mCapabilities);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PrinterInfo other = (PrinterInfo) obj;
        return Objects.equals(mId, other.mId)
                && Objects.equals(mName, other.mName)
                && mStatus == other.mStatus
                && mIconResourceId == other.mIconResourceId
                && mHasCustomPrinterIcon == other.mHasCustomPrinterIcon
                && Objects.equals(mDescription, other.mDescription)
                && Objects.equals(mInfoIntent, other.mInfoIntent)
                && Objects.equals(mCapabilities, other.mCapabilities);
    }

    @Override
    public String toString() {
        return "PrinterInfo{id=" + mId
                + ", name=" + mName
                + ", status=" + mStatus
                + ", description=" + mDescription
                + ", capabilities=" + mCapabilities + "}";
    }

    private static PrinterId checkPrinterId(PrinterId printerId) {
        if (printerId == null) {
            throw new IllegalArgumentException("printerId cannot be null.");
        }
        return printerId;
    }

    private static String checkName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name cannot be empty.");
        }
        return name;
    }

    private static int checkStatus(int status) {
        if (status != STATUS_IDLE && status != STATUS_BUSY && status != STATUS_UNAVAILABLE) {
            throw new IllegalArgumentException("status is invalid.");
        }
        return status;
    }

    public static final Parcelable.Creator<PrinterInfo> CREATOR =
            new Parcelable.Creator<PrinterInfo>() {
                @Override
                public PrinterInfo createFromParcel(Parcel parcel) {
                    return new PrinterInfo(parcel);
                }

                @Override
                public PrinterInfo[] newArray(int size) {
                    return new PrinterInfo[size];
                }
            };

    public static final class Builder {
        private PrinterId mPrinterId;
        private String mName;
        private int mStatus;
        private int mIconResourceId;
        private boolean mHasCustomPrinterIcon;
        private String mDescription;
        private PendingIntent mInfoIntent;
        private PrinterCapabilitiesInfo mCapabilities;

        public Builder(PrinterId printerId, String name, int status) {
            this.mPrinterId = checkPrinterId(printerId);
            this.mName = checkName(name);
            this.mStatus = checkStatus(status);
        }

        public Builder(PrinterInfo other) {
            Objects.requireNonNull(other, "other cannot be null");
            this.mPrinterId = other.mId;
            this.mName = other.mName;
            this.mStatus = other.mStatus;
            this.mIconResourceId = other.mIconResourceId;
            this.mHasCustomPrinterIcon = other.mHasCustomPrinterIcon;
            this.mDescription = other.mDescription;
            this.mInfoIntent = other.mInfoIntent;
            this.mCapabilities = other.mCapabilities;
        }

        public Builder setStatus(int status) {
            this.mStatus = checkStatus(status);
            return this;
        }

        public Builder setIconResourceId(int iconResourceId) {
            if (iconResourceId < 0) {
                throw new IllegalArgumentException("iconResourceId cannot be negative.");
            }
            this.mIconResourceId = iconResourceId;
            return this;
        }

        public Builder setHasCustomPrinterIcon(boolean hasCustomPrinterIcon) {
            this.mHasCustomPrinterIcon = hasCustomPrinterIcon;
            return this;
        }

        public Builder setName(String name) {
            this.mName = checkName(name);
            return this;
        }

        public Builder setDescription(String description) {
            this.mDescription = description;
            return this;
        }

        public Builder setInfoIntent(PendingIntent infoIntent) {
            this.mInfoIntent = infoIntent;
            return this;
        }

        public Builder setCapabilities(PrinterCapabilitiesInfo capabilities) {
            this.mCapabilities = capabilities;
            return this;
        }

        public PrinterInfo build() {
            return new PrinterInfo(
                    mPrinterId,
                    mName,
                    mStatus,
                    mIconResourceId,
                    mHasCustomPrinterIcon,
                    mDescription,
                    mInfoIntent,
                    mCapabilities);
        }
    }
}
