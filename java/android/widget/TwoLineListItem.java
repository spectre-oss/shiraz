// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.util.AttributeSet;

/** Legacy RelativeLayout list item holding primary text1 and secondary text2 TextViews. */
@Deprecated
public class TwoLineListItem extends RelativeLayout {
    private TextView mText1;
    private TextView mText2;

    public TwoLineListItem(Context context) {
        this(context, null, 0);
    }

    public TwoLineListItem(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TwoLineListItem(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TwoLineListItem(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mText1 = findViewById(0x01020014);
        mText2 = findViewById(0x01020015);
    }

    public TextView getText1() {
        return mText1;
    }

    public TextView getText2() {
        return mText2;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return TwoLineListItem.class.getName();
    }
}
