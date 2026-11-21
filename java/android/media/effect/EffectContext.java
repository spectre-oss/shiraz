// SPDX-License-Identifier: GPL-3.0-only
package android.media.effect;

public class EffectContext {
    private final EffectFactory mFactory;
    private boolean mReleased;

    private EffectContext() {
        mFactory = new EffectFactory(this);
    }

    public static EffectContext createWithCurrentGlContext() {
        return new EffectContext();
    }

    public EffectFactory getFactory() {
        checkNotReleased();
        return mFactory;
    }

    public void release() {
        mReleased = true;
    }

    void checkNotReleased() {
        if (mReleased) {
            throw new IllegalStateException("EffectContext has been released");
        }
    }
}
