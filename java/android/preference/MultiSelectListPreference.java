// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import java.util.HashSet;
import java.util.Set;

/** {@link DialogPreference} displaying multi-select options and persisting a {@link Set} of strings. */
public class MultiSelectListPreference extends DialogPreference {
    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private final Set<String> values = new HashSet<String>();
    private final Set<String> newValues = new HashSet<String>();
    private boolean preferenceChanged;

    public MultiSelectListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs != null || defStyleAttr != 0 || defStyleRes != 0) {
            TypedArray a = context.obtainStyledAttributes(
                    attrs, new int[] {0x010100b2, 0x010101f8}, defStyleAttr, defStyleRes);
            try {
                if (a.hasValue(0)) {
                    entries = a.getTextArray(0);
                }
                if (a.hasValue(1)) {
                    entryValues = a.getTextArray(1);
                }
            } finally {
                a.recycle();
            }
        }
    }

    public MultiSelectListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public MultiSelectListPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0x01010091);
    }

    public MultiSelectListPreference(Context context) {
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

    public void setValues(Set<String> values) {
        this.values.clear();
        if (values != null) {
            this.values.addAll(values);
        }
        persistStringSet(this.values);
        notifyChanged();
    }

    public Set<String> getValues() {
        return values;
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

    private boolean[] getSelectedItems() {
        final CharSequence[] entries = entryValues;
        final int entryCount = entries.length;
        final Set<String> current = values;
        boolean[] result = new boolean[entryCount];
        for (int i = 0; i < entryCount; i++) {
            result[i] = entries[i] != null && current.contains(entries[i].toString());
        }
        return result;
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        if (entries == null || entryValues == null) {
            throw new IllegalStateException(
                    "MultiSelectListPreference requires an entries array and an entryValues array.");
        }
        boolean[] checkedItems = getSelectedItems();
        newValues.clear();
        newValues.addAll(values);
        preferenceChanged = false;
        builder.setMultiChoiceItems(entries, checkedItems,
                new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        String entry = entryValues[which].toString();
                        if (isChecked) {
                            preferenceChanged |= newValues.add(entry);
                        } else {
                            preferenceChanged |= newValues.remove(entry);
                        }
                    }
                });
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult && preferenceChanged) {
            final Set<String> selected = new HashSet<String>(newValues);
            if (callChangeListener(selected)) {
                setValues(selected);
            }
        }
        preferenceChanged = false;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        final CharSequence[] defaultValues = a.getTextArray(index);
        final Set<String> result = new HashSet<String>();
        if (defaultValues != null) {
            for (int i = 0; i < defaultValues.length; i++) {
                if (defaultValues[i] != null) {
                    result.add(defaultValues[i].toString());
                }
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValues(restoreValue ? getPersistedStringSet(values) : (Set<String>) defaultValue);
    }

    private static final class SavedState extends BaseSavedState {
        Set<String> values;

        SavedState(Parcel source) {
            super(source);
            String[] strings = source.createStringArray();
            values = new HashSet<String>();
            if (strings != null) {
                for (int i = 0; i < strings.length; i++) {
                    values.add(strings[i]);
                }
            }
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
        myState.values = getValues();
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
        setValues(myState.values);
    }
}
