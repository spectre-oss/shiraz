// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf.content;

import android.graphics.RectF;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PdfPageLinkContent implements Parcelable {
    private final List<RectF> mBounds;
    private final Uri mUri;

    public PdfPageLinkContent(List<RectF> bounds, Uri uri) {
        Objects.requireNonNull(bounds, "Bounds cannot be null");
        Objects.requireNonNull(uri, "URI cannot be null");
        ArrayList<RectF> copy = new ArrayList<RectF>(bounds.size());
        for (int i = 0; i < bounds.size(); i++) {
            RectF r = Objects.requireNonNull(bounds.get(i), "Bound element cannot be null");
            copy.add(new RectF(r));
        }
        this.mBounds = Collections.unmodifiableList(copy);
        this.mUri = uri;
    }

    public List<RectF> getBounds() {
        return mBounds;
    }

    public Uri getUri() {
        return mUri;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mBounds.size());
        for (int i = 0; i < mBounds.size(); i++) {
            RectF r = mBounds.get(i);
            dest.writeFloat(r.left);
            dest.writeFloat(r.top);
            dest.writeFloat(r.right);
            dest.writeFloat(r.bottom);
        }
        dest.writeString(mUri.toString());
    }

    public static final Parcelable.Creator<PdfPageLinkContent> CREATOR =
            new Parcelable.Creator<PdfPageLinkContent>() {
                @Override
                public PdfPageLinkContent createFromParcel(Parcel in) {
                    int count = in.readInt();
                    ArrayList<RectF> bounds = new ArrayList<RectF>(Math.max(0, count));
                    for (int i = 0; i < count; i++) {
                        bounds.add(new RectF(
                                in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()));
                    }
                    String uriStr = in.readString();
                    return new PdfPageLinkContent(bounds, Uri.parse(uriStr));
                }

                @Override
                public PdfPageLinkContent[] newArray(int size) {
                    return new PdfPageLinkContent[size];
                }
            };
}
