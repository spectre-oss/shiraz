// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;
import java.util.Objects;

/** Metadata for a launcher activity preserved when an application is archived. */
public final class ArchivedActivityInfo {
    private CharSequence mLabel;
    private ComponentName mComponentName;
    private Drawable mIcon;
    private Drawable mMonochromeIcon;

    public ArchivedActivityInfo(CharSequence label, ComponentName componentName) {
        mLabel = Objects.requireNonNull(label);
        mComponentName = Objects.requireNonNull(componentName);
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public Drawable getMonochromeIcon() {
        return mMonochromeIcon;
    }

    public ArchivedActivityInfo setLabel(CharSequence value) {
        mLabel = Objects.requireNonNull(value);
        return this;
    }

    public ArchivedActivityInfo setComponentName(ComponentName value) {
        mComponentName = Objects.requireNonNull(value);
        return this;
    }

    public ArchivedActivityInfo setIcon(Drawable value) {
        mIcon = value;
        return this;
    }

    public ArchivedActivityInfo setMonochromeIcon(Drawable value) {
        mMonochromeIcon = value;
        return this;
    }
}
