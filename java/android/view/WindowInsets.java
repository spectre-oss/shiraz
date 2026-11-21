// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Insets;
import android.graphics.Rect;
import android.util.Size;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable legacy and API 35 inset values. */
public final class WindowInsets {
    private static final int TYPE_COUNT = 10;

    private final Insets system, stable, decor, gestures, mandatory, tappable;
    private final boolean systemConsumed, stableConsumed, decorConsumed, cutoutConsumed, round, alwaysConsumeNavBar;
    private final DisplayCutout cutout;
    private final Insets[] typeInsetsMap;
    private final Insets[] typeMaxInsetsMap;
    private final boolean[] typeVisibilityMap;
    private final RoundedCorner[] roundedCorners;
    private final Rect privacyIndicatorBounds;
    private final DisplayShape displayShape;
    private final List<Rect>[] typeBoundingRectsMap;
    private final List<Rect>[] typeMaxBoundingRectsMap;
    private final int frameWidth;
    private final int frameHeight;

    public static final WindowInsets CONSUMED = new WindowInsets(null, null, null, false, false);

    public WindowInsets(Rect system) {
        this(system, null, null, false, false);
    }

    @SuppressWarnings("unchecked")
    public WindowInsets(Rect system, Rect decor, Rect stable, boolean round, boolean nav) {
        this.system = Insets.of(system);
        this.decor = Insets.of(decor);
        this.stable = Insets.of(stable);
        this.gestures = Insets.NONE;
        this.mandatory = Insets.NONE;
        this.tappable = Insets.NONE;
        this.cutout = null;
        this.cutoutConsumed = true;
        this.systemConsumed = system == null;
        this.decorConsumed = decor == null;
        this.stableConsumed = stable == null;
        this.round = round;
        this.alwaysConsumeNavBar = nav;
        this.typeInsetsMap = createDefaultInsetsMap(this.system, this.gestures, this.mandatory,
                this.tappable, null);
        this.typeMaxInsetsMap = createDefaultInsetsMap(this.stable.equals(Insets.NONE) ? this.system : this.stable,
                this.gestures, this.mandatory, this.tappable, null);
        this.typeVisibilityMap = createDefaultVisibilityMap();
        this.roundedCorners = new RoundedCorner[4];
        this.privacyIndicatorBounds = null;
        this.displayShape = null;
        this.typeBoundingRectsMap = (List<Rect>[]) new List<?>[TYPE_COUNT];
        this.typeMaxBoundingRectsMap = (List<Rect>[]) new List<?>[TYPE_COUNT];
        this.frameWidth = 0;
        this.frameHeight = 0;
    }

    @SuppressWarnings("unchecked")
    private WindowInsets(Builder b) {
        this.cutout = b.cutout;
        this.round = b.round;
        this.alwaysConsumeNavBar = b.nav;
        this.systemConsumed = b.systemConsumed;
        this.stableConsumed = b.stableConsumed;
        this.decorConsumed = b.decorConsumed;
        this.cutoutConsumed = b.cutoutConsumed;

        this.typeInsetsMap = b.typeInsetsMap.clone();
        this.typeMaxInsetsMap = b.typeMaxInsetsMap.clone();
        this.typeVisibilityMap = b.typeVisibilityMap.clone();

        if (b.cutout != null && !b.cutoutConsumed) {
            Insets cutoutInsets = Insets.of(b.cutout.getSafeInsetLeft(), b.cutout.getSafeInsetTop(),
                    b.cutout.getSafeInsetRight(), b.cutout.getSafeInsetBottom());
            int idx = Type.indexOf(Type.DISPLAY_CUTOUT);
            if (this.typeInsetsMap[idx].equals(Insets.NONE)) {
                this.typeInsetsMap[idx] = cutoutInsets;
            }
            if (this.typeMaxInsetsMap[idx].equals(Insets.NONE)) {
                this.typeMaxInsetsMap[idx] = cutoutInsets;
            }
        }

        if (b.typesExplicitlySet && !b.legacySystemExplicitlySet) {
            this.system = b.systemConsumed ? Insets.NONE
                    : computeInsetsFromMap(this.typeInsetsMap,
                            Type.systemBars() | Type.IME | Type.DISPLAY_CUTOUT);
        } else {
            this.system = b.system;
        }
        this.stable = b.stable;
        this.decor = b.decor;
        this.gestures = b.systemConsumed && !b.typesExplicitlySet ? Insets.NONE
                : (b.typesExplicitlySet && !b.legacyGesturesExplicitlySet
                        ? this.typeInsetsMap[Type.indexOf(Type.SYSTEM_GESTURES)] : b.gestures);
        this.mandatory = b.systemConsumed && !b.typesExplicitlySet ? Insets.NONE
                : (b.typesExplicitlySet && !b.legacyMandatoryExplicitlySet
                        ? this.typeInsetsMap[Type.indexOf(Type.MANDATORY_SYSTEM_GESTURES)] : b.mandatory);
        this.tappable = b.systemConsumed && !b.typesExplicitlySet ? Insets.NONE
                : (b.typesExplicitlySet && !b.legacyTappableExplicitlySet
                        ? this.typeInsetsMap[Type.indexOf(Type.TAPPABLE_ELEMENT)] : b.tappable);

        this.roundedCorners = b.roundedCorners.clone();
        this.privacyIndicatorBounds = b.privacyIndicatorBounds != null
                ? new Rect(b.privacyIndicatorBounds) : null;
        this.displayShape = b.displayShape;
        this.typeBoundingRectsMap = b.typeBoundingRectsMap.clone();
        this.typeMaxBoundingRectsMap = b.typeMaxBoundingRectsMap.clone();
        this.frameWidth = b.frameWidth;
        this.frameHeight = b.frameHeight;
    }

    public WindowInsets(WindowInsets other) {
        this(new Builder(other));
    }

    private static Insets[] createDefaultInsetsMap(Insets system, Insets gestures,
            Insets mandatory, Insets tappable, DisplayCutout cutout) {
        Insets[] map = new Insets[TYPE_COUNT];
        Arrays.fill(map, Insets.NONE);
        if (!system.equals(Insets.NONE)) {
            map[Type.indexOf(Type.STATUS_BARS)] = Insets.of(0, system.top, 0, 0);
            map[Type.indexOf(Type.NAVIGATION_BARS)] = Insets.of(system.left, 0, system.right, system.bottom);
        }
        map[Type.indexOf(Type.SYSTEM_GESTURES)] = gestures;
        map[Type.indexOf(Type.MANDATORY_SYSTEM_GESTURES)] = mandatory;
        map[Type.indexOf(Type.TAPPABLE_ELEMENT)] = tappable;
        if (cutout != null) {
            map[Type.indexOf(Type.DISPLAY_CUTOUT)] = Insets.of(
                    cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                    cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
        }
        return map;
    }

    private static boolean[] createDefaultVisibilityMap() {
        boolean[] vis = new boolean[TYPE_COUNT];
        Arrays.fill(vis, true);
        vis[Type.indexOf(Type.IME)] = false;
        return vis;
    }

    private static Insets computeInsetsFromMap(Insets[] map, int typeMask) {
        Insets result = Insets.NONE;
        for (int i = 0; i < TYPE_COUNT; i++) {
            int bit = 1 << i;
            if ((typeMask & bit) != 0) {
                Insets cur = map[i];
                if (cur != null && !cur.equals(Insets.NONE)) {
                    result = Insets.max(result, cur);
                }
            }
        }
        return result;
    }

    public Insets getInsets(int typeMask) {
        return computeInsetsFromMap(typeInsetsMap, typeMask);
    }

    public Insets getInsetsIgnoringVisibility(int typeMask) {
        if ((typeMask & Type.IME) != 0) {
            throw new IllegalArgumentException("Unable to query the maximum insets for IME");
        }
        return computeInsetsFromMap(typeMaxInsetsMap, typeMask);
    }

    public boolean isVisible(int typeMask) {
        for (int i = 0; i < TYPE_COUNT; i++) {
            int bit = 1 << i;
            if ((typeMask & bit) != 0 && !typeVisibilityMap[i]) {
                return false;
            }
        }
        return true;
    }

    public List<Rect> getBoundingRects(int typeMask) {
        ArrayList<Rect> all = null;
        for (int i = 0; i < TYPE_COUNT; i++) {
            if ((typeMask & (1 << i)) != 0) {
                List<Rect> rects = typeBoundingRectsMap[i];
                if (rects != null && !rects.isEmpty()) {
                    if (all == null) all = new ArrayList<Rect>();
                    for (int j = 0; j < rects.size(); j++) {
                        all.add(new Rect(rects.get(j)));
                    }
                }
            }
        }
        return all != null ? all : Collections.<Rect>emptyList();
    }

    public List<Rect> getBoundingRectsIgnoringVisibility(int typeMask) {
        if ((typeMask & Type.IME) != 0) {
            throw new IllegalArgumentException("Unable to query the bounding rects for IME");
        }
        ArrayList<Rect> all = null;
        for (int i = 0; i < TYPE_COUNT; i++) {
            if ((typeMask & (1 << i)) != 0) {
                List<Rect> rects = typeMaxBoundingRectsMap[i];
                if (rects == null) rects = typeBoundingRectsMap[i];
                if (rects != null && !rects.isEmpty()) {
                    if (all == null) all = new ArrayList<Rect>();
                    for (int j = 0; j < rects.size(); j++) {
                        all.add(new Rect(rects.get(j)));
                    }
                }
            }
        }
        return all != null ? all : Collections.<Rect>emptyList();
    }

    public RoundedCorner getRoundedCorner(int position) {
        if (position < 0 || position >= 4) {
            return null;
        }
        return roundedCorners[position];
    }

    public Rect getPrivacyIndicatorBounds() {
        return privacyIndicatorBounds != null ? new Rect(privacyIndicatorBounds) : null;
    }

    public DisplayShape getDisplayShape() {
        return displayShape != null ? displayShape : DisplayShape.NONE;
    }

    public Size getFrame() {
        return new Size(frameWidth, frameHeight);
    }

    @Deprecated public int getSystemWindowInsetLeft() { return system.left; }
    @Deprecated public int getSystemWindowInsetTop() { return system.top; }
    @Deprecated public int getSystemWindowInsetRight() { return system.right; }
    @Deprecated public int getSystemWindowInsetBottom() { return system.bottom; }
    @Deprecated public int getStableInsetLeft() { return stable.left; }
    @Deprecated public int getStableInsetTop() { return stable.top; }
    @Deprecated public int getStableInsetRight() { return stable.right; }
    @Deprecated public int getStableInsetBottom() { return stable.bottom; }
    @Deprecated public Insets getSystemWindowInsets() { return system; }
    @Deprecated public Insets getStableInsets() { return stable; }
    @Deprecated public Insets getSystemGestureInsets() { return gestures; }
    @Deprecated public Insets getMandatorySystemGestureInsets() { return mandatory; }
    @Deprecated public Insets getTappableElementInsets() { return tappable; }
    public DisplayCutout getDisplayCutout() { return cutout; }
    @Deprecated public boolean hasSystemWindowInsets() { return !system.equals(Insets.NONE); }
    @Deprecated public boolean hasStableInsets() { return !stable.equals(Insets.NONE); }

    public boolean hasInsets() {
        return hasSystemWindowInsets()
                || hasStableInsets()
                || !decor.equals(Insets.NONE)
                || !gestures.equals(Insets.NONE)
                || !mandatory.equals(Insets.NONE)
                || !tappable.equals(Insets.NONE)
                || !computeInsetsFromMap(typeInsetsMap, Type.ALL).equals(Insets.NONE)
                || cutout != null;
    }

    public boolean isConsumed() {
        return systemConsumed && stableConsumed && decorConsumed && cutoutConsumed;
    }

    public boolean isRound() { return round; }
    public boolean shouldAlwaysConsumeNavBar() { return alwaysConsumeNavBar; }
    public boolean shouldAlwaysConsumeSystemBars() { return alwaysConsumeNavBar; }

    @Deprecated
    public WindowInsets consumeSystemWindowInsets() {
        Builder b = new Builder(this);
        b.system = Insets.NONE;
        b.systemConsumed = true;
        int clearMask = Type.systemBars() | Type.IME;
        for (int i = 0; i < TYPE_COUNT; i++) {
            if ((clearMask & (1 << i)) != 0) {
                b.typeInsetsMap[i] = Insets.NONE;
            }
        }
        return b.build();
    }

    @Deprecated
    public WindowInsets consumeStableInsets() {
        Builder b = new Builder(this);
        b.stable = Insets.NONE;
        b.stableConsumed = true;
        return b.build();
    }

    @Deprecated
    public WindowInsets consumeDisplayCutout() {
        Builder b = new Builder(this);
        b.cutout = null;
        b.cutoutConsumed = true;
        b.typeInsetsMap[Type.indexOf(Type.DISPLAY_CUTOUT)] = Insets.NONE;
        return b.build();
    }

    @Deprecated
    public WindowInsets replaceSystemWindowInsets(int l, int t, int r, int bottom) {
        if (systemConsumed && android.os.Build.VERSION.SDK_INT >= 29) return this;
        Builder b = new Builder(this);
        b.setSystemWindowInsets(Insets.of(l, t, r, bottom));
        b.systemConsumed = this.systemConsumed;
        return b.build();
    }

    @Deprecated
    public WindowInsets replaceSystemWindowInsets(Rect value) {
        if (value == null) throw new NullPointerException("insets");
        return replaceSystemWindowInsets(value.left, value.top, value.right, value.bottom);
    }

    static Insets insetInsets(Insets value, int l, int t, int r, int b) {
        return Insets.of(
                Math.max(0, value.left - l),
                Math.max(0, value.top - t),
                Math.max(0, value.right - r),
                Math.max(0, value.bottom - b));
    }

    public WindowInsets inset(int l, int t, int r, int bottom) {
        if (l < 0 || t < 0 || r < 0 || bottom < 0) {
            throw new IllegalArgumentException("Negative inset");
        }
        Builder b = new Builder(this);
        b.system = insetInsets(system, l, t, r, bottom);
        b.stable = insetInsets(stable, l, t, r, bottom);
        b.decor = insetInsets(decor, l, t, r, bottom);
        b.gestures = insetInsets(gestures, l, t, r, bottom);
        b.mandatory = insetInsets(mandatory, l, t, r, bottom);
        b.tappable = insetInsets(tappable, l, t, r, bottom);
        for (int i = 0; i < TYPE_COUNT; i++) {
            b.typeInsetsMap[i] = insetInsets(b.typeInsetsMap[i], l, t, r, bottom);
            b.typeMaxInsetsMap[i] = insetInsets(b.typeMaxInsetsMap[i], l, t, r, bottom);
        }
        if (cutout != null) {
            b.cutout = cutout.shirazInset(l, t, r, bottom);
        }
        if (frameWidth > 0 || frameHeight > 0) {
            b.frameWidth = Math.max(0, frameWidth - l - r);
            b.frameHeight = Math.max(0, frameHeight - t - bottom);
        }
        return b.build();
    }

    public WindowInsets inset(Insets insets) {
        return inset(insets.left, insets.top, insets.right, insets.bottom);
    }

    @Override
    public String toString() {
        return "WindowInsets{systemWindowInsets=" + system
                + " stableInsets=" + stable
                + " cutout=" + cutout
                + " round=" + round + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WindowInsets)) return false;
        WindowInsets w = (WindowInsets) o;
        return system.equals(w.system)
                && stable.equals(w.stable)
                && decor.equals(w.decor)
                && gestures.equals(w.gestures)
                && mandatory.equals(w.mandatory)
                && tappable.equals(w.tappable)
                && Objects.equals(cutout, w.cutout)
                && Arrays.equals(typeInsetsMap, w.typeInsetsMap)
                && Arrays.equals(typeMaxInsetsMap, w.typeMaxInsetsMap)
                && Arrays.equals(typeVisibilityMap, w.typeVisibilityMap)
                && Arrays.equals(roundedCorners, w.roundedCorners)
                && Objects.equals(privacyIndicatorBounds, w.privacyIndicatorBounds)
                && Objects.equals(displayShape, w.displayShape)
                && systemConsumed == w.systemConsumed
                && stableConsumed == w.stableConsumed
                && decorConsumed == w.decorConsumed
                && cutoutConsumed == w.cutoutConsumed
                && round == w.round
                && alwaysConsumeNavBar == w.alwaysConsumeNavBar
                && frameWidth == w.frameWidth
                && frameHeight == w.frameHeight;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(system, stable, decor, gestures, mandatory, tappable, cutout,
                privacyIndicatorBounds, displayShape, systemConsumed, stableConsumed,
                decorConsumed, cutoutConsumed, round, alwaysConsumeNavBar, frameWidth, frameHeight);
        result = 31 * result + Arrays.hashCode(typeInsetsMap);
        result = 31 * result + Arrays.hashCode(typeMaxInsetsMap);
        result = 31 * result + Arrays.hashCode(typeVisibilityMap);
        result = 31 * result + Arrays.hashCode(roundedCorners);
        return result;
    }

    public static final class Builder {
        private Insets system = Insets.NONE;
        private Insets stable = Insets.NONE;
        private Insets decor = Insets.NONE;
        private Insets gestures = Insets.NONE;
        private Insets mandatory = Insets.NONE;
        private Insets tappable = Insets.NONE;
        private boolean systemConsumed = true;
        private boolean stableConsumed = true;
        private boolean decorConsumed = true;
        private boolean cutoutConsumed = true;
        private boolean round;
        private boolean nav;
        private DisplayCutout cutout;
        private final Insets[] typeInsetsMap;
        private final Insets[] typeMaxInsetsMap;
        private final boolean[] typeVisibilityMap;
        private final boolean[] visibilityExplicitlySet = new boolean[TYPE_COUNT];
        private final RoundedCorner[] roundedCorners;
        private Rect privacyIndicatorBounds;
        private DisplayShape displayShape;
        private final List<Rect>[] typeBoundingRectsMap;
        private final List<Rect>[] typeMaxBoundingRectsMap;
        private int frameWidth;
        private int frameHeight;
        private boolean typesExplicitlySet;
        private boolean legacySystemExplicitlySet;
        private boolean legacyGesturesExplicitlySet;
        private boolean legacyMandatoryExplicitlySet;
        private boolean legacyTappableExplicitlySet;

        @SuppressWarnings("unchecked")
        public Builder() {
            typeInsetsMap = new Insets[TYPE_COUNT];
            Arrays.fill(typeInsetsMap, Insets.NONE);
            typeMaxInsetsMap = new Insets[TYPE_COUNT];
            Arrays.fill(typeMaxInsetsMap, Insets.NONE);
            typeVisibilityMap = createDefaultVisibilityMap();
            roundedCorners = new RoundedCorner[4];
            typeBoundingRectsMap = (List<Rect>[]) new List<?>[TYPE_COUNT];
            typeMaxBoundingRectsMap = (List<Rect>[]) new List<?>[TYPE_COUNT];
        }

        public Builder(WindowInsets w) {
            if (w == null) throw new NullPointerException("insets");
            system = w.system;
            stable = w.stable;
            decor = w.decor;
            gestures = w.gestures;
            mandatory = w.mandatory;
            tappable = w.tappable;
            systemConsumed = w.systemConsumed;
            stableConsumed = w.stableConsumed;
            decorConsumed = w.decorConsumed;
            cutoutConsumed = w.cutoutConsumed;
            round = w.round;
            nav = w.alwaysConsumeNavBar;
            cutout = w.cutout;
            typeInsetsMap = w.typeInsetsMap.clone();
            typeMaxInsetsMap = w.typeMaxInsetsMap.clone();
            typeVisibilityMap = w.typeVisibilityMap.clone();
            roundedCorners = w.roundedCorners.clone();
            privacyIndicatorBounds = w.privacyIndicatorBounds != null
                    ? new Rect(w.privacyIndicatorBounds) : null;
            displayShape = w.displayShape;
            typeBoundingRectsMap = w.typeBoundingRectsMap.clone();
            typeMaxBoundingRectsMap = w.typeMaxBoundingRectsMap.clone();
            frameWidth = w.frameWidth;
            frameHeight = w.frameHeight;
        }

        @Deprecated
        public Builder setSystemWindowInsets(Insets value) {
            system = Objects.requireNonNull(value);
            systemConsumed = false;
            legacySystemExplicitlySet = true;
            if (!typesExplicitlySet) {
                typeInsetsMap[Type.indexOf(Type.STATUS_BARS)] = Insets.of(0, value.top, 0, 0);
                typeInsetsMap[Type.indexOf(Type.NAVIGATION_BARS)] = Insets.of(value.left, 0, value.right, value.bottom);
                typeMaxInsetsMap[Type.indexOf(Type.STATUS_BARS)] = typeInsetsMap[Type.indexOf(Type.STATUS_BARS)];
                typeMaxInsetsMap[Type.indexOf(Type.NAVIGATION_BARS)] = typeInsetsMap[Type.indexOf(Type.NAVIGATION_BARS)];
            }
            return this;
        }

        @Deprecated
        public Builder setSystemGestureInsets(Insets value) {
            gestures = Objects.requireNonNull(value);
            legacyGesturesExplicitlySet = true;
            int idx = Type.indexOf(Type.SYSTEM_GESTURES);
            typeInsetsMap[idx] = value;
            typeMaxInsetsMap[idx] = value;
            return this;
        }

        @Deprecated
        public Builder setMandatorySystemGestureInsets(Insets value) {
            mandatory = Objects.requireNonNull(value);
            legacyMandatoryExplicitlySet = true;
            int idx = Type.indexOf(Type.MANDATORY_SYSTEM_GESTURES);
            typeInsetsMap[idx] = value;
            typeMaxInsetsMap[idx] = value;
            return this;
        }

        @Deprecated
        public Builder setTappableElementInsets(Insets value) {
            tappable = Objects.requireNonNull(value);
            legacyTappableExplicitlySet = true;
            int idx = Type.indexOf(Type.TAPPABLE_ELEMENT);
            typeInsetsMap[idx] = value;
            typeMaxInsetsMap[idx] = value;
            return this;
        }

        public Builder setInsets(int typeMask, Insets insets) {
            Objects.requireNonNull(insets, "insets must not be null");
            typesExplicitlySet = true;
            if ((typeMask & (Type.systemBars() | Type.IME)) != 0 && !insets.equals(Insets.NONE)) {
                systemConsumed = false;
            }
            for (int i = 0; i < TYPE_COUNT; i++) {
                int bit = 1 << i;
                if ((typeMask & bit) != 0) {
                    typeInsetsMap[i] = insets;
                    if (typeMaxInsetsMap[i].equals(Insets.NONE)) {
                        typeMaxInsetsMap[i] = insets;
                    }
                    if (!visibilityExplicitlySet[i] && bit == Type.IME) {
                        typeVisibilityMap[i] = !insets.equals(Insets.NONE);
                    }
                }
            }
            return this;
        }

        public Builder setInsetsIgnoringVisibility(int typeMask, Insets insets)
                throws IllegalArgumentException {
            if (typeMask == Type.IME || (typeMask & Type.IME) != 0) {
                throw new IllegalArgumentException("Maximum inset not available for IME");
            }
            Objects.requireNonNull(insets, "insets must not be null");
            typesExplicitlySet = true;
            for (int i = 0; i < TYPE_COUNT; i++) {
                if ((typeMask & (1 << i)) != 0) {
                    typeMaxInsetsMap[i] = insets;
                }
            }
            return this;
        }

        public Builder setVisible(int typeMask, boolean visible) {
            for (int i = 0; i < TYPE_COUNT; i++) {
                if ((typeMask & (1 << i)) != 0) {
                    typeVisibilityMap[i] = visible;
                    visibilityExplicitlySet[i] = true;
                }
            }
            return this;
        }

        @Deprecated
        public Builder setStableInsets(Insets value) {
            stable = Objects.requireNonNull(value);
            stableConsumed = false;
            return this;
        }

        public Builder setDisplayCutout(DisplayCutout value) {
            cutout = value == null || value.isEmpty() ? null : value;
            cutoutConsumed = cutout == null;
            int idx = Type.indexOf(Type.DISPLAY_CUTOUT);
            if (cutout != null) {
                Insets ci = Insets.of(cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                        cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
                typeInsetsMap[idx] = ci;
                typeMaxInsetsMap[idx] = ci;
            } else {
                typeInsetsMap[idx] = Insets.NONE;
            }
            return this;
        }

        public Builder setRoundedCorner(int position, RoundedCorner roundedCorner) {
            if (position >= 0 && position < 4) {
                roundedCorners[position] = roundedCorner;
            }
            return this;
        }

        public Builder setPrivacyIndicatorBounds(Rect bounds) {
            privacyIndicatorBounds = bounds != null ? new Rect(bounds) : null;
            return this;
        }

        public Builder setDisplayShape(DisplayShape shape) {
            displayShape = shape;
            return this;
        }

        public Builder setBoundingRects(int typeMask, List<Rect> rects) {
            Objects.requireNonNull(rects, "rects must not be null");
            List<Rect> copy = copyRectList(rects);
            for (int i = 0; i < TYPE_COUNT; i++) {
                if ((typeMask & (1 << i)) != 0) {
                    typeBoundingRectsMap[i] = copy;
                }
            }
            return this;
        }

        public Builder setBoundingRectsIgnoringVisibility(int typeMask, List<Rect> rects) {
            if ((typeMask & Type.IME) != 0) {
                throw new IllegalArgumentException("Not available for IME");
            }
            Objects.requireNonNull(rects, "rects must not be null");
            List<Rect> copy = copyRectList(rects);
            for (int i = 0; i < TYPE_COUNT; i++) {
                if ((typeMask & (1 << i)) != 0) {
                    typeMaxBoundingRectsMap[i] = copy;
                }
            }
            return this;
        }

        public Builder setFrame(int width, int height) {
            frameWidth = width;
            frameHeight = height;
            return this;
        }

        public Builder setRound(boolean value) { round = value; return this; }
        public Builder setAlwaysConsumeSystemBars(boolean value) { nav = value; return this; }

        public WindowInsets build() {
            return new WindowInsets(this);
        }

        private static List<Rect> copyRectList(List<Rect> rects) {
            ArrayList<Rect> list = new ArrayList<Rect>(rects.size());
            for (int i = 0; i < rects.size(); i++) {
                list.add(new Rect(rects.get(i)));
            }
            return Collections.unmodifiableList(list);
        }
    }

    public static final class Side {
        public static final int LEFT = 1 << 0;
        public static final int TOP = 1 << 1;
        public static final int RIGHT = 1 << 2;
        public static final int BOTTOM = 1 << 3;

        private Side() {
        }

        public static int all() {
            return LEFT | TOP | RIGHT | BOTTOM;
        }
    }

    public static final class Type {
        static final int STATUS_BARS = 1 << 0;
        static final int NAVIGATION_BARS = 1 << 1;
        static final int CAPTION_BAR = 1 << 2;
        static final int IME = 1 << 3;
        static final int SYSTEM_GESTURES = 1 << 4;
        static final int MANDATORY_SYSTEM_GESTURES = 1 << 5;
        static final int TAPPABLE_ELEMENT = 1 << 6;
        static final int DISPLAY_CUTOUT = 1 << 7;
        static final int WINDOW_DECOR = 1 << 8;
        static final int SYSTEM_OVERLAYS = 1 << 9;
        static final int ALL = 0x3ff;

        private Type() {
        }

        static int indexOf(int type) {
            switch (type) {
                case STATUS_BARS: return 0;
                case NAVIGATION_BARS: return 1;
                case CAPTION_BAR: return 2;
                case IME: return 3;
                case SYSTEM_GESTURES: return 4;
                case MANDATORY_SYSTEM_GESTURES: return 5;
                case TAPPABLE_ELEMENT: return 6;
                case DISPLAY_CUTOUT: return 7;
                case WINDOW_DECOR: return 8;
                case SYSTEM_OVERLAYS: return 9;
                default: throw new IllegalArgumentException("Unknown type: " + type);
            }
        }

        public static int statusBars() {
            return STATUS_BARS;
        }

        public static int navigationBars() {
            return NAVIGATION_BARS;
        }

        public static int captionBar() {
            return CAPTION_BAR;
        }

        public static int ime() {
            return IME;
        }

        public static int systemGestures() {
            return SYSTEM_GESTURES;
        }

        public static int mandatorySystemGestures() {
            return MANDATORY_SYSTEM_GESTURES;
        }

        public static int tappableElement() {
            return TAPPABLE_ELEMENT;
        }

        public static int displayCutout() {
            return DISPLAY_CUTOUT;
        }

        public static int systemOverlays() {
            return SYSTEM_OVERLAYS;
        }

        public static int systemBars() {
            return STATUS_BARS | NAVIGATION_BARS | CAPTION_BAR | SYSTEM_OVERLAYS;
        }
    }
}
