// SPDX-License-Identifier: GPL-3.0-only
package android.media.effect;

public abstract class Effect {
    EffectUpdateListener mUpdateListener;

    public Effect() {
    }

    public abstract String getName();

    public abstract void apply(int inputTexId, int width, int height, int outputTexId);

    public abstract void setParameter(String parameterKey, Object value);

    public void setUpdateListener(EffectUpdateListener listener) {
        mUpdateListener = listener;
    }

    public abstract void release();
}
