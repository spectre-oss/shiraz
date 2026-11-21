// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf.models.selection;

import android.graphics.pdf.content.PdfPageTextContent;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PageSelection implements Parcelable {
    private final int mPage;
    private final SelectionBoundary mStart;
    private final SelectionBoundary mStop;
    private final List<PdfPageTextContent> mSelectedContents;

    public PageSelection(
            int page,
            SelectionBoundary start,
            SelectionBoundary stop,
            List<PdfPageTextContent> selectedContents) {
        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        this.mPage = page;
        this.mStart = Objects.requireNonNull(start, "Start boundary cannot be null");
        this.mStop = Objects.requireNonNull(stop, "Stop boundary cannot be null");
        Objects.requireNonNull(selectedContents, "Selected contents cannot be null");
        ArrayList<PdfPageTextContent> copy =
                new ArrayList<PdfPageTextContent>(selectedContents.size());
        for (int i = 0; i < selectedContents.size(); i++) {
            copy.add(Objects.requireNonNull(
                    selectedContents.get(i), "Selected content element cannot be null"));
        }
        this.mSelectedContents = Collections.unmodifiableList(copy);
    }

    public int getPage() {
        return mPage;
    }

    public SelectionBoundary getStart() {
        return mStart;
    }

    public SelectionBoundary getStop() {
        return mStop;
    }

    public List<PdfPageTextContent> getSelectedTextContents() {
        return mSelectedContents;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mPage);
        mStart.writeToParcel(dest, flags);
        mStop.writeToParcel(dest, flags);
        dest.writeTypedList(mSelectedContents);
    }

    public static final Parcelable.Creator<PageSelection> CREATOR =
            new Parcelable.Creator<PageSelection>() {
                @Override
                public PageSelection createFromParcel(Parcel in) {
                    int page = in.readInt();
                    SelectionBoundary start = SelectionBoundary.CREATOR.createFromParcel(in);
                    SelectionBoundary stop = SelectionBoundary.CREATOR.createFromParcel(in);
                    ArrayList<PdfPageTextContent> contents =
                            in.createTypedArrayList(PdfPageTextContent.CREATOR);
                    return new PageSelection(page, start, stop, contents);
                }

                @Override
                public PageSelection[] newArray(int size) {
                    return new PageSelection[size];
                }
            };
}
