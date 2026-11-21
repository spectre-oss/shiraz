// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import java.util.Objects;
import java.util.function.Supplier;

public class DevicePolicyResourcesManager {
    public DevicePolicyResourcesManager() {
    }

    public Drawable getDrawable(
            String drawableId,
            String drawableStyle,
            Supplier<Drawable> defaultDrawableLoader) {
        Objects.requireNonNull(drawableId, "drawableId can't be null");
        Objects.requireNonNull(drawableStyle, "drawableStyle can't be null");
        Objects.requireNonNull(defaultDrawableLoader, "defaultDrawableLoader can't be null");
        return defaultDrawableLoader.get();
    }

    public Drawable getDrawable(
            String drawableId,
            String drawableStyle,
            String drawableSource,
            Supplier<Drawable> defaultDrawableLoader) {
        Objects.requireNonNull(drawableId, "drawableId can't be null");
        Objects.requireNonNull(drawableStyle, "drawableStyle can't be null");
        Objects.requireNonNull(drawableSource, "drawableSource can't be null");
        Objects.requireNonNull(defaultDrawableLoader, "defaultDrawableLoader can't be null");
        return defaultDrawableLoader.get();
    }

    public Drawable getDrawableForDensity(
            String drawableId,
            String drawableStyle,
            int density,
            Supplier<Drawable> defaultDrawableLoader) {
        Objects.requireNonNull(drawableId, "drawableId can't be null");
        Objects.requireNonNull(drawableStyle, "drawableStyle can't be null");
        Objects.requireNonNull(defaultDrawableLoader, "defaultDrawableLoader can't be null");
        return defaultDrawableLoader.get();
    }

    public Drawable getDrawableForDensity(
            String drawableId,
            String drawableStyle,
            String drawableSource,
            int density,
            Supplier<Drawable> defaultDrawableLoader) {
        Objects.requireNonNull(drawableId, "drawableId can't be null");
        Objects.requireNonNull(drawableStyle, "drawableStyle can't be null");
        Objects.requireNonNull(drawableSource, "drawableSource can't be null");
        Objects.requireNonNull(defaultDrawableLoader, "defaultDrawableLoader can't be null");
        return defaultDrawableLoader.get();
    }

    public Icon getDrawableAsIcon(
            String drawableId,
            String drawableStyle,
            String drawableSource,
            Icon defaultIcon) {
        Objects.requireNonNull(drawableId, "drawableId can't be null");
        Objects.requireNonNull(drawableStyle, "drawableStyle can't be null");
        Objects.requireNonNull(drawableSource, "drawableSource can't be null");
        Objects.requireNonNull(defaultIcon, "defaultIcon can't be null");
        return defaultIcon;
    }

    public Icon getDrawableAsIcon(
            String drawableId,
            String drawableStyle,
            Icon defaultIcon) {
        Objects.requireNonNull(drawableId, "drawableId can't be null");
        Objects.requireNonNull(drawableStyle, "drawableStyle can't be null");
        Objects.requireNonNull(defaultIcon, "defaultIcon can't be null");
        return defaultIcon;
    }

    public String getString(String stringId, Supplier<String> defaultStringLoader) {
        Objects.requireNonNull(stringId, "stringId can't be null");
        Objects.requireNonNull(defaultStringLoader, "defaultStringLoader can't be null");
        return defaultStringLoader.get();
    }

    public String getString(
            String stringId, Supplier<String> defaultStringLoader, Object... formatArgs) {
        Objects.requireNonNull(stringId, "stringId can't be null");
        Objects.requireNonNull(defaultStringLoader, "defaultStringLoader can't be null");
        String raw = defaultStringLoader.get();
        if (raw == null || formatArgs == null || formatArgs.length == 0) {
            return raw;
        }
        return String.format(raw, formatArgs);
    }
}
