// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;

import android.text.Layout;
import android.text.Spannable;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

/** Base MovementMethod dispatching directional and page key events to movement hooks. */
public class BaseMovementMethod implements MovementMethod {
    public BaseMovementMethod() {
    }

    @Override
    public boolean canSelectArbitrarily() {
        return false;
    }

    @Override
    public void initialize(TextView widget, Spannable text) {
    }

    @Override
    public boolean onKeyDown(TextView widget, Spannable text, int keyCode, KeyEvent event) {
        int movementMetaState = getMovementMetaState(text, event);
        boolean handled = handleMovementKey(widget, text, keyCode, movementMetaState, event);
        if (handled) {
            MetaKeyKeyListener.adjustMetaAfterKeypress(text);
            MetaKeyKeyListener.resetLockedMeta(text);
        }
        return handled;
    }

    @Override
    public boolean onKeyOther(TextView widget, Spannable text, KeyEvent event) {
        int movementMetaState = getMovementMetaState(text, event);
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_UNKNOWN
                && event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            int repeat = event.getRepeatCount();
            boolean handled = false;
            for (int i = 0; i < repeat; i++) {
                if (!handleMovementKey(widget, text, code, movementMetaState, event)) {
                    break;
                }
                handled = true;
            }
            if (handled) {
                MetaKeyKeyListener.adjustMetaAfterKeypress(text);
                MetaKeyKeyListener.resetLockedMeta(text);
            }
            return handled;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(TextView widget, Spannable text, int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public void onTakeFocus(TextView widget, Spannable text, int direction) {
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable text, MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTrackballEvent(TextView widget, Spannable text, MotionEvent event) {
        return false;
    }

    @Override
    public boolean onGenericMotionEvent(TextView widget, Spannable text, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            float hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
            boolean handled = false;
            if (hscroll < 0) {
                handled |= scrollLeft(widget, text, (int) Math.ceil(-hscroll));
            } else if (hscroll > 0) {
                handled |= scrollRight(widget, text, (int) Math.ceil(hscroll));
            }
            if (vscroll < 0) {
                handled |= scrollDown(widget, text, (int) Math.ceil(-vscroll));
            } else if (vscroll > 0) {
                handled |= scrollUp(widget, text, (int) Math.ceil(vscroll));
            }
            return handled;
        }
        return false;
    }

    protected int getMovementMetaState(Spannable buffer, KeyEvent event) {
        int metaState = MetaKeyKeyListener.getMetaState(buffer, event)
                & ~(MetaKeyKeyListener.META_ALT_LOCKED | MetaKeyKeyListener.META_SYM_LOCKED);
        return KeyEvent.normalizeMetaState(metaState) & ~KeyEvent.META_SHIFT_MASK;
    }

    protected boolean handleMovementKey(
            TextView widget, Spannable buffer, int keyCode, int movementMetaState, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    return left(widget, buffer);
                } else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_ALT_ON)) {
                    return lineStart(widget, buffer);
                }
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    return right(widget, buffer);
                } else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_ALT_ON)) {
                    return lineEnd(widget, buffer);
                }
                break;

            case KeyEvent.KEYCODE_DPAD_UP:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    return up(widget, buffer);
                } else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_ALT_ON)) {
                    return top(widget, buffer);
                } else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_CTRL_ON)) {
                    return previousParagraph(widget, buffer);
                }
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    return down(widget, buffer);
                } else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_ALT_ON)) {
                    return bottom(widget, buffer);
                } else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_CTRL_ON)) {
                    return nextParagraph(widget, buffer);
                }
                break;

            case KeyEvent.KEYCODE_PAGE_UP:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    return pageUp(widget, buffer);
                } else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_ALT_ON)) {
                    return top(widget, buffer);
                }
                break;

            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    return pageDown(widget, buffer);
                } else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_ALT_ON)) {
                    return bottom(widget, buffer);
                }
                break;

            case KeyEvent.KEYCODE_MOVE_HOME:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    return home(widget, buffer);
                } else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_CTRL_ON)) {
                    return top(widget, buffer);
                }
                break;

            case KeyEvent.KEYCODE_MOVE_END:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    return end(widget, buffer);
                } else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_CTRL_ON)) {
                    return bottom(widget, buffer);
                }
                break;
        }
        return false;
    }

    protected boolean left(TextView widget, Spannable buffer) { return false; }
    protected boolean right(TextView widget, Spannable buffer) { return false; }
    protected boolean up(TextView widget, Spannable buffer) { return false; }
    protected boolean down(TextView widget, Spannable buffer) { return false; }
    protected boolean pageUp(TextView widget, Spannable buffer) { return false; }
    protected boolean pageDown(TextView widget, Spannable buffer) { return false; }
    protected boolean top(TextView widget, Spannable buffer) { return false; }
    protected boolean bottom(TextView widget, Spannable buffer) { return false; }
    protected boolean lineStart(TextView widget, Spannable buffer) { return false; }
    protected boolean lineEnd(TextView widget, Spannable buffer) { return false; }
    protected boolean home(TextView widget, Spannable buffer) { return false; }
    protected boolean end(TextView widget, Spannable buffer) { return false; }
    public boolean previousParagraph(TextView widget, Spannable buffer) { return false; }
    public boolean nextParagraph(TextView widget, Spannable buffer) { return false; }

    private int getInnerWidth(TextView widget) {
        return Math.max(0, widget.getWidth() - widget.getTotalPaddingLeft() - widget.getTotalPaddingRight());
    }

    private int getInnerHeight(TextView widget) {
        return Math.max(0, widget.getHeight() - widget.getTotalPaddingTop() - widget.getTotalPaddingBottom());
    }

    boolean scrollLeft(TextView widget, Spannable buffer, int amount) {
        Layout layout = widget.getLayout();
        if (layout == null) return false;
        int minX = 0;
        for (int i = 0; i < layout.getLineCount(); i++) {
            minX = Math.min(minX, (int) Math.floor(layout.getLineLeft(i)));
        }
        if (widget.getScrollX() > minX) {
            int step = Math.max(1, (int) Math.ceil(widget.getPaint().getFontSpacing())) * amount;
            Touch.scrollTo(widget, layout, Math.max(minX, widget.getScrollX() - step), widget.getScrollY());
            return true;
        }
        return false;
    }

    boolean scrollRight(TextView widget, Spannable buffer, int amount) {
        Layout layout = widget.getLayout();
        if (layout == null) return false;
        int maxRight = 0;
        for (int i = 0; i < layout.getLineCount(); i++) {
            maxRight = Math.max(maxRight, (int) Math.ceil(layout.getLineRight(i)));
        }
        int maxScrollX = maxRight - getInnerWidth(widget);
        if (widget.getScrollX() < maxScrollX) {
            int step = Math.max(1, (int) Math.ceil(widget.getPaint().getFontSpacing())) * amount;
            Touch.scrollTo(widget, layout, Math.min(maxScrollX, widget.getScrollX() + step), widget.getScrollY());
            return true;
        }
        return false;
    }

    boolean scrollUp(TextView widget, Spannable buffer, int amount) {
        Layout layout = widget.getLayout();
        if (layout == null) return false;
        int top = widget.getScrollY();
        int topLine = layout.getLineForVertical(top);
        if (layout.getLineTop(topLine) == top) {
            topLine--;
        }
        if (topLine >= 0) {
            topLine = Math.max(topLine - amount + 1, 0);
            Touch.scrollTo(widget, layout, widget.getScrollX(), layout.getLineTop(topLine));
            return true;
        }
        return false;
    }

    boolean scrollDown(TextView widget, Spannable buffer, int amount) {
        Layout layout = widget.getLayout();
        if (layout == null) return false;
        int innerHeight = getInnerHeight(widget);
        int bottom = widget.getScrollY() + innerHeight;
        int bottomLine = layout.getLineForVertical(bottom);
        if (layout.getLineTop(bottomLine + 1) < bottom + 1) {
            bottomLine++;
        }
        int limit = layout.getLineCount() - 1;
        if (bottomLine <= limit) {
            bottomLine = Math.min(bottomLine + amount - 1, limit);
            Touch.scrollTo(widget, layout, widget.getScrollX(),
                    Math.max(0, layout.getLineTop(bottomLine + 1) - innerHeight));
            return true;
        }
        return false;
    }

    boolean scrollPageUp(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        if (layout == null) return false;
        int top = widget.getScrollY() - getInnerHeight(widget);
        int topLine = layout.getLineForVertical(top);
        if (topLine >= 0) {
            Touch.scrollTo(widget, layout, widget.getScrollX(), layout.getLineTop(topLine));
            return true;
        }
        return false;
    }

    boolean scrollPageDown(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        if (layout == null) return false;
        int innerHeight = getInnerHeight(widget);
        int bottom = widget.getScrollY() + innerHeight + innerHeight;
        int bottomLine = layout.getLineForVertical(bottom);
        if (bottomLine <= layout.getLineCount() - 1) {
            Touch.scrollTo(widget, layout, widget.getScrollX(),
                    Math.max(0, layout.getLineTop(bottomLine + 1) - innerHeight));
            return true;
        }
        return false;
    }

    boolean scrollTop(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        if (layout == null) return false;
        if (layout.getLineForVertical(widget.getScrollY()) >= 0 && widget.getScrollY() > 0) {
            Touch.scrollTo(widget, layout, widget.getScrollX(), layout.getLineTop(0));
            return true;
        }
        return false;
    }

    boolean scrollBottom(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        if (layout == null) return false;
        int lineCount = layout.getLineCount();
        int target = Math.max(0, layout.getLineTop(lineCount) - getInnerHeight(widget));
        if (widget.getScrollY() < target) {
            Touch.scrollTo(widget, layout, widget.getScrollX(), target);
            return true;
        }
        return false;
    }

    boolean scrollLineStart(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        if (layout == null) return false;
        if (widget.getScrollX() != 0) {
            Touch.scrollTo(widget, layout, 0, widget.getScrollY());
            return true;
        }
        return false;
    }

    boolean scrollLineEnd(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        if (layout == null) return false;
        int maxRight = 0;
        for (int i = 0; i < layout.getLineCount(); i++) {
            maxRight = Math.max(maxRight, (int) Math.ceil(layout.getLineRight(i)));
        }
        int target = Math.max(0, maxRight - getInnerWidth(widget));
        if (widget.getScrollX() < target) {
            Touch.scrollTo(widget, layout, target, widget.getScrollY());
            return true;
        }
        return false;
    }
}
