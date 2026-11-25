// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/** Base class for boolean two-state preferences ({@link CheckBoxPreference}, {@link SwitchPreference}). */
public abstract class TwoStatePreference extends Preference {
    private CharSequence summaryOn;
    private CharSequence summaryOff;
    boolean checked;
    private boolean checkedSet;
    private boolean disableDependentsState;

    public TwoStatePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs != null || defStyleAttr != 0 || defStyleRes != 0) {
            shiraz.runtime.XmlAttributes x =
                    new shiraz.runtime.XmlAttributes(context, attrs, defStyleAttr, defStyleRes);
            summaryOn = x.text("summaryOn");
            summaryOff = x.text("summaryOff");
            disableDependentsState = x.bool("disableDependentsState", false);
        }
    }

    public TwoStatePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TwoStatePreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TwoStatePreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onClick() {
        super.onClick();
        boolean newValue = !isChecked();
        if (callChangeListener(Boolean.valueOf(newValue))) {
            setChecked(newValue);
        }
    }

    public void setChecked(boolean checked) {
        boolean changed = this.checked != checked;
        if (changed || !checkedSet) {
            this.checked = checked;
            this.checkedSet = true;
            persistBoolean(checked);
            if (changed) {
                notifyDependencyChange(shouldDisableDependents());
                notifyChanged();
            }
        }
    }

    public boolean isChecked() {
        return checked;
    }

    @Override
    public boolean shouldDisableDependents() {
        boolean shouldDisable = disableDependentsState ? checked : !checked;
        return shouldDisable || super.shouldDisableDependents();
    }

    public void setSummaryOn(CharSequence summary) {
        summaryOn = summary;
        if (isChecked()) {
            notifyChanged();
        }
    }

    public void setSummaryOn(int summaryResId) {
        setSummaryOn(getContext().getString(summaryResId));
    }

    public CharSequence getSummaryOn() {
        return summaryOn;
    }

    public void setSummaryOff(CharSequence summary) {
        summaryOff = summary;
        if (!isChecked()) {
            notifyChanged();
        }
    }

    public void setSummaryOff(int summaryResId) {
        setSummaryOff(getContext().getString(summaryResId));
    }

    public CharSequence getSummaryOff() {
        return summaryOff;
    }

    public boolean getDisableDependentsState() {
        return disableDependentsState;
    }

    public void setDisableDependentsState(boolean disableDependentsState) {
        this.disableDependentsState = disableDependentsState;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return Boolean.valueOf(a.getBoolean(index, false));
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setChecked(restoreValue
                ? getPersistedBoolean(checked)
                : (defaultValue instanceof Boolean && ((Boolean) defaultValue).booleanValue()));
    }

    void syncSummaryView(View view) {
        TextView summaryView = view.findViewById(0x01020010);
        if (summaryView != null) {
            boolean useDefaultSummary = true;
            if (checked && !TextUtils.isEmpty(summaryOn)) {
                summaryView.setText(summaryOn);
                useDefaultSummary = false;
            } else if (!checked && !TextUtils.isEmpty(summaryOff)) {
                summaryView.setText(summaryOff);
                useDefaultSummary = false;
            }
            if (useDefaultSummary) {
                CharSequence summary = getSummary();
                if (!TextUtils.isEmpty(summary)) {
                    summaryView.setText(summary);
                    useDefaultSummary = false;
                }
            }
            int newVisibility = useDefaultSummary ? View.GONE : View.VISIBLE;
            if (newVisibility != summaryView.getVisibility()) {
                summaryView.setVisibility(newVisibility);
            }
        }
    }

    private static final class SavedState extends BaseSavedState {
        boolean checked;

        SavedState(Parcel source) {
            super(source);
            checked = source.readInt() == 1;
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
        myState.checked = isChecked();
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
        setChecked(myState.checked);
    }
}
