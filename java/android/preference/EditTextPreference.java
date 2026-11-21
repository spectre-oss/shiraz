// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.LinearLayout;

/** {@link DialogPreference} hosting an {@link EditText} and persisting string values. */
public class EditTextPreference extends DialogPreference {
    private final EditText editText;
    private String text;
    private boolean textSet;

    public EditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        editText = new EditText(context, attrs);
        editText.setId(0x01020003);
        editText.setEnabled(true);
    }

    public EditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public EditTextPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0x01010092);
    }

    public EditTextPreference(Context context) {
        this(context, null);
    }

    public void setText(String text) {
        final boolean changed = !TextUtils.equals(this.text, text);
        if (changed || !textSet) {
            this.text = text;
            this.textSet = true;
            persistString(text);
            if (changed) {
                notifyDependencyChange(shouldDisableDependents());
                notifyChanged();
            }
        }
    }

    public String getText() {
        return text;
    }

    @Override
    protected View onCreateDialogView() {
        View view = super.onCreateDialogView();
        if (view != null) {
            return view;
        }
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setId(0x0102002b);
        return container;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        EditText field = editText;
        field.setText(getText());
        ViewParent oldParent = field.getParent();
        if (oldParent != view) {
            if (oldParent instanceof ViewGroup) {
                ((ViewGroup) oldParent).removeView(field);
            }
            onAddEditTextToDialogView(view, field);
        }
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
    }

    protected void onAddEditTextToDialogView(View dialogView, EditText editText) {
        View container = dialogView.findViewById(0x0102002b);
        ViewGroup group = container instanceof ViewGroup
                ? (ViewGroup) container
                : (dialogView instanceof ViewGroup ? (ViewGroup) dialogView : null);
        if (group != null) {
            group.addView(editText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            String value = editText.getText() == null ? "" : editText.getText().toString();
            if (callChangeListener(value)) {
                setText(value);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setText(restoreValue ? getPersistedString(text) : (String) defaultValue);
    }

    @Override
    public boolean shouldDisableDependents() {
        return TextUtils.isEmpty(text) || super.shouldDisableDependents();
    }

    public EditText getEditText() {
        return editText;
    }

    private static final class SavedState extends BaseSavedState {
        String text;

        SavedState(Parcel source) {
            super(source);
            text = source.readString();
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
        myState.text = getText();
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
        setText(myState.text);
    }
}
