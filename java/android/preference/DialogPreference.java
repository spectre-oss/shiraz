// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

/** Base {@link Preference} that opens a modal {@link AlertDialog} when clicked. */
public abstract class DialogPreference extends Preference
        implements DialogInterface.OnClickListener,
                DialogInterface.OnDismissListener,
                PreferenceManager.OnActivityDestroyListener {
    private AlertDialog.Builder builder;
    private CharSequence dialogTitle;
    private CharSequence dialogMessage;
    private Drawable dialogIcon;
    private CharSequence positiveButtonText;
    private CharSequence negativeButtonText;
    private int dialogLayoutResId;
    private Dialog dialog;
    private int whichButtonClicked;

    public DialogPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        positiveButtonText = "OK";
        negativeButtonText = "Cancel";
        if (attrs != null || defStyleAttr != 0 || defStyleRes != 0) {
            shiraz.runtime.XmlAttributes x =
                    new shiraz.runtime.XmlAttributes(context, attrs, defStyleAttr, defStyleRes);
            CharSequence dt = x.text("dialogTitle");
            dialogTitle = dt != null ? dt : getTitle();
            dialogMessage = x.text("dialogMessage");
            if (x.has("dialogIcon")) {
                dialogIcon = x.drawable("dialogIcon");
            }
            CharSequence pos = x.text("positiveButtonText");
            if (pos != null) {
                positiveButtonText = pos;
            }
            CharSequence neg = x.text("negativeButtonText");
            if (neg != null) {
                negativeButtonText = neg;
            }
            dialogLayoutResId = x.reference("dialogLayout", 0);
        }
    }

    public DialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public DialogPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0x01010091);
    }

    public DialogPreference(Context context) {
        this(context, null);
    }

    public void setDialogTitle(CharSequence dialogTitle) {
        this.dialogTitle = dialogTitle;
    }

    public void setDialogTitle(int dialogTitleResId) {
        setDialogTitle(getContext().getString(dialogTitleResId));
    }

    public CharSequence getDialogTitle() {
        return dialogTitle != null ? dialogTitle : getTitle();
    }

    public void setDialogMessage(CharSequence dialogMessage) {
        this.dialogMessage = dialogMessage;
    }

    public void setDialogMessage(int dialogMessageResId) {
        setDialogMessage(getContext().getString(dialogMessageResId));
    }

    public CharSequence getDialogMessage() {
        return dialogMessage;
    }

    public void setDialogIcon(Drawable dialogIcon) {
        this.dialogIcon = dialogIcon;
    }

    public void setDialogIcon(int dialogIconRes) {
        this.dialogIcon = dialogIconRes == 0 ? null : getContext().getDrawable(dialogIconRes);
    }

    public Drawable getDialogIcon() {
        return dialogIcon;
    }

    public void setPositiveButtonText(CharSequence positiveButtonText) {
        this.positiveButtonText = positiveButtonText;
    }

    public void setPositiveButtonText(int positiveButtonTextResId) {
        setPositiveButtonText(getContext().getString(positiveButtonTextResId));
    }

    public CharSequence getPositiveButtonText() {
        return positiveButtonText;
    }

    public void setNegativeButtonText(CharSequence negativeButtonText) {
        this.negativeButtonText = negativeButtonText;
    }

    public void setNegativeButtonText(int negativeButtonTextResId) {
        setNegativeButtonText(getContext().getString(negativeButtonTextResId));
    }

    public CharSequence getNegativeButtonText() {
        return negativeButtonText;
    }

    public void setDialogLayoutResource(int dialogLayoutResId) {
        this.dialogLayoutResId = dialogLayoutResId;
    }

    public int getDialogLayoutResource() {
        return dialogLayoutResId;
    }

    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
    }

    @Override
    protected void onClick() {
        if (dialog != null && dialog.isShowing()) {
            return;
        }
        showDialog(null);
    }

    protected void showDialog(Bundle state) {
        Context context = getContext();
        whichButtonClicked = DialogInterface.BUTTON_NEGATIVE;
        builder = new AlertDialog.Builder(context)
                .setTitle(getDialogTitle())
                .setIcon(dialogIcon)
                .setPositiveButton(positiveButtonText, this)
                .setNegativeButton(negativeButtonText, this);

        View contentView = onCreateDialogView();
        if (contentView != null) {
            onBindDialogView(contentView);
            builder.setView(contentView);
        } else {
            builder.setMessage(dialogMessage);
        }

        onPrepareDialogBuilder(builder);
        if (getPreferenceManager() != null) {
            getPreferenceManager().registerOnActivityDestroyListener(this);
        }

        final Dialog d = dialog = builder.create();
        if (state != null) {
            d.onRestoreInstanceState(state);
        }
        d.setOnDismissListener(this);
        d.show();
    }

    protected View onCreateDialogView() {
        if (dialogLayoutResId == 0) {
            return null;
        }
        LayoutInflater inflater = LayoutInflater.from(builder != null ? builder.getContext() : getContext());
        return inflater.inflate(dialogLayoutResId, null);
    }

    protected void onBindDialogView(View view) {
        View dialogMessageView = view.findViewById(0x0102000b);
        if (dialogMessageView != null) {
            final CharSequence message = getDialogMessage();
            int newVisibility = View.GONE;
            if (!TextUtils.isEmpty(message)) {
                if (dialogMessageView instanceof TextView) {
                    ((TextView) dialogMessageView).setText(message);
                }
                newVisibility = View.VISIBLE;
            }
            if (dialogMessageView.getVisibility() != newVisibility) {
                dialogMessageView.setVisibility(newVisibility);
            }
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        whichButtonClicked = which;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (getPreferenceManager() != null) {
            getPreferenceManager().unregisterOnActivityDestroyListener(this);
        }
        this.dialog = null;
        onDialogClosed(whichButtonClicked == DialogInterface.BUTTON_POSITIVE);
    }

    protected void onDialogClosed(boolean positiveResult) {
    }

    public Dialog getDialog() {
        return dialog;
    }

    @Override
    public void onActivityDestroy() {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        dialog.dismiss();
    }

    private static final class SavedState extends BaseSavedState {
        boolean isDialogShowing;
        Bundle dialogBundle;

        SavedState(Parcel source) {
            super(source);
            isDialogShowing = source.readInt() == 1;
            dialogBundle = source.readBundle();
        }

        SavedState(Parcelable superState) {
            super(superState);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (dialog == null || !dialog.isShowing()) {
            return superState;
        }
        final SavedState myState = new SavedState(superState);
        myState.isDialogShowing = true;
        myState.dialogBundle = dialog.onSaveInstanceState();
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
        if (myState.isDialogShowing) {
            showDialog(myState.dialogBundle);
        }
    }
}
