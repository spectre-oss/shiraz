// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf.models;

import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class FormWidgetInfo implements Parcelable {
    public static final int WIDGET_TYPE_UNKNOWN = 0;
    public static final int WIDGET_TYPE_PUSHBUTTON = 1;
    public static final int WIDGET_TYPE_CHECKBOX = 2;
    public static final int WIDGET_TYPE_RADIOBUTTON = 3;
    public static final int WIDGET_TYPE_COMBOBOX = 4;
    public static final int WIDGET_TYPE_LISTBOX = 5;
    public static final int WIDGET_TYPE_TEXTFIELD = 6;
    public static final int WIDGET_TYPE_SIGNATURE = 7;

    private final int mWidgetType;
    private final int mWidgetIndex;
    private final Rect mWidgetRect;
    private final boolean mReadOnly;
    private final String mTextValue;
    private final String mAccessibilityLabel;
    private final boolean mEditableText;
    private final boolean mMultiSelect;
    private final boolean mMultiLineText;
    private final int mMaxLength;
    private final float mFontSize;
    private final List<ListItem> mListItems;

    private FormWidgetInfo(
            int widgetType,
            int widgetIndex,
            Rect widgetRect,
            boolean readOnly,
            String textValue,
            String accessibilityLabel,
            boolean editableText,
            boolean multiSelect,
            boolean multiLineText,
            int maxLength,
            float fontSize,
            List<ListItem> listItems) {
        this.mWidgetType = widgetType;
        this.mWidgetIndex = widgetIndex;
        this.mWidgetRect = new Rect(widgetRect);
        this.mReadOnly = readOnly;
        this.mTextValue = textValue;
        this.mAccessibilityLabel = accessibilityLabel;
        this.mEditableText = editableText;
        this.mMultiSelect = multiSelect;
        this.mMultiLineText = multiLineText;
        this.mMaxLength = maxLength;
        this.mFontSize = fontSize;
        this.mListItems = listItems == null
                ? Collections.<ListItem>emptyList()
                : Collections.unmodifiableList(new ArrayList<ListItem>(listItems));
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    public int getWidgetIndex() {
        return mWidgetIndex;
    }

    public Rect getWidgetRect() {
        return new Rect(mWidgetRect);
    }

    public boolean isReadOnly() {
        return mReadOnly;
    }

    public String getTextValue() {
        return mTextValue;
    }

    public String getAccessibilityLabel() {
        return mAccessibilityLabel;
    }

    public boolean isEditableText() {
        return mEditableText;
    }

    public boolean isMultiSelect() {
        return mMultiSelect;
    }

    public boolean isMultiLineText() {
        return mMultiLineText;
    }

    public int getMaxLength() {
        return mMaxLength;
    }

    public float getFontSize() {
        return mFontSize;
    }

    public List<ListItem> getListItems() {
        return mListItems;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mWidgetType),
                Integer.valueOf(mWidgetIndex),
                mWidgetRect,
                Boolean.valueOf(mReadOnly),
                mTextValue,
                mAccessibilityLabel,
                Boolean.valueOf(mEditableText),
                Boolean.valueOf(mMultiSelect),
                Boolean.valueOf(mMultiLineText),
                Integer.valueOf(mMaxLength),
                Float.valueOf(mFontSize),
                mListItems);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof FormWidgetInfo)) {
            return false;
        }
        FormWidgetInfo other = (FormWidgetInfo) obj;
        return mWidgetType == other.mWidgetType
                && mWidgetIndex == other.mWidgetIndex
                && Objects.equals(mWidgetRect, other.mWidgetRect)
                && mReadOnly == other.mReadOnly
                && Objects.equals(mTextValue, other.mTextValue)
                && Objects.equals(mAccessibilityLabel, other.mAccessibilityLabel)
                && mEditableText == other.mEditableText
                && mMultiSelect == other.mMultiSelect
                && mMultiLineText == other.mMultiLineText
                && mMaxLength == other.mMaxLength
                && Float.compare(mFontSize, other.mFontSize) == 0
                && Objects.equals(mListItems, other.mListItems);
    }

    @Override
    public String toString() {
        return "FormWidgetInfo{widgetType=" + mWidgetType
                + ", widgetIndex=" + mWidgetIndex
                + ", widgetRect=" + mWidgetRect
                + ", readOnly=" + mReadOnly
                + ", textValue=" + mTextValue
                + ", accessibilityLabel=" + mAccessibilityLabel
                + ", editableText=" + mEditableText
                + ", multiSelect=" + mMultiSelect
                + ", multiLineText=" + mMultiLineText
                + ", maxLength=" + mMaxLength
                + ", fontSize=" + mFontSize
                + ", listItems=" + mListItems + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mWidgetType);
        dest.writeInt(mWidgetIndex);
        dest.writeInt(mWidgetRect.left);
        dest.writeInt(mWidgetRect.top);
        dest.writeInt(mWidgetRect.right);
        dest.writeInt(mWidgetRect.bottom);
        dest.writeBoolean(mReadOnly);
        dest.writeString(mTextValue);
        dest.writeString(mAccessibilityLabel);
        dest.writeBoolean(mEditableText);
        dest.writeBoolean(mMultiSelect);
        dest.writeBoolean(mMultiLineText);
        dest.writeInt(mMaxLength);
        dest.writeFloat(mFontSize);
        dest.writeTypedList(mListItems);
    }

    public static final Parcelable.Creator<FormWidgetInfo> CREATOR =
            new Parcelable.Creator<FormWidgetInfo>() {
                @Override
                public FormWidgetInfo createFromParcel(Parcel in) {
                    int widgetType = in.readInt();
                    int widgetIndex = in.readInt();
                    Rect rect = new Rect(in.readInt(), in.readInt(), in.readInt(), in.readInt());
                    boolean readOnly = in.readBoolean();
                    String textValue = in.readString();
                    String accessibilityLabel = in.readString();
                    boolean editableText = in.readBoolean();
                    boolean multiSelect = in.readBoolean();
                    boolean multiLineText = in.readBoolean();
                    int maxLength = in.readInt();
                    float fontSize = in.readFloat();
                    ArrayList<ListItem> items = in.createTypedArrayList(ListItem.CREATOR);
                    return new FormWidgetInfo(
                            widgetType,
                            widgetIndex,
                            rect,
                            readOnly,
                            textValue,
                            accessibilityLabel,
                            editableText,
                            multiSelect,
                            multiLineText,
                            maxLength,
                            fontSize,
                            items);
                }

                @Override
                public FormWidgetInfo[] newArray(int size) {
                    return new FormWidgetInfo[size];
                }
            };

    public static final class Builder {
        private final int mWidgetType;
        private final int mWidgetIndex;
        private final Rect mWidgetRect;
        private final String mTextValue;
        private final String mAccessibilityLabel;
        private boolean mReadOnly;
        private boolean mEditableText;
        private boolean mMultiSelect;
        private boolean mMultiLineText;
        private int mMaxLength = -1;
        private float mFontSize;
        private List<ListItem> mListItems = Collections.emptyList();

        public Builder(
                int widgetType,
                int widgetIndex,
                Rect widgetRect,
                String textValue,
                String accessibilityLabel) {
            if (widgetType < WIDGET_TYPE_UNKNOWN || widgetType > WIDGET_TYPE_SIGNATURE) {
                throw new IllegalArgumentException("Unknown widget type: " + widgetType);
            }
            if (widgetIndex < 0) {
                throw new IllegalArgumentException("Widget index cannot be negative");
            }
            Objects.requireNonNull(widgetRect, "widgetRect cannot be null");
            Objects.requireNonNull(textValue, "textValue cannot be null");
            Objects.requireNonNull(accessibilityLabel, "accessibilityLabel cannot be null");
            this.mWidgetType = widgetType;
            this.mWidgetIndex = widgetIndex;
            this.mWidgetRect = new Rect(widgetRect);
            this.mTextValue = textValue;
            this.mAccessibilityLabel = accessibilityLabel;
        }

        public Builder setReadOnly(boolean readOnly) {
            this.mReadOnly = readOnly;
            return this;
        }

        public Builder setEditableText(boolean editableText) {
            if (mWidgetType != WIDGET_TYPE_COMBOBOX && mWidgetType != WIDGET_TYPE_TEXTFIELD) {
                throw new IllegalArgumentException(
                        "Editable text is only supported for combobox and textfield widgets");
            }
            this.mEditableText = editableText;
            return this;
        }

        public Builder setMultiSelect(boolean multiSelect) {
            if (mWidgetType != WIDGET_TYPE_LISTBOX) {
                throw new IllegalArgumentException(
                        "Multi-select is only supported for listbox widgets");
            }
            this.mMultiSelect = multiSelect;
            return this;
        }

        public Builder setMultiLineText(boolean multiLineText) {
            if (mWidgetType != WIDGET_TYPE_TEXTFIELD) {
                throw new IllegalArgumentException(
                        "Multi-line text is only supported for textfield widgets");
            }
            this.mMultiLineText = multiLineText;
            return this;
        }

        public Builder setMaxLength(int maxLength) {
            if (mWidgetType != WIDGET_TYPE_TEXTFIELD) {
                throw new IllegalArgumentException(
                        "Max length is only supported for textfield widgets");
            }
            if (maxLength < 0) {
                throw new IllegalArgumentException("Max length cannot be negative");
            }
            this.mMaxLength = maxLength;
            return this;
        }

        public Builder setFontSize(float fontSize) {
            if (mWidgetType != WIDGET_TYPE_COMBOBOX && mWidgetType != WIDGET_TYPE_TEXTFIELD) {
                throw new IllegalArgumentException(
                        "Font size is only supported for combobox and textfield widgets");
            }
            if (fontSize < 0f) {
                throw new IllegalArgumentException("Font size cannot be negative");
            }
            this.mFontSize = fontSize;
            return this;
        }

        public Builder setListItems(List<ListItem> listItems) {
            Objects.requireNonNull(listItems, "listItems cannot be null");
            if (mWidgetType != WIDGET_TYPE_COMBOBOX && mWidgetType != WIDGET_TYPE_LISTBOX) {
                throw new IllegalArgumentException(
                        "List items are only supported for combobox and listbox widgets");
            }
            ArrayList<ListItem> copy = new ArrayList<ListItem>(listItems.size());
            for (int i = 0; i < listItems.size(); i++) {
                copy.add(Objects.requireNonNull(listItems.get(i), "ListItem cannot be null"));
            }
            this.mListItems = copy;
            return this;
        }

        public FormWidgetInfo build() {
            return new FormWidgetInfo(
                    mWidgetType,
                    mWidgetIndex,
                    mWidgetRect,
                    mReadOnly,
                    mTextValue,
                    mAccessibilityLabel,
                    mEditableText,
                    mMultiSelect,
                    mMultiLineText,
                    mMaxLength,
                    mFontSize,
                    mListItems);
        }
    }
}
