// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Describes what happened after the last AutofillService.onFillRequest call. */
public final class FillEventHistory implements Parcelable {
    private final Bundle mClientState;
    private final List<Event> mEvents;

    public FillEventHistory(Bundle clientState, List<Event> events) {
        mClientState = clientState;
        mEvents = events != null
                ? Collections.unmodifiableList(new ArrayList<Event>(events))
                : Collections.<Event>emptyList();
    }

    FillEventHistory(Parcel in) {
        mClientState = in.readBundle(FillEventHistory.class.getClassLoader());
        int n = in.readInt();
        ArrayList<Event> list = new ArrayList<Event>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            int type = in.readInt();
            String datasetId = in.readString();
            Bundle cs = in.readBundle(FillEventHistory.class.getClassLoader());
            int noSave = in.readInt();
            int uiType = in.readInt();
            list.add(new Event(type, datasetId, cs, noSave, uiType));
        }
        mEvents = Collections.unmodifiableList(list);
    }

    @Deprecated
    public Bundle getClientState() {
        return mClientState;
    }

    public List<Event> getEvents() {
        return mEvents;
    }

    @Override
    public String toString() {
        return "FillEventHistory: [events=" + mEvents + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeBundle(mClientState);
        parcel.writeInt(mEvents.size());
        for (int i = 0; i < mEvents.size(); i++) {
            Event ev = mEvents.get(i);
            parcel.writeInt(ev.getType());
            parcel.writeString(ev.getDatasetId());
            parcel.writeBundle(ev.getClientState());
            parcel.writeInt(ev.getNoSaveUiReason());
            parcel.writeInt(ev.getUiType());
        }
    }

    public static final Parcelable.Creator<FillEventHistory> CREATOR =
            new Parcelable.Creator<FillEventHistory>() {
                @Override
                public FillEventHistory createFromParcel(Parcel parcel) {
                    return new FillEventHistory(parcel);
                }

                @Override
                public FillEventHistory[] newArray(int size) {
                    return new FillEventHistory[size];
                }
            };

    public static final class Event {
        public static final int TYPE_DATASET_SELECTED = 0;
        public static final int TYPE_DATASET_AUTHENTICATION_SELECTED = 1;
        public static final int TYPE_AUTHENTICATION_SELECTED = 2;
        public static final int TYPE_SAVE_SHOWN = 3;
        public static final int TYPE_CONTEXT_COMMITTED = 4;
        public static final int TYPE_DATASETS_SHOWN = 5;
        public static final int TYPE_VIEW_REQUESTED_AUTOFILL = 6;

        public static final int NO_SAVE_UI_REASON_NONE = 0;
        public static final int NO_SAVE_UI_REASON_NO_SAVE_INFO = 1;
        public static final int NO_SAVE_UI_REASON_WITH_DELAY_SAVE_FLAG = 2;
        public static final int NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED = 3;
        public static final int NO_SAVE_UI_REASON_NO_VALUE_CHANGED = 4;
        public static final int NO_SAVE_UI_REASON_FIELD_VALIDATION_FAILED = 5;
        public static final int NO_SAVE_UI_REASON_DATASET_MATCH = 6;

        public static final int UI_TYPE_UNKNOWN = 0;
        public static final int UI_TYPE_MENU = 1;
        public static final int UI_TYPE_INLINE = 2;
        public static final int UI_TYPE_DIALOG = 3;

        private final int mEventType;
        private final String mDatasetId;
        private final Bundle mClientState;
        private final int mNoSaveUiReason;
        private final int mUiType;

        Event(int eventType, String datasetId, Bundle clientState, int noSaveUiReason, int uiType) {
            mEventType = eventType;
            mDatasetId = datasetId;
            mClientState = clientState;
            mNoSaveUiReason = noSaveUiReason;
            mUiType = uiType;
        }

        public int getType() {
            return mEventType;
        }

        public String getDatasetId() {
            return mDatasetId;
        }

        public Bundle getClientState() {
            return mClientState;
        }

        public Set<String> getSelectedDatasetIds() {
            return mDatasetId != null
                    ? Collections.singleton(mDatasetId)
                    : Collections.<String>emptySet();
        }

        public Set<String> getIgnoredDatasetIds() {
            return Collections.emptySet();
        }

        public Map<AutofillId, String> getChangedFields() {
            return Collections.emptyMap();
        }

        public Map<AutofillId, FieldClassification> getFieldsClassification() {
            return Collections.emptyMap();
        }

        public Map<AutofillId, Set<String>> getManuallyEnteredField() {
            return Collections.emptyMap();
        }

        public int getNoSaveUiReason() {
            return mNoSaveUiReason;
        }

        public int getUiType() {
            return mUiType;
        }

        @Override
        public String toString() {
            return "Event: [eventType=" + mEventType + ", datasetId=" + mDatasetId + "]";
        }
    }
}
