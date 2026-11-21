// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.usb;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public class UsbAccessory implements Parcelable {
    private final String mManufacturer;
    private final String mModel;
    private final String mDescription;
    private final String mVersion;
    private final String mUri;
    private final String mSerial;

    /** @hide */
    public UsbAccessory(
            String manufacturer,
            String model,
            String description,
            String version,
            String uri,
            String serial) {
        mManufacturer = Objects.requireNonNull(manufacturer, "manufacturer");
        mModel = Objects.requireNonNull(model, "model");
        mDescription = description;
        mVersion = version;
        mUri = uri;
        mSerial = serial;
    }

    public String getManufacturer() {
        return mManufacturer;
    }

    public String getModel() {
        return mModel;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getVersion() {
        return mVersion;
    }

    public String getUri() {
        return mUri;
    }

    public String getSerial() {
        return mSerial;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UsbAccessory)) {
            return false;
        }
        UsbAccessory accessory = (UsbAccessory) obj;
        return Objects.equals(mManufacturer, accessory.mManufacturer)
                && Objects.equals(mModel, accessory.mModel)
                && Objects.equals(mDescription, accessory.mDescription)
                && Objects.equals(mVersion, accessory.mVersion)
                && Objects.equals(mUri, accessory.mUri)
                && Objects.equals(mSerial, accessory.mSerial);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mManufacturer, mModel, mDescription, mVersion, mUri, mSerial);
    }

    @Override
    public String toString() {
        return "UsbAccessory[mManufacturer=" + mManufacturer
                + ", mModel=" + mModel
                + ", mDescription=" + mDescription
                + ", mVersion=" + mVersion
                + ", mUri=" + mUri
                + ", mSerial=" + mSerial + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mManufacturer);
        parcel.writeString(mModel);
        parcel.writeString(mDescription);
        parcel.writeString(mVersion);
        parcel.writeString(mUri);
        parcel.writeString(mSerial);
    }

    public static final Parcelable.Creator<UsbAccessory> CREATOR =
            new Parcelable.Creator<UsbAccessory>() {
                @Override
                public UsbAccessory createFromParcel(Parcel in) {
                    String manufacturer = in.readString();
                    String model = in.readString();
                    String description = in.readString();
                    String version = in.readString();
                    String uri = in.readString();
                    String serial = in.readString();
                    return new UsbAccessory(
                            manufacturer, model, description, version, uri, serial);
                }

                @Override
                public UsbAccessory[] newArray(int size) {
                    return new UsbAccessory[size];
                }
            };
}
