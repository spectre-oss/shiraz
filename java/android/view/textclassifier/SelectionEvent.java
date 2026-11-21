// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class SelectionEvent implements Parcelable {
    public static final int ACTION_OVERTYPE = 100;
    public static final int ACTION_COPY = 101;
    public static final int ACTION_PASTE = 102;
    public static final int ACTION_CUT = 103;
    public static final int ACTION_SHARE = 104;
    public static final int ACTION_SMART_SHARE = 105;
    public static final int ACTION_DRAG = 106;
    public static final int ACTION_ABANDON = 107;
    public static final int ACTION_OTHER = 108;
    public static final int ACTION_SELECT_ALL = 200;
    public static final int ACTION_RESET = 201;

    public static final int EVENT_SELECTION_STARTED = 1;
    public static final int EVENT_SELECTION_MODIFIED = 2;
    public static final int EVENT_SMART_SELECTION_SINGLE = 3;
    public static final int EVENT_SMART_SELECTION_MULTI = 4;
    public static final int EVENT_AUTO_SELECTION = 5;

    public static final int INVOCATION_UNKNOWN = 0;
    public static final int INVOCATION_MANUAL = 1;
    public static final int INVOCATION_LINK = 2;

    private final int mAbsoluteStart;
    private final int mAbsoluteEnd;
    private final int mEventType;
    private final String mEntityType;
    private final String mPackageName;
    private final String mWidgetType;
    private final String mWidgetVersion;
    private final int mInvocationMethod;
    private final String mResultId;
    private final long mEventTime;
    private final long mDurationSinceSessionStart;
    private final long mDurationSincePreviousEvent;
    private final int mEventIndex;
    private final TextClassificationSessionId mSessionId;
    private final int mStart;
    private final int mEnd;
    private final int mSmartStart;
    private final int mSmartEnd;

    private SelectionEvent(
            int start,
            int end,
            int eventType,
            String entityType,
            int invocationMethod,
            String resultId) {
        if (end < start) {
            throw new IllegalArgumentException("end cannot be less than start");
        }
        mStart = start;
        mEnd = end;
        mAbsoluteStart = start;
        mAbsoluteEnd = end;
        mEventType = eventType;
        mEntityType = entityType != null ? entityType : TextClassifier.TYPE_UNKNOWN;
        mInvocationMethod = invocationMethod;
        mResultId = resultId;
        mPackageName = "";
        mWidgetType = TextClassifier.WIDGET_TYPE_UNKNOWN;
        mWidgetVersion = null;
        mEventTime = System.currentTimeMillis();
        mDurationSinceSessionStart = 0L;
        mDurationSincePreviousEvent = 0L;
        mEventIndex = 0;
        mSessionId = null;
        mSmartStart = start;
        mSmartEnd = end;
    }

    private SelectionEvent(Parcel in) {
        mAbsoluteStart = in.readInt();
        mAbsoluteEnd = in.readInt();
        mEventType = in.readInt();
        mEntityType = in.readString();
        mWidgetVersion = in.readString();
        mPackageName = in.readString();
        mWidgetType = in.readString();
        mInvocationMethod = in.readInt();
        mResultId = in.readString();
        mEventTime = in.readLong();
        mDurationSinceSessionStart = in.readLong();
        mDurationSincePreviousEvent = in.readLong();
        mEventIndex = in.readInt();
        mSessionId = in.readTypedObject(TextClassificationSessionId.CREATOR);
        mStart = in.readInt();
        mEnd = in.readInt();
        mSmartStart = in.readInt();
        mSmartEnd = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAbsoluteStart);
        dest.writeInt(mAbsoluteEnd);
        dest.writeInt(mEventType);
        dest.writeString(mEntityType);
        dest.writeString(mWidgetVersion);
        dest.writeString(mPackageName);
        dest.writeString(mWidgetType);
        dest.writeInt(mInvocationMethod);
        dest.writeString(mResultId);
        dest.writeLong(mEventTime);
        dest.writeLong(mDurationSinceSessionStart);
        dest.writeLong(mDurationSincePreviousEvent);
        dest.writeInt(mEventIndex);
        dest.writeTypedObject(mSessionId, flags);
        dest.writeInt(mStart);
        dest.writeInt(mEnd);
        dest.writeInt(mSmartStart);
        dest.writeInt(mSmartEnd);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static SelectionEvent createSelectionStartedEvent(int invocationMethod, int start) {
        return new SelectionEvent(
                start, start + 1, EVENT_SELECTION_STARTED,
                TextClassifier.TYPE_UNKNOWN, invocationMethod, "");
    }

    public static SelectionEvent createSelectionModifiedEvent(int start, int end) {
        return new SelectionEvent(
                start, end, EVENT_SELECTION_MODIFIED,
                TextClassifier.TYPE_UNKNOWN, INVOCATION_UNKNOWN, "");
    }

    public static SelectionEvent createSelectionModifiedEvent(
            int start, int end, TextClassification classification) {
        Objects.requireNonNull(classification, "classification");
        String entity = classification.getEntityCount() > 0
                ? classification.getEntity(0) : TextClassifier.TYPE_UNKNOWN;
        return new SelectionEvent(
                start, end, EVENT_SELECTION_MODIFIED,
                entity, INVOCATION_UNKNOWN, classification.getId());
    }

    public static SelectionEvent createSelectionModifiedEvent(
            int start, int end, TextSelection selection) {
        Objects.requireNonNull(selection, "selection");
        String entity = selection.getEntityCount() > 0
                ? selection.getEntity(0) : TextClassifier.TYPE_UNKNOWN;
        return new SelectionEvent(
                start, end, EVENT_AUTO_SELECTION,
                entity, INVOCATION_UNKNOWN, selection.getId());
    }

    public static SelectionEvent createSelectionActionEvent(
            int start, int end, int actionType) {
        return new SelectionEvent(
                start, end, actionType,
                TextClassifier.TYPE_UNKNOWN, INVOCATION_UNKNOWN, "");
    }

    public static SelectionEvent createSelectionActionEvent(
            int start, int end, int actionType, TextClassification classification) {
        Objects.requireNonNull(classification, "classification");
        String entity = classification.getEntityCount() > 0
                ? classification.getEntity(0) : TextClassifier.TYPE_UNKNOWN;
        return new SelectionEvent(
                start, end, actionType,
                entity, INVOCATION_UNKNOWN, classification.getId());
    }

    public int getEventType() { return mEventType; }
    public String getEntityType() { return mEntityType; }
    public String getPackageName() { return mPackageName; }
    public String getWidgetType() { return mWidgetType; }
    public String getWidgetVersion() { return mWidgetVersion; }
    public int getInvocationMethod() { return mInvocationMethod; }
    public String getResultId() { return mResultId; }
    public long getEventTime() { return mEventTime; }
    public long getDurationSinceSessionStart() { return mDurationSinceSessionStart; }
    public long getDurationSincePreviousEvent() { return mDurationSincePreviousEvent; }
    public int getEventIndex() { return mEventIndex; }
    public TextClassificationSessionId getSessionId() { return mSessionId; }
    public int getStart() { return mStart; }
    public int getEnd() { return mEnd; }
    public int getSmartStart() { return mSmartStart; }
    public int getSmartEnd() { return mSmartEnd; }

    public static boolean isTerminal(int eventType) {
        switch (eventType) {
            case ACTION_OVERTYPE:
            case ACTION_COPY:
            case ACTION_PASTE:
            case ACTION_CUT:
            case ACTION_SHARE:
            case ACTION_SMART_SHARE:
            case ACTION_DRAG:
            case ACTION_ABANDON:
            case ACTION_OTHER:
                return true;
            default:
                return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mAbsoluteStart),
                Integer.valueOf(mAbsoluteEnd),
                Integer.valueOf(mEventType),
                mEntityType,
                Integer.valueOf(mInvocationMethod),
                mResultId,
                Integer.valueOf(mStart),
                Integer.valueOf(mEnd));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SelectionEvent)) return false;
        SelectionEvent other = (SelectionEvent) obj;
        return mAbsoluteStart == other.mAbsoluteStart
                && mAbsoluteEnd == other.mAbsoluteEnd
                && mEventType == other.mEventType
                && mInvocationMethod == other.mInvocationMethod
                && mStart == other.mStart
                && mEnd == other.mEnd
                && Objects.equals(mEntityType, other.mEntityType)
                && Objects.equals(mResultId, other.mResultId);
    }

    @Override
    public String toString() {
        return "SelectionEvent{eventType=" + mEventType
                + ", entityType=" + mEntityType
                + ", start=" + mStart + ", end=" + mEnd + "}";
    }

    public static final Parcelable.Creator<SelectionEvent> CREATOR =
            new Parcelable.Creator<SelectionEvent>() {
                @Override
                public SelectionEvent createFromParcel(Parcel in) {
                    return new SelectionEvent(in);
                }

                @Override
                public SelectionEvent[] newArray(int size) {
                    return new SelectionEvent[size];
                }
            };
}
