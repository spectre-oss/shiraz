// SPDX-License-Identifier: GPL-3.0-only
package android.inputmethodservice;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.xmlpull.v1.XmlPullParser;

@Deprecated
public class Keyboard {
    public static final int EDGE_LEFT = 0x01;
    public static final int EDGE_RIGHT = 0x02;
    public static final int EDGE_TOP = 0x04;
    public static final int EDGE_BOTTOM = 0x08;

    public static final int KEYCODE_SHIFT = -1;
    public static final int KEYCODE_MODE_CHANGE = -2;
    public static final int KEYCODE_CANCEL = -3;
    public static final int KEYCODE_DONE = -4;
    public static final int KEYCODE_DELETE = -5;
    public static final int KEYCODE_ALT = -6;

    private static final String TAG_KEYBOARD = "Keyboard";
    private static final String TAG_ROW = "Row";
    private static final String TAG_KEY = "Key";

    private int mDefaultHorizontalGap;
    private int mDefaultWidth;
    private int mDefaultHeight;
    private int mDefaultVerticalGap;
    private boolean mShifted;
    private final List<Key> mKeys = new ArrayList<>();
    private final List<Key> mModifierKeys = new ArrayList<>();
    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mTotalHeight;
    private int mTotalWidth;
    private int mKeyboardMode;
    private int mProximityThreshold;

    public static class Row {
        public int defaultWidth;
        public int defaultHeight;
        public int defaultHorizontalGap;
        public int verticalGap;
        public int rowEdgeFlags;
        public int mode;
        final Keyboard parent;

        public Row(Keyboard parent) {
            this.parent = parent;
            if (parent != null) {
                this.defaultWidth = parent.mDefaultWidth;
                this.defaultHeight = parent.mDefaultHeight;
                this.defaultHorizontalGap = parent.mDefaultHorizontalGap;
                this.verticalGap = parent.mDefaultVerticalGap;
            }
        }

        public Row(Resources res, Keyboard parent, XmlResourceParser parser) {
            this(parent);
            if (parser != null) {
                rowEdgeFlags = parser.getAttributeIntValue(null, "rowEdgeFlags", 0);
                mode = parser.getAttributeIntValue(null, "keyboardMode", 0);
            }
        }
    }

    public static class Key {
        public int[] codes;
        public CharSequence label;
        public Drawable icon;
        public Drawable iconPreview;
        public int width;
        public int height;
        public int gap;
        public boolean sticky;
        public int x;
        public int y;
        public boolean pressed;
        public boolean on;
        public CharSequence text;
        public CharSequence popupCharacters;
        public int edgeFlags;
        public boolean modifier;
        private final Keyboard keyboard;
        public int popupResId;
        public boolean repeatable;

        private static final int ATTR_STATE_CHECKABLE = 0x0101009f;
        private static final int ATTR_STATE_CHECKED = 0x010100a0;
        private static final int ATTR_STATE_PRESSED = 0x010100a7;

        private static final int[] KEY_STATE_NORMAL_ON = {
            ATTR_STATE_CHECKABLE,
            ATTR_STATE_CHECKED
        };
        private static final int[] KEY_STATE_PRESSED_ON = {
            ATTR_STATE_PRESSED,
            ATTR_STATE_CHECKABLE,
            ATTR_STATE_CHECKED
        };
        private static final int[] KEY_STATE_NORMAL_OFF = {
            ATTR_STATE_CHECKABLE
        };
        private static final int[] KEY_STATE_PRESSED_OFF = {
            ATTR_STATE_PRESSED,
            ATTR_STATE_CHECKABLE
        };
        private static final int[] KEY_STATE_NORMAL = {};
        private static final int[] KEY_STATE_PRESSED = {
            ATTR_STATE_PRESSED
        };

        public Key(Row parent) {
            keyboard = parent != null ? parent.parent : null;
            if (parent != null) {
                height = parent.defaultHeight;
                width = parent.defaultWidth;
                gap = parent.defaultHorizontalGap;
                edgeFlags = parent.rowEdgeFlags;
            }
        }

        public Key(Resources res, Row parent, int x, int y, XmlResourceParser parser) {
            this(parent);
            this.x = x;
            this.y = y;
            if (parser != null) {
                String codesAttr = parser.getAttributeValue(null, "codes");
                String labelAttr = parser.getAttributeValue(null, "keyLabel");
                if (labelAttr != null) {
                    this.label = labelAttr;
                }
                if (codesAttr != null && !codesAttr.isEmpty()) {
                    this.codes = parseCSV(codesAttr);
                } else if (labelAttr != null && !labelAttr.isEmpty()) {
                    this.codes = new int[] { labelAttr.charAt(0) };
                } else {
                    this.codes = new int[] { 0 };
                }
                this.repeatable = parser.getAttributeBooleanValue(null, "isRepeatable", false);
                this.modifier = parser.getAttributeBooleanValue(null, "isModifier", false);
                this.sticky = parser.getAttributeBooleanValue(null, "isSticky", false);
                this.edgeFlags |= parser.getAttributeIntValue(null, "keyEdgeFlags", 0);
                String outText = parser.getAttributeValue(null, "keyOutputText");
                if (outText != null) {
                    this.text = outText;
                }
            } else {
                this.codes = new int[] { 0 };
            }
            this.x += this.gap;
        }

        public void onPressed() {
            pressed = true;
        }

        public void onReleased(boolean inside) {
            pressed = false;
            if (sticky && inside) {
                on = !on;
            }
        }

        public boolean isInside(int px, int py) {
            boolean leftEdge = (edgeFlags & EDGE_LEFT) > 0;
            boolean rightEdge = (edgeFlags & EDGE_RIGHT) > 0;
            boolean topEdge = (edgeFlags & EDGE_TOP) > 0;
            boolean bottomEdge = (edgeFlags & EDGE_BOTTOM) > 0;
            return (px >= this.x || (leftEdge && px <= this.x + this.width))
                    && (px < this.x + this.width || (rightEdge && px >= this.x))
                    && (py >= this.y || (topEdge && py <= this.y + this.height))
                    && (py < this.y + this.height || (bottomEdge && py >= this.y));
        }

        public int squaredDistanceFrom(int px, int py) {
            int xDist = this.x + width / 2 - px;
            int yDist = this.y + height / 2 - py;
            return xDist * xDist + yDist * yDist;
        }

        public int[] getCurrentDrawableState() {
            if (on) {
                return pressed ? KEY_STATE_PRESSED_ON : KEY_STATE_NORMAL_ON;
            } else if (sticky) {
                return pressed ? KEY_STATE_PRESSED_OFF : KEY_STATE_NORMAL_OFF;
            } else {
                return pressed ? KEY_STATE_PRESSED : KEY_STATE_NORMAL;
            }
        }

        private static int[] parseCSV(String value) {
            StringTokenizer st = new StringTokenizer(value, ",");
            int count = st.countTokens();
            int[] values = new int[count];
            int i = 0;
            while (st.hasMoreTokens()) {
                String token = st.nextToken().trim();
                try {
                    values[i++] = Integer.decode(token);
                } catch (NumberFormatException e) {
                    values[i++] = token.isEmpty() ? 0 : token.charAt(0);
                }
            }
            return values;
        }
    }

    public Keyboard(Context context, int xmlLayoutResId) {
        this(context, xmlLayoutResId, 0);
    }

    public Keyboard(Context context, int xmlLayoutResId, int modeId, int width, int height) {
        mDisplayWidth = Math.max(1, width);
        mDisplayHeight = Math.max(1, height);
        mDefaultHorizontalGap = 0;
        mDefaultWidth = Math.max(1, mDisplayWidth / 10);
        mDefaultVerticalGap = 0;
        mDefaultHeight = mDefaultWidth;
        mKeyboardMode = modeId;
        mProximityThreshold = (int) (mDefaultWidth * 1.4f);
        mProximityThreshold = mProximityThreshold * mProximityThreshold;
        if (context != null && xmlLayoutResId != 0 && context.getResources() != null) {
            try {
                XmlResourceParser parser = context.getResources().getXml(xmlLayoutResId);
                if (parser != null) {
                    try {
                        loadKeyboard(context, parser);
                    } finally {
                        parser.close();
                    }
                }
            } catch (Exception ignored) {
                // Resource ID may be synthetic/non-XML when invoked with Keyboard(ctx, resId, chars, ...)
            }
        }
    }

    public Keyboard(Context context, int xmlLayoutResId, int modeId) {
        this(context, xmlLayoutResId, modeId, getDefaultDisplayWidth(context),
                getDefaultDisplayHeight(context));
    }

    public Keyboard(Context context, int layoutTemplateResId, CharSequence characters,
            int columns, int horizontalPadding) {
        this(context, layoutTemplateResId);
        mKeys.clear();
        mModifierKeys.clear();
        int x = 0;
        int y = 0;
        int column = 0;
        mTotalWidth = 0;

        Row row = new Row(this);
        row.defaultHeight = mDefaultHeight;
        row.defaultWidth = mDefaultWidth;
        row.defaultHorizontalGap = mDefaultHorizontalGap;
        row.verticalGap = mDefaultVerticalGap;
        row.rowEdgeFlags = EDGE_TOP | EDGE_BOTTOM;

        final int maxColumns = columns == -1 ? Integer.MAX_VALUE : Math.max(1, columns);
        if (characters != null) {
            for (int i = 0; i < characters.length(); i++) {
                char c = characters.charAt(i);
                if (column >= maxColumns
                        || x + mDefaultWidth + horizontalPadding > mDisplayWidth) {
                    x = 0;
                    y += mDefaultVerticalGap + mDefaultHeight;
                    column = 0;
                }
                final Key key = new Key(row);
                key.x = x;
                key.y = y;
                key.label = String.valueOf(c);
                key.codes = new int[] { c };
                column++;
                x += key.width + key.gap;
                mKeys.add(key);
                if (x > mTotalWidth) {
                    mTotalWidth = x;
                }
            }
        }
        mTotalHeight = y + mDefaultHeight;
    }

    public List<Key> getKeys() {
        return mKeys;
    }

    public List<Key> getModifierKeys() {
        return mModifierKeys;
    }

    protected int getHorizontalGap() {
        return mDefaultHorizontalGap;
    }

    protected void setHorizontalGap(int gap) {
        mDefaultHorizontalGap = gap;
    }

    protected int getVerticalGap() {
        return mDefaultVerticalGap;
    }

    protected void setVerticalGap(int gap) {
        mDefaultVerticalGap = gap;
    }

    protected int getKeyHeight() {
        return mDefaultHeight;
    }

    protected void setKeyHeight(int height) {
        mDefaultHeight = height;
    }

    protected int getKeyWidth() {
        return mDefaultWidth;
    }

    protected void setKeyWidth(int width) {
        mDefaultWidth = width;
    }

    public int getHeight() {
        return mTotalHeight;
    }

    public int getMinWidth() {
        return mTotalWidth;
    }

    public boolean setShifted(boolean shiftState) {
        boolean changed = mShifted != shiftState;
        mShifted = shiftState;
        for (int i = 0; i < mModifierKeys.size(); i++) {
            Key k = mModifierKeys.get(i);
            if (k.codes != null && k.codes.length > 0 && k.codes[0] == KEYCODE_SHIFT) {
                k.on = shiftState;
            }
        }
        return changed;
    }

    public boolean isShifted() {
        return mShifted;
    }

    public int getShiftKeyIndex() {
        for (int i = 0; i < mKeys.size(); i++) {
            Key k = mKeys.get(i);
            if (k.codes != null && k.codes.length > 0 && k.codes[0] == KEYCODE_SHIFT) {
                return i;
            }
        }
        return -1;
    }

    public int[] getNearestKeys(int x, int y) {
        List<Integer> hits = new ArrayList<>();
        for (int i = 0; i < mKeys.size(); i++) {
            Key key = mKeys.get(i);
            if (key.isInside(x, y) || key.squaredDistanceFrom(x, y) < mProximityThreshold) {
                hits.add(i);
            }
        }
        int[] result = new int[hits.size()];
        for (int i = 0; i < hits.size(); i++) {
            result[i] = hits.get(i);
        }
        return result;
    }

    protected Row createRowFromXml(Resources res, XmlResourceParser parser) {
        return new Row(res, this, parser);
    }

    protected Key createKeyFromXml(Resources res, Row parent, int x, int y,
            XmlResourceParser parser) {
        return new Key(res, parent, x, y, parser);
    }

    private void loadKeyboard(Context context, XmlResourceParser parser) throws Exception {
        Resources res = context.getResources();
        boolean inRow = false;
        int x = 0;
        int y = 0;
        Row currentRow = null;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if (TAG_ROW.equals(tag)) {
                    inRow = true;
                    x = 0;
                    currentRow = createRowFromXml(res, parser);
                    if (currentRow.mode != 0 && currentRow.mode != mKeyboardMode) {
                        skipToEndOfRow(parser);
                        inRow = false;
                    }
                } else if (TAG_KEY.equals(tag) && inRow && currentRow != null) {
                    Key key = createKeyFromXml(res, currentRow, x, y, parser);
                    mKeys.add(key);
                    if (key.codes != null && key.codes.length > 0
                            && (key.codes[0] == KEYCODE_SHIFT || key.codes[0] == KEYCODE_ALT)) {
                        mModifierKeys.add(key);
                    }
                    x = key.x + key.width;
                    if (x > mTotalWidth) {
                        mTotalWidth = x;
                    }
                } else if (TAG_KEYBOARD.equals(tag)) {
                    mDefaultWidth = parser.getAttributeIntValue(null, "keyWidth", mDefaultWidth);
                    mDefaultHeight = parser.getAttributeIntValue(null, "keyHeight", mDefaultHeight);
                    mDefaultHorizontalGap = parser.getAttributeIntValue(
                            null, "horizontalGap", mDefaultHorizontalGap);
                    mDefaultVerticalGap = parser.getAttributeIntValue(
                            null, "verticalGap", mDefaultVerticalGap);
                }
            } else if (event == XmlPullParser.END_TAG) {
                if (TAG_ROW.equals(parser.getName()) && inRow && currentRow != null) {
                    inRow = false;
                    y += currentRow.verticalGap + currentRow.defaultHeight;
                }
            }
        }
        mTotalHeight = Math.max(0, y - mDefaultVerticalGap);
    }

    private static void skipToEndOfRow(XmlResourceParser parser) throws Exception {
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.END_TAG && TAG_ROW.equals(parser.getName())) {
                return;
            }
        }
    }

    private static int getDefaultDisplayWidth(Context context) {
        if (context != null && context.getResources() != null) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            if (dm != null && dm.widthPixels > 0) {
                return dm.widthPixels;
            }
        }
        return 480;
    }

    private static int getDefaultDisplayHeight(Context context) {
        if (context != null && context.getResources() != null) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            if (dm != null && dm.heightPixels > 0) {
                return dm.heightPixels;
            }
        }
        return 800;
    }
}
