// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;

import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/** MovementMethod supporting cursor positioning and Shift/touch selection. */
public class ArrowKeyMovementMethod extends BaseMovementMethod implements MovementMethod {
    private static ArrowKeyMovementMethod sInstance;

    public ArrowKeyMovementMethod() {
    }

    private static boolean isSelecting(Spannable buffer) {
        return MetaKeyKeyListener.getMetaState(buffer, KeyEvent.META_SHIFT_ON) == 1
                || MetaKeyKeyListener.getMetaState(buffer, MetaKeyKeyListener.META_SELECTING) != 0;
    }

    private static void moveOrExtend(Spannable buffer, int newEnd, boolean cap) {
        int clamped = Math.max(0, Math.min(buffer.length(), newEnd));
        if (cap) {
            int start = Selection.getSelectionStart(buffer);
            if (start < 0) start = clamped;
            Selection.setSelection(buffer, start, clamped);
        } else {
            Selection.setSelection(buffer, clamped);
        }
    }

    private static int currentEnd(Spannable buffer) {
        int end = Selection.getSelectionEnd(buffer);
        if (end >= 0) return end;
        int start = Selection.getSelectionStart(buffer);
        return start >= 0 ? start : 0;
    }

    @Override
    protected boolean handleMovementKey(
            TextView widget, Spannable buffer, int keyCode, int movementMetaState, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getRepeatCount() == 0
                        && MetaKeyKeyListener.getMetaState(
                                buffer, MetaKeyKeyListener.META_SELECTING, event) != 0) {
                    return widget.showContextMenu();
                }
                break;
        }
        return super.handleMovementKey(widget, buffer, keyCode, movementMetaState, event);
    }

    @Override
    protected boolean left(TextView widget, Spannable buffer) {
        int cur = currentEnd(buffer);
        if (cur <= 0 && !isSelecting(buffer)) {
            return false;
        }
        int next = cur > 0 ? TextUtils.getOffsetBefore(buffer, cur) : 0;
        moveOrExtend(buffer, next, isSelecting(buffer));
        return true;
    }

    @Override
    protected boolean right(TextView widget, Spannable buffer) {
        int cur = currentEnd(buffer);
        if (cur >= buffer.length() && !isSelecting(buffer)) {
            return false;
        }
        int next = cur < buffer.length() ? TextUtils.getOffsetAfter(buffer, cur) : buffer.length();
        moveOrExtend(buffer, next, isSelecting(buffer));
        return true;
    }

    @Override
    protected boolean up(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        int cur = currentEnd(buffer);
        if (layout != null) {
            int line = layout.getLineForOffset(cur);
            if (line > 0) {
                float h = layout.getPrimaryHorizontal(cur);
                int target = layout.getOffsetForHorizontal(line - 1, h);
                moveOrExtend(buffer, target, isSelecting(buffer));
                return true;
            }
        }
        int prevNl = TextUtils.lastIndexOf(buffer, '\n', Math.max(0, cur - 1));
        if (prevNl >= 0) {
            moveOrExtend(buffer, prevNl, isSelecting(buffer));
            return true;
        }
        return false;
    }

    @Override
    protected boolean down(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        int cur = currentEnd(buffer);
        if (layout != null) {
            int line = layout.getLineForOffset(cur);
            if (line < layout.getLineCount() - 1) {
                float h = layout.getPrimaryHorizontal(cur);
                int target = layout.getOffsetForHorizontal(line + 1, h);
                moveOrExtend(buffer, target, isSelecting(buffer));
                return true;
            }
        }
        int nextNl = TextUtils.indexOf(buffer, '\n', cur);
        if (nextNl >= 0 && nextNl + 1 <= buffer.length()) {
            moveOrExtend(buffer, nextNl + 1, isSelecting(buffer));
            return true;
        }
        return false;
    }

    @Override
    protected boolean pageUp(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        if (layout == null) return top(widget, buffer);
        int cur = currentEnd(buffer);
        int curLine = layout.getLineForOffset(cur);
        int pageH = Math.max(1, widget.getHeight() - widget.getTotalPaddingTop() - widget.getTotalPaddingBottom());
        int targetY = Math.max(0, layout.getLineTop(curLine) - pageH);
        int targetLine = layout.getLineForVertical(targetY);
        if (targetLine == curLine && curLine == 0) return false;
        int target = layout.getOffsetForHorizontal(targetLine, layout.getPrimaryHorizontal(cur));
        moveOrExtend(buffer, target, isSelecting(buffer));
        return true;
    }

    @Override
    protected boolean pageDown(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        if (layout == null) return bottom(widget, buffer);
        int cur = currentEnd(buffer);
        int curLine = layout.getLineForOffset(cur);
        int pageH = Math.max(1, widget.getHeight() - widget.getTotalPaddingTop() - widget.getTotalPaddingBottom());
        int targetY = Math.min(layout.getHeight(), layout.getLineBottom(curLine) + pageH);
        int targetLine = layout.getLineForVertical(targetY);
        if (targetLine == curLine && curLine == layout.getLineCount() - 1) return false;
        int target = layout.getOffsetForHorizontal(targetLine, layout.getPrimaryHorizontal(cur));
        moveOrExtend(buffer, target, isSelecting(buffer));
        return true;
    }

    @Override
    protected boolean top(TextView widget, Spannable buffer) {
        moveOrExtend(buffer, 0, isSelecting(buffer));
        return true;
    }

    @Override
    protected boolean bottom(TextView widget, Spannable buffer) {
        moveOrExtend(buffer, buffer.length(), isSelecting(buffer));
        return true;
    }

    @Override
    protected boolean lineStart(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        int cur = currentEnd(buffer);
        if (layout != null) {
            int line = layout.getLineForOffset(cur);
            moveOrExtend(buffer, layout.getLineStart(line), isSelecting(buffer));
            return true;
        }
        int start = cur > 0 ? TextUtils.lastIndexOf(buffer, '\n', cur - 1) + 1 : 0;
        moveOrExtend(buffer, start, isSelecting(buffer));
        return true;
    }

    @Override
    protected boolean lineEnd(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        int cur = currentEnd(buffer);
        if (layout != null) {
            int line = layout.getLineForOffset(cur);
            moveOrExtend(buffer, layout.getLineVisibleEnd(line), isSelecting(buffer));
            return true;
        }
        int end = TextUtils.indexOf(buffer, '\n', cur);
        if (end < 0) end = buffer.length();
        moveOrExtend(buffer, end, isSelecting(buffer));
        return true;
    }

    @Override
    protected boolean home(TextView widget, Spannable buffer) {
        return lineStart(widget, buffer);
    }

    @Override
    protected boolean end(TextView widget, Spannable buffer) {
        return lineEnd(widget, buffer);
    }

    @Override
    public boolean previousParagraph(TextView widget, Spannable buffer) {
        int cur = currentEnd(buffer);
        if (cur <= 0) return false;
        int prev = TextUtils.lastIndexOf(buffer, '\n', Math.max(0, cur - 2));
        moveOrExtend(buffer, prev < 0 ? 0 : prev + 1, isSelecting(buffer));
        return true;
    }

    @Override
    public boolean nextParagraph(TextView widget, Spannable buffer) {
        int cur = currentEnd(buffer);
        if (cur >= buffer.length()) return false;
        int next = TextUtils.indexOf(buffer, '\n', cur);
        moveOrExtend(buffer, next < 0 ? buffer.length() : Math.min(buffer.length(), next + 1), isSelecting(buffer));
        return true;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int initialScrollX = -1;
        int initialScrollY = -1;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP) {
            initialScrollX = Touch.getInitialScrollX(widget, buffer);
            initialScrollY = Touch.getInitialScrollY(widget, buffer);
        }
        boolean wasTouchando = Touch.onTouchEvent(widget, buffer, event);
        if (widget.isFocused()) {
            if (action == MotionEvent.ACTION_DOWN) {
                if (isSelecting(buffer)) {
                    int offset = widget.getOffsetForPosition(event.getX(), event.getY());
                    Selection.setSelection(buffer, Selection.getSelectionStart(buffer), offset);
                    return true;
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (isSelecting(buffer) && wasTouchando) {
                    widget.cancelLongPress();
                    int offset = widget.getOffsetForPosition(event.getX(), event.getY());
                    Selection.extendSelection(buffer, offset);
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP) {
                if ((initialScrollY >= 0 && initialScrollY != widget.getScrollY())
                        || (initialScrollX >= 0 && initialScrollX != widget.getScrollX())) {
                    return true;
                }
                int offset = widget.getOffsetForPosition(event.getX(), event.getY());
                moveOrExtend(buffer, offset, isSelecting(buffer));
                MetaKeyKeyListener.adjustMetaAfterKeypress(buffer);
                MetaKeyKeyListener.resetLockedMeta(buffer);
                return true;
            }
        }
        return wasTouchando;
    }

    @Override
    public boolean canSelectArbitrarily() {
        return true;
    }

    @Override
    public void initialize(TextView widget, Spannable text) {
        Selection.setSelection(text, 0);
    }

    @Override
    public void onTakeFocus(TextView view, Spannable text, int dir) {
        if ((dir & (View.FOCUS_FORWARD | View.FOCUS_DOWN)) != 0) {
            if (view.getLayout() == null) {
                Selection.setSelection(text, text.length());
            }
        } else {
            Selection.setSelection(text, text.length());
        }
    }

    public static MovementMethod getInstance() {
        if (sInstance == null) {
            sInstance = new ArrowKeyMovementMethod();
        }
        return sInstance;
    }
}
