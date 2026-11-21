// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;

/** {@link DialogPreference} displaying single-choice options and persisting a single string value. */
public class ListPreference extends DialogPreference {
    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private String value;
    private String summaryFormat;
    private int clickedDialogEntryIndex;
    private boolean valueSet;

    public ListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        CharSequence rawSummary = super.getSummary();
        if (rawSummary != null) {
            summaryFormat = rawSummary.toString();
        }
    }

    public ListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ListPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0x01010091);
    }

    public ListPreference(Context context) {
        this(context, null);
    }

    public void setEntries(CharSequence[] entries) {
        this.entries = entries;
    }

    public void setEntries(int entriesResId) {
        setEntries(getContext().getResources().getTextArray(entriesResId));
    }

    public CharSequence[] getEntries() {
        return entries;
    }

    public void setEntryValues(CharSequence[] entryValues) {
        this.entryValues = entryValues;
    }

    public void setEntryValues(int entryValuesResId) {
        setEntryValues(getContext().getResources().getTextArray(entryValuesResId));
    }

    public CharSequence[] getEntryValues() {
        return entryValues;
    }

    public void setValue(String value) {
        final boolean changed = !TextUtils.equals(this.value, value);
        if (changed || !valueSet) {
            this.value = value;
            this.valueSet = true;
            persistString(value);
            if (changed) {
                notifyChanged();
            }
        }
    }

    @Override
    public CharSequence getSummary() {
        final CharSequence entry = getEntry();
        if (summaryFormat == null) {
            return super.getSummary();
        }
        return String.format(summaryFormat, entry == null ? "" : entry);
    }

    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(summary);
        if (summary == null && summaryFormat != null) {
            summaryFormat = null;
        } else if (summary != null && !summary.toString().equals(summaryFormat)) {
            summaryFormat = summary.toString();
        }
    }

    public void setValueIndex(int index) {
        if (entryValues != null && index >= 0 && index < entryValues.length) {
            setValue(entryValues[index].toString());
        }
    }

    public String getValue() {
        return value;
    }

    public CharSequence getEntry() {
        int index = getValueIndex();
        return index >= 0 && entries != null && index < entries.length ? entries[index] : null;
    }

    public int findIndexOfValue(String value) {
        if (value != null && entryValues != null) {
            for (int i = entryValues.length - 1; i >= 0; i--) {
                if (entryValues[i] != null && entryValues[i].toString().equals(value)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int getValueIndex() {
        return findIndexOfValue(value);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        if (entries == null || entryValues == null) {
            throw new IllegalStateException(
                    "ListPreference requires an entries array and an entryValues array.");
        }
        clickedDialogEntryIndex = getValueIndex();
        builder.setSingleChoiceItems(entries, clickedDialogEntryIndex,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clickedDialogEntryIndex = which;
                        ListPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                        dialog.dismiss();
                    }
                });
        builder.setPositiveButton(null, null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult && clickedDialogEntryIndex >= 0 && entryValues != null
                && clickedDialogEntryIndex < entryValues.length) {
            String val = entryValues[clickedDialogEntryIndex].toString();
            if (callChangeListener(val)) {
                setValue(val);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedString(value) : (String) defaultValue);
    }

    private static final class SavedState extends BaseSavedState {
        String value;

        SavedState(Parcel source) {
            super(source);
            value = source.readString();
        }

        SavedState(Parcelable superState) {
            super(superState);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            return superState;
        }
        final SavedState myState = new SavedState(superState);
        myState.value = getValue();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
    }
}
