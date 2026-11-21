// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.res.Resources;

/** Extension of SpinnerAdapter supporting a distinct Theme for drop-down views. */
public interface ThemedSpinnerAdapter extends SpinnerAdapter {
    void setDropDownViewTheme(Resources.Theme theme);

    Resources.Theme getDropDownViewTheme();
}
