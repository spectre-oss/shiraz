// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Insets;

public interface WindowInsetsAnimationController {
    Insets getHiddenStateInsets();
    Insets getShownStateInsets();
    Insets getCurrentInsets();
    float getCurrentFraction();
    float getCurrentAlpha();
    int getTypes();
    void setInsetsAndAlpha(Insets insets, float alpha, float fraction);
    void finish(boolean shown);

    default boolean isReady() {
        return !isFinished() && !isCancelled();
    }

    boolean isFinished();
    boolean isCancelled();
}
