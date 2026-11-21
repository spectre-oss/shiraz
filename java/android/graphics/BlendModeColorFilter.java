// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

public final class BlendModeColorFilter extends ColorFilter {
    final int mColor;
    final BlendMode mMode;

    public BlendModeColorFilter(int color, BlendMode mode) {
        if (mode == null) {
            throw new NullPointerException("mode must not be null");
        }
        mColor = color;
        mMode = mode;
    }

    public int getColor() {
        return mColor;
    }

    public BlendMode getMode() {
        return mMode;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        BlendModeColorFilter other = (BlendModeColorFilter) object;
        return other.mColor == mColor && other.mMode == mMode;
    }

    @Override
    public int hashCode() {
        return 31 * mMode.hashCode() + mColor;
    }
}
