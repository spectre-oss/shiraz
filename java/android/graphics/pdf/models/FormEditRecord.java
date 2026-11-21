// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf.models;

import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class FormEditRecord implements Parcelable {
    public static final int EDIT_TYPE_CLICK = 0;
    public static final int EDIT_TYPE_SET_INDICES = 1;
    public static final int EDIT_TYPE_SET_TEXT = 2;

    private final int mPageNumber;
    private final int mWidgetIndex;
    private final int mType;
    private final Point mClickPoint;
    private final int[] mSelectedIndices;
    private final String mText;

    private FormEditRecord(
            int pageNumber,
            int widgetIndex,
            int type,
            Point clickPoint,
            int[] selectedIndices,
            String text) {
        this.mPageNumber = pageNumber;
        this.mWidgetIndex = widgetIndex;
        this.mType = type;
        this.mClickPoint = clickPoint == null ? null : new Point(clickPoint);
        this.mSelectedIndices = selectedIndices == null ? new int[0] : selectedIndices.clone();
        this.mText = text;
    }

    public int getPageNumber() {
        return mPageNumber;
    }

    public int getWidgetIndex() {
        return mWidgetIndex;
    }

    public int getType() {
        return mType;
    }

    public Point getClickPoint() {
        return mClickPoint == null ? null : new Point(mClickPoint);
    }

    public int[] getSelectedIndices() {
        return mSelectedIndices.clone();
    }

    public String getText() {
        return mText;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mPageNumber);
        dest.writeInt(mWidgetIndex);
        dest.writeInt(mType);
        if (mClickPoint == null) {
            dest.writeInt(0);
        } else {
            dest.writeInt(1);
            dest.writeInt(mClickPoint.x);
            dest.writeInt(mClickPoint.y);
        }
        dest.writeIntArray(mSelectedIndices);
        dest.writeString(mText);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof FormEditRecord)) {
            return false;
        }
        FormEditRecord other = (FormEditRecord) obj;
        return mPageNumber == other.mPageNumber
                && mWidgetIndex == other.mWidgetIndex
                && mType == other.mType
                && Objects.equals(mClickPoint, other.mClickPoint)
                && Arrays.equals(mSelectedIndices, other.mSelectedIndices)
                && Objects.equals(mText, other.mText);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                Integer.valueOf(mPageNumber),
                Integer.valueOf(mWidgetIndex),
                Integer.valueOf(mType),
                mClickPoint,
                mText);
        return 31 * result + Arrays.hashCode(mSelectedIndices);
    }

    public static final Parcelable.Creator<FormEditRecord> CREATOR =
            new Parcelable.Creator<FormEditRecord>() {
                @Override
                public FormEditRecord createFromParcel(Parcel in) {
                    int pageNumber = in.readInt();
                    int widgetIndex = in.readInt();
                    int type = in.readInt();
                    Point clickPoint = null;
                    if (in.readInt() != 0) {
                        clickPoint = new Point(in.readInt(), in.readInt());
                    }
                    int[] selectedIndices = in.createIntArray();
                    String text = in.readString();
                    return new FormEditRecord(
                            pageNumber, widgetIndex, type, clickPoint, selectedIndices, text);
                }

                @Override
                public FormEditRecord[] newArray(int size) {
                    return new FormEditRecord[size];
                }
            };

    public static final class Builder {
        private final int mType;
        private final int mPageNumber;
        private final int mWidgetIndex;
        private Point mClickPoint;
        private int[] mSelectedIndices;
        private String mText;

        public Builder(int type, int pageNumber, int widgetIndex) {
            if (type != EDIT_TYPE_CLICK
                    && type != EDIT_TYPE_SET_INDICES
                    && type != EDIT_TYPE_SET_TEXT) {
                throw new IllegalArgumentException("Invalid edit type: " + type);
            }
            if (pageNumber < 0) {
                throw new IllegalArgumentException("Page number cannot be negative");
            }
            if (widgetIndex < 0) {
                throw new IllegalArgumentException("Widget index cannot be negative");
            }
            this.mType = type;
            this.mPageNumber = pageNumber;
            this.mWidgetIndex = widgetIndex;
        }

        public Builder setClickPoint(Point clickPoint) {
            if (mType != EDIT_TYPE_CLICK) {
                throw new IllegalArgumentException(
                        "Cannot set clickPoint on a record of type " + mType);
            }
            this.mClickPoint = clickPoint == null ? null : new Point(clickPoint);
            return this;
        }

        public Builder setSelectedIndices(int[] selectedIndices) {
            if (mType != EDIT_TYPE_SET_INDICES) {
                throw new IllegalArgumentException(
                        "Cannot set selectedIndices on a record of type " + mType);
            }
            this.mSelectedIndices = selectedIndices == null ? null : selectedIndices.clone();
            return this;
        }

        public Builder setText(String text) {
            if (mType != EDIT_TYPE_SET_TEXT) {
                throw new IllegalArgumentException(
                        "Cannot set text on a record of type " + mType);
            }
            this.mText = text;
            return this;
        }

        public FormEditRecord build() {
            if (mType == EDIT_TYPE_CLICK) {
                Objects.requireNonNull(mClickPoint, "Click point cannot be null for click record");
            } else if (mType == EDIT_TYPE_SET_INDICES) {
                Objects.requireNonNull(
                        mSelectedIndices, "Selected indices cannot be null for set indices record");
            } else if (mType == EDIT_TYPE_SET_TEXT) {
                Objects.requireNonNull(mText, "Text cannot be null for set text record");
            }
            return new FormEditRecord(
                    mPageNumber, mWidgetIndex, mType, mClickPoint, mSelectedIndices, mText);
        }
    }
}
