// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;

import android.content.res.Resources;
import android.util.AttributeSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class LevelListDrawable extends DrawableContainer {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private LevelListState levelListState;
    private boolean mutated;

    public LevelListDrawable() {
        this(null, null);
    }

    private LevelListDrawable(LevelListState orig, Resources res) {
        LevelListState as = new LevelListState(orig, this, res);
        setConstantState(as);
        onLevelChange(getLevel());
    }

    public void addLevel(int low, int high, Drawable drawable) {
        if (drawable != null) {
            levelListState.addLevel(low, high, drawable);
            addChild(drawable);
            onLevelChange(getLevel());
        }
    }

    @Override
    protected boolean onLevelChange(int level) {
        int idx = levelListState.indexOfLevel(level);
        if (selectDrawable(idx)) {
            return true;
        }
        return super.onLevelChange(level);
    }

    @Override
    public void inflate(
            Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme)
            throws XmlPullParserException, IOException {
        if (parser == null) {
            return;
        }
        int innerDepth = parser.getDepth() + 1;
        int type;
        int depth;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                        || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            if (depth > innerDepth || !"item".equals(parser.getName())) {
                continue;
            }
            int low = 0;
            int high = 0;
            int drawableRes = 0;
            if (attrs != null) {
                low = attrs.getAttributeIntValue(ANDROID_NS, "minLevel", 0);
                high = attrs.getAttributeIntValue(ANDROID_NS, "maxLevel", 0);
                drawableRes = attrs.getAttributeResourceValue(ANDROID_NS, "drawable", 0);
            }
            if (high < low) {
                throw new XmlPullParserException(
                        parser.getPositionDescription()
                                + ": <item> tag requires a 'maxLevel' attribute >= 'minLevel'");
            }
            Drawable dr = null;
            if (drawableRes != 0 && r != null) {
                dr = r.getDrawable(drawableRes, theme);
            } else {
                while ((type = parser.next()) == XmlPullParser.TEXT) {
                    // skip text
                }
                if (type != XmlPullParser.START_TAG) {
                    throw new XmlPullParserException(
                            parser.getPositionDescription()
                                    + ": <item> tag requires a 'drawable' attribute or child tag");
                }
                if ("color".equals(parser.getName())) {
                    dr = new ColorDrawable();
                    dr.inflate(r, parser, attrs, theme);
                } else if ("shape".equals(parser.getName())) {
                    dr = new GradientDrawable();
                }
            }
            if (dr != null) {
                addLevel(low, high, dr);
            }
        }
        onLevelChange(getLevel());
    }

    @Override
    public Drawable mutate() {
        if (!mutated && super.mutate() == this) {
            levelListState.mutate();
            mutated = true;
        }
        return this;
    }

    @Override
    protected void setConstantState(DrawableContainerState state) {
        super.setConstantState(state);
        if (state instanceof LevelListState) {
            levelListState = (LevelListState) state;
        }
    }

    private static final class LevelListState extends DrawableContainerState {
        private int[] lows;
        private int[] highs;

        LevelListState(LevelListState orig, LevelListDrawable owner, Resources res) {
            super(orig, owner, res);
            if (orig != null) {
                lows = orig.lows;
                highs = orig.highs;
            } else {
                lows = new int[getChildren().length];
                highs = new int[getChildren().length];
            }
        }

        void mutate() {
            lows = lows.clone();
            highs = highs.clone();
        }

        void addLevel(int low, int high, Drawable drawable) {
            int pos = addChild(drawable);
            if (pos >= lows.length) {
                growArray(pos, pos + 10);
            }
            lows[pos] = low;
            highs[pos] = high;
        }

        int indexOfLevel(int level) {
            final int[] lowArr = lows;
            final int[] highArr = highs;
            final int n = getChildCount();
            for (int i = 0; i < n; i++) {
                if (level >= lowArr[i] && level <= highArr[i]) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public Drawable newDrawable() {
            return new LevelListDrawable(this, null);
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return new LevelListDrawable(this, res);
        }

        @Override
        public void growArray(int oldSize, int newSize) {
            super.growArray(oldSize, newSize);
            int[] newInts = new int[newSize];
            if (lows != null) {
                System.arraycopy(lows, 0, newInts, 0, Math.min(oldSize, lows.length));
            }
            lows = newInts;
            newInts = new int[newSize];
            if (highs != null) {
                System.arraycopy(highs, 0, newInts, 0, Math.min(oldSize, highs.length));
            }
            highs = newInts;
        }
    }
}
