// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.content.res.Resources;
import android.view.Display;
import java.util.Objects;

public class Presentation extends Dialog {
    private final Display mDisplay;

    public Presentation(Context outerContext, Display display) {
        this(outerContext, display, 0);
    }

    public Presentation(Context outerContext, Display display, int theme) {
        super(Objects.requireNonNull(outerContext, "outerContext"), theme);
        mDisplay = Objects.requireNonNull(display, "display");
    }

    public Display getDisplay() {
        return mDisplay;
    }

    public Resources getResources() {
        return getContext().getResources();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void show() {
        super.show();
    }

    public void onDisplayRemoved() {
    }

    public void onDisplayChanged() {
    }
}
