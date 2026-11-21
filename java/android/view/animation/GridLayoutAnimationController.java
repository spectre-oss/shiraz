// SPDX-License-Identifier: GPL-3.0-only

package android.view.animation;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class GridLayoutAnimationController extends LayoutAnimationController {
    public static final int DIRECTION_LEFT_TO_RIGHT = 0;
    public static final int DIRECTION_RIGHT_TO_LEFT = 1;
    public static final int DIRECTION_TOP_TO_BOTTOM = 0;
    public static final int DIRECTION_BOTTOM_TO_TOP = 2;
    public static final int DIRECTION_HORIZONTAL_MASK = 1;
    public static final int DIRECTION_VERTICAL_MASK = 2;

    public static final int PRIORITY_NONE = 0;
    public static final int PRIORITY_COLUMN = 1;
    public static final int PRIORITY_ROW = 2;

    private float mColumnDelay;
    private float mRowDelay;
    private int mDirection = DIRECTION_LEFT_TO_RIGHT | DIRECTION_TOP_TO_BOTTOM;
    private int mDirectionPriority = PRIORITY_NONE;

    public GridLayoutAnimationController(Context context, AttributeSet attrs) {
        super(context, attrs);
        mColumnDelay = 0.5f;
        mRowDelay = 0.5f;
    }

    public GridLayoutAnimationController(Animation animation) {
        this(animation, 0.5f, 0.5f);
    }

    public GridLayoutAnimationController(Animation animation, float columnDelay, float rowDelay) {
        super(animation);
        mColumnDelay = columnDelay;
        mRowDelay = rowDelay;
    }

    public float getColumnDelay() {
        return mColumnDelay;
    }

    public void setColumnDelay(float columnDelay) {
        mColumnDelay = columnDelay;
    }

    public float getRowDelay() {
        return mRowDelay;
    }

    public void setRowDelay(float rowDelay) {
        mRowDelay = rowDelay;
    }

    public int getDirection() {
        return mDirection;
    }

    public void setDirection(int direction) {
        mDirection = direction;
    }

    public int getDirectionPriority() {
        return mDirectionPriority;
    }

    public void setDirectionPriority(int directionPriority) {
        mDirectionPriority = directionPriority;
    }

    @Override
    public boolean willOverlap() {
        return mColumnDelay < 1.0f || mRowDelay < 1.0f;
    }

    @Override
    protected long getDelayForView(View view) {
        return 0L;
    }

    public static class AnimationParameters extends LayoutAnimationController.AnimationParameters {
        public int column;
        public int row;
        public int columnsCount;
        public int rowsCount;

        public AnimationParameters() {
        }
    }
}
