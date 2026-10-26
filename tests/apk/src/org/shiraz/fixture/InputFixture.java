// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

final class InputFixture {
    static final boolean ENABLED = "1".equals(System.getenv("SHIRAZ_FIXTURE_INPUT"));
    private static final int[] ACTIONS = {0, 2, 1, 0, 3, 0, 2, 1};
    private static final float[] X = {16, 40, 40, 20, 20, 48, 67.2f, 67.2f}, Y = {24, 60, 60, 30, 30, 80, 96, 96};
    int events, clicks;
    long downTime, lastTime;
    static { if (ENABLED) System.loadLibrary("shiraz-test-input"); }
    static native void inject();
    static void stateChecks(android.content.Context context) {
        final int[] clicked = {0};
        View view = new View(context); view.layout(0, 0, 100, 200);
        view.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View target) { ++clicked[0]; } });
        send(view, MotionEvent.ACTION_DOWN, 10, 20);
        if (!view.isPressed()) throw new AssertionError("Click press state");
        send(view, MotionEvent.ACTION_CANCEL, 10, 20);
        if (view.isPressed() || clicked[0] != 0) throw new AssertionError("Canceled click");
        send(view, MotionEvent.ACTION_DOWN, 10, 20); send(view, MotionEvent.ACTION_MOVE, -1, 20); send(view, MotionEvent.ACTION_UP, 10, 20);
        if (clicked[0] != 0 || view.isPressed()) throw new AssertionError("Drag outside rearmed a click");
        send(view, MotionEvent.ACTION_DOWN, 10, 20); view.setEnabled(false); send(view, MotionEvent.ACTION_UP, 10, 20);
        if (clicked[0] != 0 || view.isPressed()) throw new AssertionError("Disabled view clicked");
        view.setEnabled(true);
        view.setOnTouchListener(new View.OnTouchListener() { @Override public boolean onTouch(View target, MotionEvent event) { return true; } });
        send(view, MotionEvent.ACTION_DOWN, 10, 20); send(view, MotionEvent.ACTION_UP, 10, 20);
        if (clicked[0] != 0) throw new AssertionError("Consumed touch clicked");
        view.setOnTouchListener(null);
        send(view, MotionEvent.ACTION_DOWN, 10, 20); send(view, MotionEvent.ACTION_UP, 10, 20);
        if (clicked[0] != 1 || view.isPressed()) throw new AssertionError("Valid click not delivered");
        MotionEvent event = MotionEvent.obtain(10, 20, MotionEvent.ACTION_DOWN, 1, 2, .5f, .3f, 7, 2, 3, 4, 1);
        if (event.getDownTime() != 10 || event.getEventTime() != 20 || event.getPressure() != .5f || event.getSize() != .3f ||
            event.getMetaState() != 7 || event.getXPrecision() != 2 || event.getYPrecision() != 3 || event.getDeviceId() != 4 || event.getEdgeFlags() != 1 ||
            event.getHistorySize() != 0 || event.findPointerIndex(5) != -1) throw new AssertionError("MotionEvent fields");
        try { event.getX(1); throw new AssertionError("Absent pointer accepted"); } catch (IllegalArgumentException expected) { }
        try { event.setAction(MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT)); throw new AssertionError("Absent action pointer accepted"); } catch (IllegalArgumentException expected) { }
        event.setLocation(5, 6);
        if (event.getX() != 5 || event.getY() != 6 || event.getRawX() != 1 || event.getRawY() != 2) throw new AssertionError("Local/raw coordinates");
        event.recycle();
    }
    private static void send(View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(10, 20, action, x, y, 0);
        try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
    }
    void attach(final View view) {
        view.setOnTouchListener(new View.OnTouchListener() { @Override public boolean onTouch(View target, MotionEvent event) {
            if (System.getenv("SHIRAZ_FIXTURE_POINTER_FAIL") != null) throw new AssertionError("SHIRAZ_FIXTURE_EXPECTED_POINTER_FAILURE");
            if (Looper.myLooper() != Looper.getMainLooper() || target != view || events >= ACTIONS.length || event.getActionMasked() != ACTIONS[events])
                throw new AssertionError("Pointer order/dispatch thread");
            if (Math.abs(event.getX() - X[events]) > .01 || Math.abs(event.getY() - Y[events]) > .01 || event.getPointerCount() != 1 || event.getPointerId(0) != 0)
                throw new AssertionError("Pointer geometry");
            int source = events < 5 ? 0x2002 : 0x1002;
            if (!event.isFromSource(source) || event.getSource() != source || event.getToolType(0) != (events < 5 ? MotionEvent.TOOL_TYPE_MOUSE : MotionEvent.TOOL_TYPE_FINGER))
                throw new AssertionError("Pointer source/tool");
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) downTime = event.getDownTime();
            if (event.getDownTime() != downTime || event.getEventTime() < downTime || event.getEventTime() < lastTime) throw new AssertionError("Pointer timestamps");
            if (events == 2 && event.getEventTime() - downTime < 5) throw new AssertionError("SDL input timing was replaced with delivery time");
            lastTime = event.getEventTime();
            MotionEvent copy = MotionEvent.obtain(event);
            copy.offsetLocation(2, -3);
            if (Math.abs(copy.getRawX() - event.getRawX()) > .01 || Math.abs(copy.getX() - event.getX() - 2) > .01) throw new AssertionError("Copied event coordinates");
            copy.recycle();
            try { copy.getX(); throw new AssertionError("Recycled event accepted"); } catch (IllegalStateException expected) { }
            ++events; return false;
        } });
        view.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View target) { ++clicks; view.invalidate(); } });
    }
    void verify(View view) {
        if (events != ACTIONS.length || clicks != 2 || view.isPressed()) throw new AssertionError("Pointer delivery/click/cancel state");
        System.out.println("SHIRAZ_FIXTURE_INPUT_OK");
    }
}
