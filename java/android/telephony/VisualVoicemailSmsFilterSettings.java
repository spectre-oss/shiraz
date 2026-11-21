// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class VisualVoicemailSmsFilterSettings implements Parcelable {
    public static final int DESTINATION_PORT_ANY = -1;
    public static final int DESTINATION_PORT_DATA_SMS = -2;

    public final String clientPrefix;
    public final List<String> originatingNumbers;
    public final int destinationPort;

    private VisualVoicemailSmsFilterSettings(Builder builder) {
        clientPrefix = builder.mClientPrefix;
        originatingNumbers = Collections.unmodifiableList(
                new ArrayList<String>(builder.mOriginatingNumbers));
        destinationPort = builder.mDestinationPort;
    }

    private VisualVoicemailSmsFilterSettings(Parcel in) {
        clientPrefix = in.readString();
        int count = in.readInt();
        ArrayList<String> nums = new ArrayList<String>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            nums.add(in.readString());
        }
        originatingNumbers = Collections.unmodifiableList(nums);
        destinationPort = in.readInt();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(clientPrefix);
        dest.writeInt(originatingNumbers.size());
        for (int i = 0; i < originatingNumbers.size(); i++) {
            dest.writeString(originatingNumbers.get(i));
        }
        dest.writeInt(destinationPort);
    }

    public String toString() {
        return "[VisualVoicemailSmsFilterSettings clientPrefix=" + clientPrefix
                + ", originatingNumbers=" + originatingNumbers
                + ", destinationPort=" + destinationPort + "]";
    }

    public static final Parcelable.Creator<VisualVoicemailSmsFilterSettings> CREATOR =
            new Parcelable.Creator<VisualVoicemailSmsFilterSettings>() {
                public VisualVoicemailSmsFilterSettings createFromParcel(Parcel in) {
                    return new VisualVoicemailSmsFilterSettings(in);
                }

                public VisualVoicemailSmsFilterSettings[] newArray(int size) {
                    return new VisualVoicemailSmsFilterSettings[size];
                }
            };

    public static class Builder {
        private String mClientPrefix = "//VVM";
        private List<String> mOriginatingNumbers = Collections.emptyList();
        private int mDestinationPort = DESTINATION_PORT_ANY;

        public Builder() {
        }

        public VisualVoicemailSmsFilterSettings build() {
            return new VisualVoicemailSmsFilterSettings(this);
        }

        public Builder setClientPrefix(String clientPrefix) {
            Objects.requireNonNull(clientPrefix, "Client prefix cannot be null");
            mClientPrefix = clientPrefix;
            return this;
        }

        public Builder setOriginatingNumbers(List<String> originatingNumbers) {
            Objects.requireNonNull(originatingNumbers, "Originating numbers cannot be null");
            mOriginatingNumbers = new ArrayList<String>(originatingNumbers);
            return this;
        }

        public Builder setDestinationPort(int destinationPort) {
            mDestinationPort = destinationPort;
            return this;
        }
    }
}
