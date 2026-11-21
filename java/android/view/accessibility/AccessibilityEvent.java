// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Objects;

public final class AccessibilityEvent extends AccessibilityRecord implements Parcelable {
    public static final int INVALID_POSITION = -1;
    public static final int MAX_TEXT_LENGTH = 500;

    public static final int TYPE_VIEW_CLICKED = 0x00000001;
    public static final int TYPE_VIEW_LONG_CLICKED = 0x00000002;
    public static final int TYPE_VIEW_SELECTED = 0x00000004;
    public static final int TYPE_VIEW_FOCUSED = 0x00000008;
    public static final int TYPE_VIEW_TEXT_CHANGED = 0x00000010;
    public static final int TYPE_WINDOW_STATE_CHANGED = 0x00000020;
    public static final int TYPE_NOTIFICATION_STATE_CHANGED = 0x00000040;
    public static final int TYPE_VIEW_HOVER_ENTER = 0x00000080;
    public static final int TYPE_VIEW_HOVER_EXIT = 0x00000100;
    public static final int TYPE_TOUCH_EXPLORATION_GESTURE_START = 0x00000200;
    public static final int TYPE_TOUCH_EXPLORATION_GESTURE_END = 0x00000400;
    public static final int TYPE_WINDOW_CONTENT_CHANGED = 0x00000800;
    public static final int TYPE_VIEW_SCROLLED = 0x00001000;
    public static final int TYPE_VIEW_TEXT_SELECTION_CHANGED = 0x00002000;
    public static final int TYPE_ANNOUNCEMENT = 0x00004000;
    public static final int TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000;
    public static final int TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED = 0x00010000;
    public static final int TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY = 0x00020000;
    public static final int TYPE_GESTURE_DETECTION_START = 0x00040000;
    public static final int TYPE_GESTURE_DETECTION_END = 0x00080000;
    public static final int TYPE_TOUCH_INTERACTION_START = 0x00100000;
    public static final int TYPE_TOUCH_INTERACTION_END = 0x00200000;
    public static final int TYPE_WINDOWS_CHANGED = 0x00400000;
    public static final int TYPE_VIEW_CONTEXT_CLICKED = 0x00800000;
    public static final int TYPE_ASSIST_READING_CONTEXT = 0x01000000;
    public static final int TYPE_SPEECH_STATE_CHANGE = 0x02000000;
    public static final int TYPE_VIEW_TARGETED_BY_SCROLL = 0x04000000;
    public static final int TYPES_ALL_MASK = 0xFFFFFFFF;

    public static final int CONTENT_CHANGE_TYPE_UNDEFINED = 0x00000000;
    public static final int CONTENT_CHANGE_TYPE_SUBTREE = 0x00000001;
    public static final int CONTENT_CHANGE_TYPE_TEXT = 0x00000002;
    public static final int CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION = 0x00000004;
    public static final int CONTENT_CHANGE_TYPE_PANE_TITLE = 0x00000008;
    public static final int CONTENT_CHANGE_TYPE_PANE_APPEARED = 0x00000010;
    public static final int CONTENT_CHANGE_TYPE_PANE_DISAPPEARED = 0x00000020;
    public static final int CONTENT_CHANGE_TYPE_STATE_DESCRIPTION = 0x00000040;
    public static final int CONTENT_CHANGE_TYPE_DRAG_STARTED = 0x00000080;
    public static final int CONTENT_CHANGE_TYPE_DRAG_DROPPED = 0x00000100;
    public static final int CONTENT_CHANGE_TYPE_DRAG_CANCELLED = 0x00000200;
    public static final int CONTENT_CHANGE_TYPE_CONTENT_INVALID = 0x00000400;
    public static final int CONTENT_CHANGE_TYPE_ERROR = 0x00000800;
    public static final int CONTENT_CHANGE_TYPE_ENABLED = 0x00001000;

    public static final int SPEECH_STATE_SPEAKING_START = 0x00000001;
    public static final int SPEECH_STATE_SPEAKING_END = 0x00000002;
    public static final int SPEECH_STATE_LISTENING_START = 0x00000004;
    public static final int SPEECH_STATE_LISTENING_END = 0x00000008;

    public static final int WINDOWS_CHANGE_ADDED = 0x00000001;
    public static final int WINDOWS_CHANGE_REMOVED = 0x00000002;
    public static final int WINDOWS_CHANGE_TITLE = 0x00000004;
    public static final int WINDOWS_CHANGE_BOUNDS = 0x00000008;
    public static final int WINDOWS_CHANGE_LAYER = 0x00000010;
    public static final int WINDOWS_CHANGE_ACTIVE = 0x00000020;
    public static final int WINDOWS_CHANGE_FOCUSED = 0x00000040;
    public static final int WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED = 0x00000080;
    public static final int WINDOWS_CHANGE_PARENT = 0x00000100;
    public static final int WINDOWS_CHANGE_CHILDREN = 0x00000200;
    public static final int WINDOWS_CHANGE_PIP = 0x00000400;

    private int eventType;
    private int contentChangeTypes;
    private int speechStateChangeTypes;
    private int windowChangeTypes;
    private int action;
    private int movementGranularity;
    private long eventTime;
    private CharSequence packageName;
    private boolean accessibilityDataSensitive;
    private final ArrayList<AccessibilityRecord> records = new ArrayList<AccessibilityRecord>();

    public AccessibilityEvent() {
    }

    public AccessibilityEvent(int eventType) {
        this.eventType = eventType;
    }

    public AccessibilityEvent(AccessibilityEvent event) {
        super(event);
        this.eventType = event.eventType;
        this.contentChangeTypes = event.contentChangeTypes;
        this.speechStateChangeTypes = event.speechStateChangeTypes;
        this.windowChangeTypes = event.windowChangeTypes;
        this.action = event.action;
        this.movementGranularity = event.movementGranularity;
        this.eventTime = event.eventTime;
        this.packageName = event.packageName;
        this.accessibilityDataSensitive = event.accessibilityDataSensitive;
        for (int i = 0; i < event.records.size(); i++) {
            this.records.add(AccessibilityRecord.obtain(event.records.get(i)));
        }
    }

    public static AccessibilityEvent obtain() {
        return new AccessibilityEvent();
    }

    public static AccessibilityEvent obtain(int type) {
        return new AccessibilityEvent(type);
    }

    public static AccessibilityEvent obtain(AccessibilityEvent other) {
        other.live();
        return new AccessibilityEvent(other);
    }

    public int getEventType() {
        live();
        return eventType;
    }

    public void setEventType(int value) {
        live();
        eventType = value;
    }

    public int getContentChangeTypes() {
        live();
        return contentChangeTypes;
    }

    public void setContentChangeTypes(int value) {
        live();
        contentChangeTypes = value;
    }

    public boolean isAccessibilityDataSensitive() {
        live();
        return accessibilityDataSensitive;
    }

    public void setAccessibilityDataSensitive(boolean accessibilityDataSensitive) {
        live();
        this.accessibilityDataSensitive = accessibilityDataSensitive;
    }

    public int getSpeechStateChangeTypes() {
        live();
        return speechStateChangeTypes;
    }

    public void setSpeechStateChangeTypes(int state) {
        live();
        speechStateChangeTypes = state;
    }

    public int getWindowChanges() {
        live();
        return windowChangeTypes;
    }

    void setWindowChanges(int changes) {
        live();
        windowChangeTypes = changes;
    }

    public int getAction() {
        live();
        return action;
    }

    public void setAction(int value) {
        live();
        action = value;
    }

    public int getMovementGranularity() {
        live();
        return movementGranularity;
    }

    public void setMovementGranularity(int value) {
        live();
        movementGranularity = value;
    }

    public long getEventTime() {
        live();
        return eventTime;
    }

    public void setEventTime(long value) {
        live();
        eventTime = value;
    }

    public CharSequence getPackageName() {
        live();
        return packageName;
    }

    public void setPackageName(CharSequence value) {
        live();
        packageName = value == null ? null : value.toString();
    }

    public void appendRecord(AccessibilityRecord value) {
        live();
        if (value == null) {
            throw new NullPointerException("record");
        }
        if (value == this || records.contains(value)) {
            throw new IllegalArgumentException("Event record ownership");
        }
        if (records.size() >= 1024) {
            throw new IllegalStateException("Accessibility record limit");
        }
        records.add(value);
    }

    public int getRecordCount() {
        live();
        return records.size();
    }

    public AccessibilityRecord getRecord(int index) {
        live();
        return records.get(index);
    }

    @Override
    public void recycle() {
        super.recycle();
        for (int i = 0; i < records.size(); i++) {
            records.get(i).recycle();
        }
        records.clear();
    }

    public void initFromParcel(Parcel in) {
        live();
        Objects.requireNonNull(in, "in");
        readRecordFromParcel(in);
        eventType = in.readInt();
        contentChangeTypes = in.readInt();
        speechStateChangeTypes = in.readInt();
        windowChangeTypes = in.readInt();
        action = in.readInt();
        movementGranularity = in.readInt();
        eventTime = in.readLong();
        packageName = in.readString();
        accessibilityDataSensitive = in.readInt() != 0;
        int recordCount = in.readInt();
        records.clear();
        for (int i = 0; i < recordCount; i++) {
            AccessibilityRecord rec = new AccessibilityRecord();
            rec.readRecordFromParcel(in);
            records.add(rec);
        }
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        live();
        Objects.requireNonNull(out, "out");
        writeRecordToParcel(out, flags);
        out.writeInt(eventType);
        out.writeInt(contentChangeTypes);
        out.writeInt(speechStateChangeTypes);
        out.writeInt(windowChangeTypes);
        out.writeInt(action);
        out.writeInt(movementGranularity);
        out.writeLong(eventTime);
        out.writeString(packageName == null ? null : packageName.toString());
        out.writeInt(accessibilityDataSensitive ? 1 : 0);
        out.writeInt(records.size());
        for (int i = 0; i < records.size(); i++) {
            records.get(i).writeRecordToParcel(out, flags);
        }
    }

    @Override
    public int describeContents() {
        live();
        return 0;
    }

    @Override
    public String toString() {
        live();
        return "EventType: " + eventTypeToString(eventType)
                + "; EventTime: " + eventTime
                + "; PackageName: " + packageName
                + "; MovementGranularity: " + movementGranularity
                + "; Action: " + action
                + "; ContentChangeTypes: " + contentChangeTypes
                + "; recordCount: " + records.size();
    }

    public static String eventTypeToString(int eventType) {
        if (eventType == TYPES_ALL_MASK) {
            return "TYPES_ALL_MASK";
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        int remaining = eventType;
        while (remaining != 0) {
            int flag = 1 << Integer.numberOfTrailingZeros(remaining);
            remaining &= ~flag;
            if (count > 0) {
                builder.append(", ");
            }
            builder.append(singleEventTypeToString(flag));
            count++;
        }
        if (count > 1) {
            builder.insert(0, '[');
            builder.append(']');
        }
        return builder.toString();
    }

    private static String singleEventTypeToString(int eventType) {
        switch (eventType) {
            case TYPE_VIEW_CLICKED:
                return "TYPE_VIEW_CLICKED";
            case TYPE_VIEW_LONG_CLICKED:
                return "TYPE_VIEW_LONG_CLICKED";
            case TYPE_VIEW_SELECTED:
                return "TYPE_VIEW_SELECTED";
            case TYPE_VIEW_FOCUSED:
                return "TYPE_VIEW_FOCUSED";
            case TYPE_VIEW_TEXT_CHANGED:
                return "TYPE_VIEW_TEXT_CHANGED";
            case TYPE_WINDOW_STATE_CHANGED:
                return "TYPE_WINDOW_STATE_CHANGED";
            case TYPE_NOTIFICATION_STATE_CHANGED:
                return "TYPE_NOTIFICATION_STATE_CHANGED";
            case TYPE_VIEW_HOVER_ENTER:
                return "TYPE_VIEW_HOVER_ENTER";
            case TYPE_VIEW_HOVER_EXIT:
                return "TYPE_VIEW_HOVER_EXIT";
            case TYPE_TOUCH_EXPLORATION_GESTURE_START:
                return "TYPE_TOUCH_EXPLORATION_GESTURE_START";
            case TYPE_TOUCH_EXPLORATION_GESTURE_END:
                return "TYPE_TOUCH_EXPLORATION_GESTURE_END";
            case TYPE_WINDOW_CONTENT_CHANGED:
                return "TYPE_WINDOW_CONTENT_CHANGED";
            case TYPE_VIEW_SCROLLED:
                return "TYPE_VIEW_SCROLLED";
            case TYPE_VIEW_TEXT_SELECTION_CHANGED:
                return "TYPE_VIEW_TEXT_SELECTION_CHANGED";
            case TYPE_ANNOUNCEMENT:
                return "TYPE_ANNOUNCEMENT";
            case TYPE_VIEW_ACCESSIBILITY_FOCUSED:
                return "TYPE_VIEW_ACCESSIBILITY_FOCUSED";
            case TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED:
                return "TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED";
            case TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY:
                return "TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY";
            case TYPE_GESTURE_DETECTION_START:
                return "TYPE_GESTURE_DETECTION_START";
            case TYPE_GESTURE_DETECTION_END:
                return "TYPE_GESTURE_DETECTION_END";
            case TYPE_TOUCH_INTERACTION_START:
                return "TYPE_TOUCH_INTERACTION_START";
            case TYPE_TOUCH_INTERACTION_END:
                return "TYPE_TOUCH_INTERACTION_END";
            case TYPE_WINDOWS_CHANGED:
                return "TYPE_WINDOWS_CHANGED";
            case TYPE_VIEW_CONTEXT_CLICKED:
                return "TYPE_VIEW_CONTEXT_CLICKED";
            case TYPE_ASSIST_READING_CONTEXT:
                return "TYPE_ASSIST_READING_CONTEXT";
            case TYPE_SPEECH_STATE_CHANGE:
                return "TYPE_SPEECH_STATE_CHANGE";
            case TYPE_VIEW_TARGETED_BY_SCROLL:
                return "TYPE_VIEW_TARGETED_BY_SCROLL";
            default:
                return Integer.toHexString(eventType);
        }
    }

    public static final Parcelable.Creator<AccessibilityEvent> CREATOR =
            new Parcelable.Creator<AccessibilityEvent>() {
                @Override
                public AccessibilityEvent createFromParcel(Parcel in) {
                    AccessibilityEvent event = new AccessibilityEvent();
                    event.initFromParcel(in);
                    return event;
                }

                @Override
                public AccessibilityEvent[] newArray(int size) {
                    return new AccessibilityEvent[size];
                }
            };
}
