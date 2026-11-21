// SPDX-License-Identifier: GPL-3.0-only
package android.media.effect;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EffectFactory {
    public static final String EFFECT_AUTOFIX = "android.media.effect.effects.AutoFixEffect";
    public static final String EFFECT_BACKDROPPER = "android.media.effect.effects.BackDropperEffect";
    public static final String EFFECT_BITMAPOVERLAY =
            "android.media.effect.effects.BitmapOverlayEffect";
    public static final String EFFECT_BLACKWHITE = "android.media.effect.effects.BlackWhiteEffect";
    public static final String EFFECT_BRIGHTNESS = "android.media.effect.effects.BrightnessEffect";
    public static final String EFFECT_CONTRAST = "android.media.effect.effects.ContrastEffect";
    public static final String EFFECT_CROP = "android.media.effect.effects.CropEffect";
    public static final String EFFECT_CROSSPROCESS =
            "android.media.effect.effects.CrossProcessEffect";
    public static final String EFFECT_DOCUMENTARY =
            "android.media.effect.effects.DocumentaryEffect";
    public static final String EFFECT_DUOTONE = "android.media.effect.effects.DuotoneEffect";
    public static final String EFFECT_FILLLIGHT = "android.media.effect.effects.FillLightEffect";
    public static final String EFFECT_FISHEYE = "android.media.effect.effects.FisheyeEffect";
    public static final String EFFECT_FLIP = "android.media.effect.effects.FlipEffect";
    public static final String EFFECT_GRAIN = "android.media.effect.effects.GrainEffect";
    public static final String EFFECT_GRAYSCALE = "android.media.effect.effects.GrayscaleEffect";
    public static final String EFFECT_LOMOISH = "android.media.effect.effects.LomoishEffect";
    public static final String EFFECT_NEGATIVE = "android.media.effect.effects.NegativeEffect";
    public static final String EFFECT_POSTERIZE = "android.media.effect.effects.PosterizeEffect";
    public static final String EFFECT_REDEYE = "android.media.effect.effects.RedEyeEffect";
    public static final String EFFECT_ROTATE = "android.media.effect.effects.RotateEffect";
    public static final String EFFECT_SATURATE = "android.media.effect.effects.SaturateEffect";
    public static final String EFFECT_SEPIA = "android.media.effect.effects.SepiaEffect";
    public static final String EFFECT_SHARPEN = "android.media.effect.effects.SharpenEffect";
    public static final String EFFECT_STRAIGHTEN = "android.media.effect.effects.StraightenEffect";
    public static final String EFFECT_TEMPERATURE =
            "android.media.effect.effects.ColorTemperatureEffect";
    public static final String EFFECT_TINT = "android.media.effect.effects.TintEffect";
    public static final String EFFECT_VIGNETTE = "android.media.effect.effects.VignetteEffect";

    private static final Set<String> SUPPORTED_EFFECTS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    EFFECT_AUTOFIX, EFFECT_BACKDROPPER, EFFECT_BITMAPOVERLAY,
                    EFFECT_BLACKWHITE, EFFECT_BRIGHTNESS, EFFECT_CONTRAST,
                    EFFECT_CROP, EFFECT_CROSSPROCESS, EFFECT_DOCUMENTARY,
                    EFFECT_DUOTONE, EFFECT_FILLLIGHT, EFFECT_FISHEYE,
                    EFFECT_FLIP, EFFECT_GRAIN, EFFECT_GRAYSCALE,
                    EFFECT_LOMOISH, EFFECT_NEGATIVE, EFFECT_POSTERIZE,
                    EFFECT_REDEYE, EFFECT_ROTATE, EFFECT_SATURATE,
                    EFFECT_SEPIA, EFFECT_SHARPEN, EFFECT_STRAIGHTEN,
                    EFFECT_TEMPERATURE, EFFECT_TINT, EFFECT_VIGNETTE)));

    private final EffectContext mEffectContext;

    EffectFactory(EffectContext effectContext) {
        mEffectContext = effectContext;
    }

    public Effect createEffect(String effectName) {
        if (!isEffectSupported(effectName)) {
            throw new IllegalArgumentException(
                    "Cannot instantiate unknown effect '" + effectName + "'!");
        }
        mEffectContext.checkNotReleased();
        return new HostEffect(effectName);
    }

    public static boolean isEffectSupported(String effectName) {
        return effectName != null && SUPPORTED_EFFECTS.contains(effectName);
    }

    private static final class HostEffect extends Effect {
        private final String mName;
        private final Map<String, Object> mParams = new HashMap<String, Object>();
        private boolean mReleased;

        HostEffect(String name) {
            mName = name;
        }

        public String getName() {
            return mName;
        }

        public void apply(int inputTexId, int width, int height, int outputTexId) {
            if (mReleased) {
                throw new IllegalStateException("Effect has been released");
            }
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Invalid texture dimensions");
            }
            EffectUpdateListener listener = mUpdateListener;
            if (listener != null) {
                listener.onEffectUpdated(this, null);
            }
        }

        public void setParameter(String parameterKey, Object value) {
            if (mReleased) {
                throw new IllegalStateException("Effect has been released");
            }
            if (parameterKey == null) {
                throw new IllegalArgumentException("parameterKey cannot be null");
            }
            mParams.put(parameterKey, value);
        }

        public void release() {
            mReleased = true;
            mParams.clear();
        }
    }
}
