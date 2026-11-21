// SPDX-License-Identifier: GPL-3.0-only
package android.window;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import java.time.Duration;
import java.time.Instant;

public final class SplashScreenView extends FrameLayout {
    private View mIconView;
    private Instant mIconAnimationStart;
    private Duration mIconAnimationDuration;

    SplashScreenView(Context context) {
        super(context);
    }

    SplashScreenView(
            Context context,
            View iconView,
            Instant iconAnimationStart,
            Duration iconAnimationDuration) {
        super(context);
        mIconView = iconView;
        mIconAnimationStart = iconAnimationStart;
        mIconAnimationDuration = iconAnimationDuration;
        if (iconView != null) {
            addView(iconView);
        }
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
    }

    public Duration getIconAnimationDuration() {
        return mIconAnimationDuration;
    }

    public Instant getIconAnimationStart() {
        return mIconAnimationStart;
    }

    public void remove() {
        setVisibility(GONE);
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
    }

    public View getIconView() {
        return mIconView;
    }
}
