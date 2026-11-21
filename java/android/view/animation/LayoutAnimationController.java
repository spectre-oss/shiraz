// SPDX-License-Identifier: GPL-3.0-only

package android.view.animation;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

public class LayoutAnimationController {
    public static final int ORDER_NORMAL = 0;
    public static final int ORDER_REVERSE = 1;
    public static final int ORDER_RANDOM = 2;

    protected Animation mAnimation;
    protected Interpolator mInterpolator;
    protected Random mRandomizer;

    private float mDelay;
    private int mOrder = ORDER_NORMAL;
    private long mDuration;
    private long mMaxDelay;

    public LayoutAnimationController(Context context, AttributeSet attrs) {
        mDelay = 0.5f;
        mOrder = ORDER_NORMAL;
    }

    public LayoutAnimationController(Animation animation) {
        this(animation, 0.5f);
    }

    public LayoutAnimationController(Animation animation, float delay) {
        mDelay = delay;
        setAnimation(animation);
    }

    public int getOrder() {
        return mOrder;
    }

    public void setOrder(int order) {
        mOrder = order;
    }

    public void setAnimation(Context context, int resourceID) {
        setAnimation(AnimationUtils.loadAnimation(context, resourceID));
    }

    public void setAnimation(Animation animation) {
        mAnimation = animation;
        if (mAnimation != null) {
            mAnimation.setFillBefore(true);
        }
    }

    public Animation getAnimation() {
        return mAnimation;
    }

    public void setInterpolator(Context context, int resourceID) {
        setInterpolator(AnimationUtils.loadInterpolator(context, resourceID));
    }

    public void setInterpolator(Interpolator interpolator) {
        mInterpolator = interpolator;
    }

    public Interpolator getInterpolator() {
        return mInterpolator;
    }

    public float getDelay() {
        return mDelay;
    }

    public void setDelay(float delay) {
        mDelay = delay;
    }

    public boolean willOverlap() {
        return mDelay < 1.0f;
    }

    public void start() {
        if (mAnimation != null) {
            mDuration = mAnimation.getDuration();
            mMaxDelay = Long.MIN_VALUE;
            mAnimation.setStartTime(-1L);
        }
    }

    public final Animation getAnimationForView(View view) {
        if (mAnimation == null) {
            return null;
        }
        long delay = getDelayForView(view) + mAnimation.getStartOffset();
        if (delay > mMaxDelay) {
            mMaxDelay = delay;
        }
        try {
            Animation anim = mAnimation.clone();
            anim.setStartOffset(delay);
            return anim;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    public boolean isDone() {
        if (mAnimation == null) {
            return true;
        }
        return AnimationUtils.currentAnimationTimeMillis()
                > mAnimation.getStartTime() + mMaxDelay + mDuration;
    }

    protected long getDelayForView(View view) {
        return 0L;
    }

    protected int getTransformedIndex(AnimationParameters params) {
        if (params == null || params.count <= 0) {
            return 0;
        }
        switch (getOrder()) {
            case ORDER_REVERSE:
                return params.count - 1 - params.index;
            case ORDER_RANDOM:
                if (mRandomizer == null) {
                    mRandomizer = new Random();
                }
                return (int) (params.count * mRandomizer.nextFloat());
            case ORDER_NORMAL:
            default:
                return params.index;
        }
    }

    public static class AnimationParameters {
        public int count;
        public int index;

        public AnimationParameters() {
        }
    }
}
