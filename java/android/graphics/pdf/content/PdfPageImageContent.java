// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf.content;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class PdfPageImageContent implements Parcelable {
    private final String mAltText;

    public PdfPageImageContent(String altText) {
        this.mAltText = Objects.requireNonNull(altText, "Alt text cannot be null");
    }

    public String getAltText() {
        return mAltText;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeString(mAltText);
    }

    public static final Parcelable.Creator<PdfPageImageContent> CREATOR =
            new Parcelable.Creator<PdfPageImageContent>() {
                @Override
                public PdfPageImageContent createFromParcel(Parcel in) {
                    return new PdfPageImageContent(in.readString());
                }

                @Override
                public PdfPageImageContent[] newArray(int size) {
                    return new PdfPageImageContent[size];
                }
            };
}
