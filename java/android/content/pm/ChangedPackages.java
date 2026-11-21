// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Sequence-numbered list of package names modified since a given sequence number. */
public final class ChangedPackages implements Parcelable {
    private final int mSequenceNumber;
    private final List<String> mPackageNames;

    public ChangedPackages(int sequenceNumber, List<String> packageNames) {
        mSequenceNumber = sequenceNumber;
        mPackageNames = Collections.unmodifiableList(
                new ArrayList<String>(Objects.requireNonNull(packageNames)));
    }

    ChangedPackages(Parcel in) {
        mSequenceNumber = in.readInt();
        int n = in.readInt();
        List<String> pkgs = new ArrayList<String>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            pkgs.add(in.readString());
        }
        mPackageNames = Collections.unmodifiableList(pkgs);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSequenceNumber);
        dest.writeInt(mPackageNames.size());
        for (String pkg : mPackageNames) {
            dest.writeString(pkg);
        }
    }

    public int getSequenceNumber() {
        return mSequenceNumber;
    }

    public List<String> getPackageNames() {
        return mPackageNames;
    }

    public static final Creator<ChangedPackages> CREATOR = new Creator<ChangedPackages>() {
        @Override
        public ChangedPackages createFromParcel(Parcel in) {
            return new ChangedPackages(in);
        }

        @Override
        public ChangedPackages[] newArray(int size) {
            return new ChangedPackages[size];
        }
    };
}
