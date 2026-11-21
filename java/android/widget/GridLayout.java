// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import java.util.Arrays;

/** Two-dimensional grid container supporting row/column spans, weights, and alignments. */
@RemoteViews.RemoteView
public class GridLayout extends ViewGroup {
    public static final int HORIZONTAL = LinearLayout.HORIZONTAL;
    public static final int VERTICAL = LinearLayout.VERTICAL;
    public static final int UNDEFINED = Integer.MIN_VALUE;
    public static final int ALIGN_BOUNDS = 0;
    public static final int ALIGN_MARGINS = 1;

    public abstract static class Alignment {
        Alignment() {
        }

        abstract int getGravityOffset(View view, int cellDelta);

        abstract int getAlignmentValue(View view, int viewSize, int mode);

        int getSizeInCell(View view, int viewSize, int cellSize) {
            return viewSize;
        }
    }

    static final Alignment UNDEFINED_ALIGNMENT = new Alignment() {
        @Override
        int getGravityOffset(View view, int cellDelta) {
            return UNDEFINED;
        }

        @Override
        public int getAlignmentValue(View view, int viewSize, int mode) {
            return UNDEFINED;
        }
    };

    private static final Alignment LEADING = new Alignment() {
        @Override
        int getGravityOffset(View view, int cellDelta) {
            return 0;
        }

        @Override
        public int getAlignmentValue(View view, int viewSize, int mode) {
            return 0;
        }
    };

    private static final Alignment TRAILING = new Alignment() {
        @Override
        int getGravityOffset(View view, int cellDelta) {
            return cellDelta;
        }

        @Override
        public int getAlignmentValue(View view, int viewSize, int mode) {
            return viewSize;
        }
    };

    public static final Alignment TOP = LEADING;
    public static final Alignment BOTTOM = TRAILING;
    public static final Alignment START = LEADING;
    public static final Alignment END = TRAILING;

    private static Alignment createSwitchingAlignment(final Alignment ltr, final Alignment rtl) {
        return new Alignment() {
            @Override
            int getGravityOffset(View view, int cellDelta) {
                boolean isLayoutRtl = view != null
                        && view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                return (!isLayoutRtl ? ltr : rtl).getGravityOffset(view, cellDelta);
            }

            @Override
            public int getAlignmentValue(View view, int viewSize, int mode) {
                boolean isLayoutRtl = view != null
                        && view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                return (!isLayoutRtl ? ltr : rtl).getAlignmentValue(view, viewSize, mode);
            }
        };
    }

    public static final Alignment LEFT = createSwitchingAlignment(START, END);
    public static final Alignment RIGHT = createSwitchingAlignment(END, START);

    public static final Alignment CENTER = new Alignment() {
        @Override
        int getGravityOffset(View view, int cellDelta) {
            return cellDelta >> 1;
        }

        @Override
        public int getAlignmentValue(View view, int viewSize, int mode) {
            return viewSize >> 1;
        }
    };

    public static final Alignment BASELINE = new Alignment() {
        @Override
        int getGravityOffset(View view, int cellDelta) {
            return 0;
        }

        @Override
        public int getAlignmentValue(View view, int viewSize, int mode) {
            if (view == null || view.getVisibility() == GONE) {
                return 0;
            }
            int baseline = view.getBaseline();
            return baseline == -1 ? UNDEFINED : baseline;
        }
    };

    public static final Alignment FILL = new Alignment() {
        @Override
        int getGravityOffset(View view, int cellDelta) {
            return 0;
        }

        @Override
        public int getAlignmentValue(View view, int viewSize, int mode) {
            return UNDEFINED;
        }

        @Override
        public int getSizeInCell(View view, int viewSize, int cellSize) {
            return cellSize;
        }
    };

    public static class Spec {
        static final Spec UNDEFINED = spec(GridLayout.UNDEFINED);
        static final float DEFAULT_WEIGHT = 0f;

        final boolean startDefined;
        final int spanMin;
        final int spanSize;
        final Alignment alignment;
        final float weight;

        Spec(boolean startDefined, int start, int size, Alignment alignment, float weight) {
            this.startDefined = startDefined;
            this.spanMin = start;
            this.spanSize = Math.max(1, size);
            this.alignment = alignment != null ? alignment : UNDEFINED_ALIGNMENT;
            this.weight = weight;
        }

        Alignment getAbsoluteAlignment(boolean horizontal) {
            if (alignment != UNDEFINED_ALIGNMENT) {
                return alignment;
            }
            if (weight == 0f) {
                return horizontal ? START : BASELINE;
            }
            return FILL;
        }

        final Spec copyWriteSpan(int min, int size) {
            return new Spec(startDefined, min, size, alignment, weight);
        }

        final Spec copyWriteAlignment(Alignment newAlignment) {
            return new Spec(startDefined, spanMin, spanSize, newAlignment, weight);
        }

        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            }
            if (that == null || getClass() != that.getClass()) {
                return false;
            }
            Spec spec = (Spec) that;
            if (!alignment.equals(spec.alignment)) {
                return false;
            }
            return spanMin == spec.spanMin && spanSize == spec.spanSize;
        }

        @Override
        public int hashCode() {
            int result = 31 * spanMin + (spanMin + spanSize);
            result = 31 * result + alignment.hashCode();
            return result;
        }
    }

    public static Spec spec(int start, int size, Alignment alignment, float weight) {
        return new Spec(start != UNDEFINED, start, size, alignment, weight);
    }

    public static Spec spec(int start, Alignment alignment, float weight) {
        return spec(start, 1, alignment, weight);
    }

    public static Spec spec(int start, int size, float weight) {
        return spec(start, size, UNDEFINED_ALIGNMENT, weight);
    }

    public static Spec spec(int start, float weight) {
        return spec(start, 1, weight);
    }

    public static Spec spec(int start, int size, Alignment alignment) {
        return spec(start, size, alignment, Spec.DEFAULT_WEIGHT);
    }

    public static Spec spec(int start, Alignment alignment) {
        return spec(start, 1, alignment);
    }

    public static Spec spec(int start, int size) {
        return spec(start, size, UNDEFINED_ALIGNMENT);
    }

    public static Spec spec(int start) {
        return spec(start, 1);
    }

    private static Alignment getAlignment(int gravity, boolean horizontal) {
        final int axisSpecified = 0x0001;
        final int axisPullBefore = 0x0002;
        final int axisPullAfter = 0x0004;
        int mask = horizontal ? Gravity.HORIZONTAL_GRAVITY_MASK : Gravity.VERTICAL_GRAVITY_MASK;
        int shift = horizontal ? 0 : 4;
        int flags = (gravity & mask) >> shift;
        switch (flags) {
            case (axisSpecified | axisPullBefore):
                return horizontal ? LEFT : TOP;
            case (axisSpecified | axisPullAfter):
                return horizontal ? RIGHT : BOTTOM;
            case (axisSpecified | axisPullBefore | axisPullAfter):
                return FILL;
            case axisSpecified:
                return CENTER;
            case (axisSpecified | axisPullBefore | Gravity.RELATIVE_LAYOUT_DIRECTION):
                return START;
            case (axisSpecified | axisPullAfter | Gravity.RELATIVE_LAYOUT_DIRECTION):
                return END;
            default:
                return UNDEFINED_ALIGNMENT;
        }
    }

    public static class LayoutParams extends MarginLayoutParams {
        public Spec rowSpec = Spec.UNDEFINED;
        public Spec columnSpec = Spec.UNDEFINED;

        private LayoutParams(
                int width,
                int height,
                int left,
                int top,
                int right,
                int bottom,
                Spec rowSpec,
                Spec columnSpec) {
            super(width, height);
            setMargins(left, top, right, bottom);
            this.rowSpec = rowSpec != null ? rowSpec : Spec.UNDEFINED;
            this.columnSpec = columnSpec != null ? columnSpec : Spec.UNDEFINED;
        }

        public LayoutParams(Spec rowSpec, Spec columnSpec) {
            this(WRAP_CONTENT, WRAP_CONTENT,
                    UNDEFINED, UNDEFINED, UNDEFINED, UNDEFINED,
                    rowSpec, columnSpec);
            setMargins(0, 0, 0, 0);
        }

        public LayoutParams() {
            this(Spec.UNDEFINED, Spec.UNDEFINED);
        }

        public LayoutParams(ViewGroup.LayoutParams params) {
            super(params);
        }

        public LayoutParams(MarginLayoutParams params) {
            super(params);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.rowSpec = source.rowSpec;
            this.columnSpec = source.columnSpec;
        }

        public LayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);
            if (attrs != null) {
                shiraz.runtime.XmlAttributes a = new shiraz.runtime.XmlAttributes(context, attrs);
                int gravity = a.integer("layout_gravity", Gravity.NO_GRAVITY);
                int col = a.integer("layout_column", UNDEFINED);
                int colSpan = Math.max(1, a.integer("layout_columnSpan", 1));
                float colWeight = a.number("layout_columnWeight", Spec.DEFAULT_WEIGHT);
                this.columnSpec = spec(col, colSpan, getAlignment(gravity, true), colWeight);

                int row = a.integer("layout_row", UNDEFINED);
                int rowSpan = Math.max(1, a.integer("layout_rowSpan", 1));
                float rowWeight = a.number("layout_rowWeight", Spec.DEFAULT_WEIGHT);
                this.rowSpec = spec(row, rowSpan, getAlignment(gravity, false), rowWeight);
            }
        }

        @Override
        protected boolean shirazDefaultsMissingSize() {
            return true;
        }

        public void setGravity(int gravity) {
            rowSpec = rowSpec.copyWriteAlignment(getAlignment(gravity, false));
            columnSpec = columnSpec.copyWriteAlignment(getAlignment(gravity, true));
        }

        protected void setBaseAttributes(TypedArray attributes, int widthAttr, int heightAttr) {
            this.width = attributes.getLayoutDimension(widthAttr, WRAP_CONTENT);
            this.height = attributes.getLayoutDimension(heightAttr, WRAP_CONTENT);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LayoutParams that = (LayoutParams) o;
            if (!columnSpec.equals(that.columnSpec)) {
                return false;
            }
            return rowSpec.equals(that.rowSpec);
        }

        @Override
        public int hashCode() {
            int result = rowSpec.hashCode();
            result = 31 * result + columnSpec.hashCode();
            return result;
        }
    }

    private int mOrientation = HORIZONTAL;
    private int mRowCount = UNDEFINED;
    private int mColumnCount = UNDEFINED;
    private boolean mUseDefaultMargins = false;
    private int mAlignmentMode = ALIGN_MARGINS;
    private boolean mRowOrderPreserved = true;
    private boolean mColumnOrderPreserved = true;
    private final int mDefaultGap;

    private int[] mColLocations;
    private int[] mRowLocations;
    private int[] mResolvedChildRows;
    private int[] mResolvedChildCols;

    public GridLayout(Context context) {
        this(context, null);
    }

    public GridLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GridLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public GridLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mDefaultGap = Math.round(8f * context.getResources().getDisplayMetrics().density);
        if (attrs != null) {
            shiraz.runtime.XmlAttributes a =
                    new shiraz.runtime.XmlAttributes(context, attrs, defStyleAttr, defStyleRes);
            if (a.has("orientation")) {
                setOrientation(a.integer("orientation", HORIZONTAL));
            }
            if (a.has("rowCount")) {
                setRowCount(a.integer("rowCount", UNDEFINED));
            }
            if (a.has("columnCount")) {
                setColumnCount(a.integer("columnCount", UNDEFINED));
            }
            if (a.has("useDefaultMargins")) {
                setUseDefaultMargins(a.bool("useDefaultMargins", false));
            }
            if (a.has("alignmentMode")) {
                setAlignmentMode(a.integer("alignmentMode", ALIGN_MARGINS));
            }
            if (a.has("rowOrderPreserved")) {
                setRowOrderPreserved(a.bool("rowOrderPreserved", true));
            }
            if (a.has("columnOrderPreserved")) {
                setColumnOrderPreserved(a.bool("columnOrderPreserved", true));
            }
        }
    }

    public int getOrientation() {
        return mOrientation;
    }

    public void setOrientation(int orientation) {
        if (mOrientation != orientation) {
            mOrientation = orientation;
            requestLayout();
        }
    }

    public int getRowCount() {
        return mRowCount != UNDEFINED ? mRowCount : computeEffectiveCounts()[0];
    }

    public void setRowCount(int rowCount) {
        if (rowCount != UNDEFINED && rowCount < 0) {
            throw new IllegalArgumentException("rowCount must be >= 0 or UNDEFINED");
        }
        mRowCount = rowCount;
        requestLayout();
    }

    public int getColumnCount() {
        return mColumnCount != UNDEFINED ? mColumnCount : computeEffectiveCounts()[1];
    }

    public void setColumnCount(int columnCount) {
        if (columnCount != UNDEFINED && columnCount < 0) {
            throw new IllegalArgumentException("columnCount must be >= 0 or UNDEFINED");
        }
        mColumnCount = columnCount;
        requestLayout();
    }

    public boolean getUseDefaultMargins() {
        return mUseDefaultMargins;
    }

    public void setUseDefaultMargins(boolean useDefaultMargins) {
        mUseDefaultMargins = useDefaultMargins;
        requestLayout();
    }

    public int getAlignmentMode() {
        return mAlignmentMode;
    }

    public void setAlignmentMode(int alignmentMode) {
        mAlignmentMode = alignmentMode;
        requestLayout();
    }

    public boolean isRowOrderPreserved() {
        return mRowOrderPreserved;
    }

    public void setRowOrderPreserved(boolean rowOrderPreserved) {
        mRowOrderPreserved = rowOrderPreserved;
        requestLayout();
    }

    public boolean isColumnOrderPreserved() {
        return mColumnOrderPreserved;
    }

    public void setColumnOrderPreserved(boolean columnOrderPreserved) {
        mColumnOrderPreserved = columnOrderPreserved;
        requestLayout();
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof LayoutParams) {
            return new LayoutParams((LayoutParams) lp);
        } else if (lp instanceof MarginLayoutParams) {
            return new LayoutParams((MarginLayoutParams) lp);
        } else {
            return new LayoutParams(lp);
        }
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        requestLayout();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        requestLayout();
    }

    private int getMarginValue(int margin) {
        if (margin == UNDEFINED) {
            return mUseDefaultMargins ? mDefaultGap / 2 : 0;
        }
        return margin;
    }

    private int[] computeEffectiveCounts() {
        int childCount = getChildCount();
        if (childCount == 0) {
            return new int[] {
                mRowCount != UNDEFINED ? mRowCount : 0,
                mColumnCount != UNDEFINED ? mColumnCount : 0
            };
        }
        boolean horizontal = (mOrientation == HORIZONTAL);
        int fixedMajor = horizontal ? mColumnCount : mRowCount;
        int maxDefinedMajor = 0;
        for (int i = 0; i < childCount; i++) {
            LayoutParams lp = (LayoutParams) getChildAt(i).getLayoutParams();
            Spec s = horizontal ? lp.columnSpec : lp.rowSpec;
            int end = (s.startDefined ? Math.max(0, s.spanMin) : 0) + s.spanSize;
            if (end > maxDefinedMajor) {
                maxDefinedMajor = end;
            }
        }
        int majorCount = fixedMajor != UNDEFINED
                ? Math.max(fixedMajor, maxDefinedMajor)
                : Math.max(maxDefinedMajor, childCount);
        if (majorCount <= 0) {
            majorCount = 1;
        }

        int[] resolvedRows = new int[childCount];
        int[] resolvedCols = new int[childCount];
        int[] occupiedUntilMinor = new int[majorCount];
        int minorCursor = 0;
        int majorCursor = 0;
        int maxRow = mRowCount != UNDEFINED ? mRowCount : 0;
        int maxCol = mColumnCount != UNDEFINED ? mColumnCount : 0;

        for (int i = 0; i < childCount; i++) {
            LayoutParams lp = (LayoutParams) getChildAt(i).getLayoutParams();
            Spec majorSpec = horizontal ? lp.columnSpec : lp.rowSpec;
            Spec minorSpec = horizontal ? lp.rowSpec : lp.columnSpec;
            int majorSpan = Math.min(majorSpec.spanSize, majorCount);
            int minorSpan = minorSpec.spanSize;

            int minorPos;
            int majorPos;
            if (minorSpec.startDefined && majorSpec.startDefined) {
                minorPos = Math.max(0, minorSpec.spanMin);
                majorPos = Math.max(0, Math.min(majorSpec.spanMin, majorCount - majorSpan));
            } else if (majorSpec.startDefined) {
                majorPos = Math.max(0, Math.min(majorSpec.spanMin, majorCount - majorSpan));
                minorPos = minorSpec.startDefined ? Math.max(0, minorSpec.spanMin) : minorCursor;
                while (!fits(occupiedUntilMinor, majorPos, majorSpan, minorPos)) {
                    minorPos++;
                }
            } else if (minorSpec.startDefined) {
                minorPos = Math.max(0, minorSpec.spanMin);
                majorPos = 0;
                while (majorPos + majorSpan <= majorCount
                        && !fits(occupiedUntilMinor, majorPos, majorSpan, minorPos)) {
                    majorPos++;
                }
                if (majorPos + majorSpan > majorCount) {
                    majorPos = 0;
                }
            } else {
                minorPos = minorCursor;
                majorPos = majorCursor;
                while (true) {
                    if (majorPos + majorSpan > majorCount) {
                        majorPos = 0;
                        minorPos++;
                    }
                    if (fits(occupiedUntilMinor, majorPos, majorSpan, minorPos)) {
                        break;
                    }
                    majorPos++;
                }
                minorCursor = minorPos;
                majorCursor = majorPos + majorSpan;
            }

            for (int k = majorPos; k < majorPos + majorSpan && k < majorCount; k++) {
                occupiedUntilMinor[k] = Math.max(occupiedUntilMinor[k], minorPos + minorSpan);
            }

            int r = horizontal ? minorPos : majorPos;
            int c = horizontal ? majorPos : minorPos;
            resolvedRows[i] = r;
            resolvedCols[i] = c;
            maxRow = Math.max(maxRow, r + lp.rowSpec.spanSize);
            maxCol = Math.max(maxCol, c + lp.columnSpec.spanSize);
        }

        mResolvedChildRows = resolvedRows;
        mResolvedChildCols = resolvedCols;
        return new int[] { maxRow, maxCol };
    }

    private static boolean fits(int[] occupiedUntilMinor, int majorStart, int majorSpan, int minorPos) {
        for (int k = majorStart; k < majorStart + majorSpan && k < occupiedUntilMinor.length; k++) {
            if (occupiedUntilMinor[k] > minorPos) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int[] counts = computeEffectiveCounts();
        int rows = counts[0];
        int cols = counts[1];
        int hPad = getPaddingLeft() + getPaddingRight();
        int vPad = getPaddingTop() + getPaddingBottom();

        int childCount = getChildCount();
        if (childCount == 0 || rows == 0 || cols == 0) {
            setMeasuredDimension(
                    resolveSizeAndState(Math.max(hPad, getSuggestedMinimumWidth()), widthSpec, 0),
                    resolveSizeAndState(Math.max(vPad, getSuggestedMinimumHeight()), heightSpec, 0));
            mColLocations = new int[cols + 1];
            mRowLocations = new int[rows + 1];
            return;
        }

        int layoutDir = getLayoutDirection();
        int[] colWidths = new int[cols];
        int[] rowHeights = new int[rows];
        float[] colWeights = new float[cols];
        float[] rowWeights = new float[rows];

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.resolveLayoutDirection(layoutDir);
            int leftM = getMarginValue(lp.leftMargin);
            int rightM = getMarginValue(lp.rightMargin);
            int topM = getMarginValue(lp.topMargin);
            int bottomM = getMarginValue(lp.bottomMargin);

            int cwSpec = getChildMeasureSpec(widthSpec, hPad + leftM + rightM,
                    lp.columnSpec.weight > 0 && lp.width == 0 ? LayoutParams.WRAP_CONTENT : lp.width);
            int chSpec = getChildMeasureSpec(heightSpec, vPad + topM + bottomM,
                    lp.rowSpec.weight > 0 && lp.height == 0 ? LayoutParams.WRAP_CONTENT : lp.height);
            child.measure(cwSpec, chSpec);

            int c = mResolvedChildCols[i];
            int cSpan = Math.min(lp.columnSpec.spanSize, cols - c);
            int r = mResolvedChildRows[i];
            int rSpan = Math.min(lp.rowSpec.spanSize, rows - r);

            int reqW = (lp.columnSpec.weight > 0 && lp.width == 0 ? 0 : child.getMeasuredWidth())
                    + leftM + rightM;
            int reqH = (lp.rowSpec.weight > 0 && lp.height == 0 ? 0 : child.getMeasuredHeight())
                    + topM + bottomM;

            if (cSpan == 1) {
                colWidths[c] = Math.max(colWidths[c], reqW);
            }
            if (rSpan == 1) {
                rowHeights[r] = Math.max(rowHeights[r], reqH);
            }
            if (lp.columnSpec.weight > 0f && cSpan > 0) {
                float share = lp.columnSpec.weight / cSpan;
                for (int k = 0; k < cSpan; k++) {
                    colWeights[c + k] = Math.max(colWeights[c + k], share);
                }
            }
            if (lp.rowSpec.weight > 0f && rSpan > 0) {
                float share = lp.rowSpec.weight / rSpan;
                for (int k = 0; k < rSpan; k++) {
                    rowWeights[r + k] = Math.max(rowWeights[r + k], share);
                }
            }
        }

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int c = mResolvedChildCols[i];
            int cSpan = Math.min(lp.columnSpec.spanSize, cols - c);
            if (cSpan > 1) {
                int reqW = child.getMeasuredWidth()
                        + getMarginValue(lp.leftMargin) + getMarginValue(lp.rightMargin);
                int cur = 0;
                for (int k = 0; k < cSpan; k++) {
                    cur += colWidths[c + k];
                }
                if (reqW > cur) {
                    int extra = reqW - cur;
                    for (int k = 0; k < cSpan; k++) {
                        int add = (extra + (cSpan - k - 1)) / (cSpan - k);
                        colWidths[c + k] += add;
                        extra -= add;
                    }
                }
            }
            int r = mResolvedChildRows[i];
            int rSpan = Math.min(lp.rowSpec.spanSize, rows - r);
            if (rSpan > 1) {
                int reqH = child.getMeasuredHeight()
                        + getMarginValue(lp.topMargin) + getMarginValue(lp.bottomMargin);
                int cur = 0;
                for (int k = 0; k < rSpan; k++) {
                    cur += rowHeights[r + k];
                }
                if (reqH > cur) {
                    int extra = reqH - cur;
                    for (int k = 0; k < rSpan; k++) {
                        int add = (extra + (rSpan - k - 1)) / (rSpan - k);
                        rowHeights[r + k] += add;
                        extra -= add;
                    }
                }
            }
        }

        int baseGridW = sum(colWidths);
        int baseGridH = sum(rowHeights);
        int measuredW = resolveSizeAndState(
                Math.max(baseGridW + hPad, getSuggestedMinimumWidth()), widthSpec, 0);
        int measuredH = resolveSizeAndState(
                Math.max(baseGridH + vPad, getSuggestedMinimumHeight()), heightSpec, 0);

        distributeWeights(colWidths, colWeights, (measuredW & MEASURED_SIZE_MASK) - hPad - baseGridW);
        distributeWeights(rowHeights, rowWeights, (measuredH & MEASURED_SIZE_MASK) - vPad - baseGridH);

        mColLocations = prefixSums(colWidths);
        mRowLocations = prefixSums(rowHeights);

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int c = mResolvedChildCols[i];
            int cSpan = Math.min(lp.columnSpec.spanSize, cols - c);
            int r = mResolvedChildRows[i];
            int rSpan = Math.min(lp.rowSpec.spanSize, rows - r);
            int cellW = mColLocations[c + cSpan] - mColLocations[c]
                    - getMarginValue(lp.leftMargin) - getMarginValue(lp.rightMargin);
            int cellH = mRowLocations[r + rSpan] - mRowLocations[r]
                    - getMarginValue(lp.topMargin) - getMarginValue(lp.bottomMargin);

            Alignment hAlign = lp.columnSpec.getAbsoluteAlignment(true);
            Alignment vAlign = lp.rowSpec.getAbsoluteAlignment(false);
            boolean fillW = hAlign == FILL || lp.width == LayoutParams.MATCH_PARENT;
            boolean fillH = vAlign == FILL || lp.height == LayoutParams.MATCH_PARENT;
            if (fillW || fillH) {
                int wSpec = fillW
                        ? MeasureSpec.makeMeasureSpec(Math.max(0, cellW), MeasureSpec.EXACTLY)
                        : MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(), MeasureSpec.EXACTLY);
                int hSpec = fillH
                        ? MeasureSpec.makeMeasureSpec(Math.max(0, cellH), MeasureSpec.EXACTLY)
                        : MeasureSpec.makeMeasureSpec(child.getMeasuredHeight(), MeasureSpec.EXACTLY);
                child.measure(wSpec, hSpec);
            }
        }

        setMeasuredDimension(measuredW, measuredH);
    }

    private static int sum(int[] values) {
        int s = 0;
        for (int i = 0; i < values.length; i++) {
            s += values[i];
        }
        return s;
    }

    private static void distributeWeights(int[] sizes, float[] weights, int delta) {
        if (delta == 0) {
            return;
        }
        float totalWeight = 0f;
        for (int i = 0; i < weights.length; i++) {
            totalWeight += weights[i];
        }
        if (totalWeight <= 0f) {
            return;
        }
        int remaining = delta;
        float remWeight = totalWeight;
        for (int i = 0; i < sizes.length; i++) {
            if (weights[i] > 0f && remWeight > 0f) {
                int share = Math.round(remaining * (weights[i] / remWeight));
                sizes[i] = Math.max(0, sizes[i] + share);
                remaining -= share;
                remWeight -= weights[i];
            }
        }
    }

    private static int[] prefixSums(int[] sizes) {
        int[] out = new int[sizes.length + 1];
        for (int i = 0; i < sizes.length; i++) {
            out[i + 1] = out[i] + sizes[i];
        }
        return out;
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int childCount = getChildCount();
        if (childCount == 0 || mColLocations == null || mRowLocations == null) {
            return;
        }
        int targetWidth = right - left;
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int paddingTop = getPaddingTop();
        boolean isLayoutRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;

        int cols = mColLocations.length - 1;
        int rows = mRowLocations.length - 1;

        for (int i = 0; i < childCount; i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) {
                continue;
            }
            LayoutParams lp = (LayoutParams) c.getLayoutParams();
            int col = mResolvedChildCols[i];
            int colSpan = Math.min(lp.columnSpec.spanSize, cols - col);
            int row = mResolvedChildRows[i];
            int rowSpan = Math.min(lp.rowSpec.spanSize, rows - row);

            int pWidth = c.getMeasuredWidth();
            int pHeight = c.getMeasuredHeight();

            int leftMargin = getMarginValue(lp.leftMargin);
            int rightMargin = getMarginValue(lp.rightMargin);
            int topMargin = getMarginValue(lp.topMargin);
            int bottomMargin = getMarginValue(lp.bottomMargin);

            int x1 = mColLocations[col];
            int x2 = mColLocations[col + colSpan];
            int y1 = mRowLocations[row];
            int y2 = mRowLocations[row + rowSpan];

            int cellWidth = Math.max(0, (x2 - x1) - leftMargin - rightMargin);
            int cellHeight = Math.max(0, (y2 - y1) - topMargin - bottomMargin);

            Alignment hAlign = lp.columnSpec.getAbsoluteAlignment(true);
            Alignment vAlign = lp.rowSpec.getAbsoluteAlignment(false);

            int width = hAlign.getSizeInCell(c, pWidth, cellWidth);
            int height = vAlign.getSizeInCell(c, pHeight, cellHeight);

            int dx = hAlign.getGravityOffset(c, cellWidth - width);
            int dy = vAlign.getGravityOffset(c, cellHeight - height);

            int cx = !isLayoutRtl
                    ? paddingLeft + x1 + leftMargin + dx
                    : targetWidth - width - paddingRight - x1 - rightMargin - dx;
            int cy = paddingTop + y1 + topMargin + dy;

            if (width != c.getMeasuredWidth() || height != c.getMeasuredHeight()) {
                c.measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }
            c.layout(cx, cy, cx + width, cy + height);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return GridLayout.class.getName();
    }
}
