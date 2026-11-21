// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FileServiceInfo extends ServiceInfo implements Parcelable {
    private final List<FileInfo> files;

    FileServiceInfo(
            Map<Locale, String> newNames,
            String newClassName,
            List<Locale> newLocales,
            String newServiceId,
            Date start,
            Date end,
            List<FileInfo> newFiles) {
        super(newNames, newClassName, newLocales, newServiceId, start, end);
        files = newFiles != null
                ? Collections.unmodifiableList(new ArrayList<FileInfo>(newFiles))
                : Collections.<FileInfo>emptyList();
    }

    FileServiceInfo(Parcel in) {
        super(in);
        List<FileInfo> parsed = in.createTypedArrayList(FileInfo.CREATOR);
        files = parsed != null ? Collections.unmodifiableList(parsed) : Collections.<FileInfo>emptyList();
    }

    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeTypedList(files);
    }

    public int describeContents() {
        return 0;
    }

    public List<FileInfo> getFiles() {
        return files;
    }

    public static final Parcelable.Creator<FileServiceInfo> CREATOR =
            new Parcelable.Creator<FileServiceInfo>() {
                public FileServiceInfo createFromParcel(Parcel source) {
                    return new FileServiceInfo(source);
                }

                public FileServiceInfo[] newArray(int size) {
                    return new FileServiceInfo[size];
                }
            };
}
