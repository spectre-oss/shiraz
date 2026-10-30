// SPDX-License-Identifier: GPL-3.0-only
package android.app;

/** Native action-bar contract. Host windows currently have no native action bar. */
public abstract class ActionBar {
    public abstract void show();
    public abstract void hide();
    public abstract boolean isShowing();
}
