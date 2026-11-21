// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.CompoundButton;

/** {@link Preference} providing two-state switch toggle binding and on/off labels. */
public class SwitchPreference extends TwoStatePreference {
    private CharSequence switchOn;
    private CharSequence switchOff;
    private final CompoundButton.OnCheckedChangeListener listener =
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (!callChangeListener(Boolean.valueOf(isChecked))) {
                        buttonView.setChecked(!isChecked);
                        return;
                    }
                    SwitchPreference.this.setChecked(isChecked);
                }
            };

    public SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs != null || defStyleAttr != 0 || defStyleRes != 0) {
            shiraz.runtime.XmlAttributes x =
                    new shiraz.runtime.XmlAttributes(context, attrs, defStyleAttr, defStyleRes);
            switchOn = x.text("switchTextOn");
            switchOff = x.text("switchTextOff");
        }
    }

    public SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SwitchPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0x0101036d);
    }

    public SwitchPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        View checkableView = view.findViewById(0x01020040);
        if (checkableView == null) {
            View frame = view.findViewById(0x01020018);
            if (frame instanceof ViewGroup) {
                CheckBox toggle = new CheckBox(getContext());
                toggle.setId(0x01020040);
                toggle.setFocusable(false);
                toggle.setClickable(false);
                ((ViewGroup) frame).addView(toggle);
                frame.setVisibility(View.VISIBLE);
                checkableView = toggle;
            }
        }
        if (checkableView instanceof CompoundButton) {
            ((CompoundButton) checkableView).setOnCheckedChangeListener(null);
        }
        if (checkableView instanceof Checkable) {
            ((Checkable) checkableView).setChecked(checked);
        }
        if (checkableView instanceof CompoundButton) {
            CompoundButton button = (CompoundButton) checkableView;
            CharSequence label = checked ? switchOn : switchOff;
            if (label != null) {
                button.setText(label);
            }
            button.setOnCheckedChangeListener(listener);
        }
        syncSummaryView(view);
    }

    public void setSwitchTextOn(CharSequence onText) {
        switchOn = onText;
        notifyChanged();
    }

    public void setSwitchTextOff(CharSequence offText) {
        switchOff = offText;
        notifyChanged();
    }

    public void setSwitchTextOn(int resId) {
        setSwitchTextOn(getContext().getString(resId));
    }

    public void setSwitchTextOff(int resId) {
        setSwitchTextOff(getContext().getString(resId));
    }

    public CharSequence getSwitchTextOn() {
        return switchOn;
    }

    public CharSequence getSwitchTextOff() {
        return switchOff;
    }
}
