// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Checkable;

/** {@link Preference} providing checkbox widget state binding and boolean persistence. */
public class CheckBoxPreference extends TwoStatePreference {
    public CheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CheckBoxPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0x0101008f);
    }

    public CheckBoxPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        View checkboxView = view.findViewById(0x01020001);
        if (checkboxView == null) {
            View frame = view.findViewById(0x01020018);
            if (frame instanceof ViewGroup) {
                CheckBox cb = new CheckBox(getContext());
                cb.setId(0x01020001);
                cb.setFocusable(false);
                cb.setClickable(false);
                ((ViewGroup) frame).addView(cb);
                frame.setVisibility(View.VISIBLE);
                checkboxView = cb;
            }
        }
        if (checkboxView instanceof Checkable) {
            ((Checkable) checkboxView).setChecked(checked);
        }
        syncSummaryView(view);
    }
}
