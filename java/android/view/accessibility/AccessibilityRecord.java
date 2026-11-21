// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.Display;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Local event metadata; no system accessibility connection is claimed. */
public class AccessibilityRecord {
    protected boolean recycled;
    private View source;
    private int sourceId = AccessibilityNodeProvider.HOST_VIEW_ID;
    private int windowId = -1;
    private int displayId = Display.DEFAULT_DISPLAY;
    private final ArrayList<CharSequence> text = new ArrayList<CharSequence>();
    private CharSequence className;
    private CharSequence contentDescription;
    private CharSequence beforeText;
    private Parcelable parcelableData;
    private boolean checked;
    private boolean enabled;
    private boolean password;
    private boolean fullScreen;
    private boolean scrollable;
    private int itemCount = -1;
    private int currentItemIndex = -1;
    private int fromIndex = -1;
    private int toIndex = -1;
    private int scrollX = -1;
    private int scrollY = -1;
    private int scrollDeltaX = -1;
    private int scrollDeltaY = -1;
    private int maxScrollX = -1;
    private int maxScrollY = -1;
    private int addedCount = -1;
    private int removedCount = -1;

    public AccessibilityRecord() {
    }

    public AccessibilityRecord(AccessibilityRecord record) {
        copyFrom(record);
    }

    protected void live() {
        if (recycled) {
            throw new IllegalStateException("Accessibility record recycled");
        }
    }

    public static AccessibilityRecord obtain() {
        return new AccessibilityRecord();
    }

    public static AccessibilityRecord obtain(AccessibilityRecord other) {
        AccessibilityRecord copy = obtain();
        copy.copyFrom(other);
        return copy;
    }

    public void recycle() {
        live();
        recycled = true;
        source = null;
        sourceId = AccessibilityNodeProvider.HOST_VIEW_ID;
        parcelableData = null;
        text.clear();
    }

    public void setSource(View view) {
        setSource(view, AccessibilityNodeProvider.HOST_VIEW_ID);
    }

    public void setSource(View view, int id) {
        live();
        source = view;
        sourceId = id;
    }

    public AccessibilityNodeInfo getSource() {
        live();
        if (source == null) {
            return null;
        }
        if (sourceId == AccessibilityNodeProvider.HOST_VIEW_ID) {
            return source.createAccessibilityNodeInfo();
        }
        // Resolve only through the local provider; never substitute the host for a missing child.
        AccessibilityNodeProvider provider = source.getAccessibilityNodeProvider();
        return provider == null ? null : provider.createAccessibilityNodeInfo(sourceId);
    }

    public AccessibilityNodeInfo getSource(int prefetchingStrategy) {
        return getSource();
    }

    public int getDisplayId() {
        live();
        return displayId;
    }

    void setDisplayId(int value) {
        live();
        displayId = value;
    }

    public int getWindowId() {
        live();
        return windowId;
    }

    void setWindowId(int value) {
        live();
        windowId = value;
    }

    public List<CharSequence> getText() {
        live();
        return text;
    }

    public CharSequence getClassName() {
        live();
        return className;
    }

    public void setClassName(CharSequence value) {
        live();
        className = value == null ? null : value.toString();
    }

    public CharSequence getContentDescription() {
        live();
        return contentDescription;
    }

    public void setContentDescription(CharSequence value) {
        live();
        contentDescription = value == null ? null : value.toString();
    }

    public CharSequence getBeforeText() {
        live();
        return beforeText;
    }

    public void setBeforeText(CharSequence value) {
        live();
        beforeText = value == null ? null : value.toString();
    }

    public Parcelable getParcelableData() {
        live();
        return parcelableData;
    }

    public void setParcelableData(Parcelable value) {
        live();
        parcelableData = value;
    }

    public boolean isChecked() {
        live();
        return checked;
    }

    public void setChecked(boolean value) {
        live();
        checked = value;
    }

    public boolean isEnabled() {
        live();
        return enabled;
    }

    public void setEnabled(boolean value) {
        live();
        enabled = value;
    }

    public boolean isPassword() {
        live();
        return password;
    }

    public void setPassword(boolean value) {
        live();
        password = value;
    }

    public boolean isFullScreen() {
        live();
        return fullScreen;
    }

    public void setFullScreen(boolean value) {
        live();
        fullScreen = value;
    }

    public boolean isScrollable() {
        live();
        return scrollable;
    }

    public void setScrollable(boolean value) {
        live();
        scrollable = value;
    }

    public int getItemCount() {
        live();
        return itemCount;
    }

    public void setItemCount(int value) {
        live();
        itemCount = value;
    }

    public int getCurrentItemIndex() {
        live();
        return currentItemIndex;
    }

    public void setCurrentItemIndex(int value) {
        live();
        currentItemIndex = value;
    }

    public int getFromIndex() {
        live();
        return fromIndex;
    }

    public void setFromIndex(int value) {
        live();
        fromIndex = value;
    }

    public int getToIndex() {
        live();
        return toIndex;
    }

    public void setToIndex(int value) {
        live();
        toIndex = value;
    }

    public int getScrollX() {
        live();
        return scrollX;
    }

    public void setScrollX(int value) {
        live();
        scrollX = value;
    }

    public int getScrollY() {
        live();
        return scrollY;
    }

    public void setScrollY(int value) {
        live();
        scrollY = value;
    }

    public int getScrollDeltaX() {
        live();
        return scrollDeltaX;
    }

    public void setScrollDeltaX(int value) {
        live();
        scrollDeltaX = value;
    }

    public int getScrollDeltaY() {
        live();
        return scrollDeltaY;
    }

    public void setScrollDeltaY(int value) {
        live();
        scrollDeltaY = value;
    }

    public int getMaxScrollX() {
        live();
        return maxScrollX;
    }

    public void setMaxScrollX(int value) {
        live();
        maxScrollX = value;
    }

    public int getMaxScrollY() {
        live();
        return maxScrollY;
    }

    public void setMaxScrollY(int value) {
        live();
        maxScrollY = value;
    }

    public int getAddedCount() {
        live();
        return addedCount;
    }

    public void setAddedCount(int value) {
        live();
        addedCount = value;
    }

    public int getRemovedCount() {
        live();
        return removedCount;
    }

    public void setRemovedCount(int value) {
        live();
        removedCount = value;
    }

    protected void copyFrom(AccessibilityRecord other) {
        other.live();
        source = other.source;
        sourceId = other.sourceId;
        windowId = other.windowId;
        displayId = other.displayId;
        text.clear();
        text.addAll(other.text);
        className = other.className;
        contentDescription = other.contentDescription;
        beforeText = other.beforeText;
        parcelableData = other.parcelableData;
        checked = other.checked;
        enabled = other.enabled;
        password = other.password;
        fullScreen = other.fullScreen;
        scrollable = other.scrollable;
        itemCount = other.itemCount;
        currentItemIndex = other.currentItemIndex;
        fromIndex = other.fromIndex;
        toIndex = other.toIndex;
        scrollX = other.scrollX;
        scrollY = other.scrollY;
        scrollDeltaX = other.scrollDeltaX;
        scrollDeltaY = other.scrollDeltaY;
        maxScrollX = other.maxScrollX;
        maxScrollY = other.maxScrollY;
        addedCount = other.addedCount;
        removedCount = other.removedCount;
    }

    void writeRecordToParcel(Parcel out, int flags) {
        live();
        int bools = (checked ? 1 : 0)
                | (enabled ? 2 : 0)
                | (password ? 4 : 0)
                | (fullScreen ? 8 : 0)
                | (scrollable ? 16 : 0);
        out.writeInt(bools);
        out.writeInt(windowId);
        out.writeInt(displayId);
        out.writeInt(itemCount);
        out.writeInt(currentItemIndex);
        out.writeInt(fromIndex);
        out.writeInt(toIndex);
        out.writeInt(scrollX);
        out.writeInt(scrollY);
        out.writeInt(scrollDeltaX);
        out.writeInt(scrollDeltaY);
        out.writeInt(maxScrollX);
        out.writeInt(maxScrollY);
        out.writeInt(addedCount);
        out.writeInt(removedCount);
        out.writeString(className == null ? null : className.toString());
        out.writeString(contentDescription == null ? null : contentDescription.toString());
        out.writeString(beforeText == null ? null : beforeText.toString());
        out.writeInt(text.size());
        for (int i = 0; i < text.size(); i++) {
            CharSequence cs = text.get(i);
            out.writeString(cs == null ? null : cs.toString());
        }
    }

    void readRecordFromParcel(Parcel in) {
        int bools = in.readInt();
        checked = (bools & 1) != 0;
        enabled = (bools & 2) != 0;
        password = (bools & 4) != 0;
        fullScreen = (bools & 8) != 0;
        scrollable = (bools & 16) != 0;
        windowId = in.readInt();
        displayId = in.readInt();
        itemCount = in.readInt();
        currentItemIndex = in.readInt();
        fromIndex = in.readInt();
        toIndex = in.readInt();
        scrollX = in.readInt();
        scrollY = in.readInt();
        scrollDeltaX = in.readInt();
        scrollDeltaY = in.readInt();
        maxScrollX = in.readInt();
        maxScrollY = in.readInt();
        addedCount = in.readInt();
        removedCount = in.readInt();
        className = in.readString();
        contentDescription = in.readString();
        beforeText = in.readString();
        int count = in.readInt();
        text.clear();
        for (int i = 0; i < count; i++) {
            text.add(in.readString());
        }
    }

    @Override
    public String toString() {
        live();
        return "AccessibilityRecord[ClassName: " + className
                + "; Text: " + text
                + "; ContentDescription: " + contentDescription
                + "; ItemCount: " + itemCount + "]";
    }
}
