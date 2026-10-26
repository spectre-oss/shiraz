// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.view.*;
import android.os.Looper;

/** Compiled against the stock SDK; physical input arrives through the SDL window. */
final class KeyboardFixture {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void clusterXml(Context context){
        int id=context.getResources().getIdentifier("keyboard_clusters","layout",context.getPackageName());
        ViewGroup root=(ViewGroup)LayoutInflater.from(context).inflate(id,null,false);ViewGroup first=(ViewGroup)root.getChildAt(0);View second=root.getChildAt(1);
        check(first.getTouchscreenBlocksFocus()&&first.isKeyboardNavigationCluster()&&second.isKeyboardNavigationCluster()&&first.getNextClusterForwardId()==second.getId(),"compiled cluster attributes");
        check(first.getChildAt(0).isFocusedByDefault()&&first.restoreDefaultFocus()&&first.getChildAt(0).hasFocus(),"compiled default focus");
    }
    static void stateChecks(Context context) {
        KeyEvent down = new KeyEvent(10,20,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_A,0,KeyEvent.META_CTRL_ON,7,30,0,0x101);
        check(down.getDownTime() == 10 && down.getEventTime() == 20 && down.getScanCode() == 30 && down.getDeviceId() == 7 && down.isFromSource(0x101) && down.isCtrlPressed(), "key metadata");
        check(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK).isSystem() && !KeyEvent.changeFlags(down,KeyEvent.FLAG_FROM_SYSTEM).isSystem(), "system-key classification is independent of event origin");
        check(KeyEvent.isMediaSessionKey(KeyEvent.KEYCODE_MEDIA_PLAY) && !KeyEvent.isMediaSessionKey(KeyEvent.KEYCODE_A), "media-key classification");
        check("KEYCODE_A".equals(KeyEvent.keyCodeToString(KeyEvent.KEYCODE_A)) && KeyEvent.keyCodeFromString("KEYCODE_A") == KeyEvent.KEYCODE_A && KeyEvent.keyCodeFromString("29") == KeyEvent.KEYCODE_A, "symbolic key values");
        check(KeyEvent.metaStateHasModifiers(KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CAPS_LOCK_ON, KeyEvent.META_CTRL_ON) && KeyEvent.metaStateHasNoModifiers(KeyEvent.META_CAPS_LOCK_ON), "modifier normalization and locks");
        try { KeyEvent.metaStateHasModifiers(0,KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON); throw new AssertionError("contradictory modifiers accepted"); } catch (IllegalArgumentException expected) { }
        final StringBuilder order = new StringBuilder();
        KeyEvent.Callback callback = new KeyEvent.Callback() {
            public boolean onKeyDown(int code, KeyEvent event) { order.append('D'); if (event.getRepeatCount() == 0) event.startTracking(); return true; }
            public boolean onKeyLongPress(int code, KeyEvent event) { order.append('L'); return true; }
            public boolean onKeyUp(int code, KeyEvent event) { order.append('U'); return true; }
            public boolean onKeyMultiple(int code, int count, KeyEvent event) { order.append('M'); return false; }
        };
        KeyEvent.DispatcherState state = new KeyEvent.DispatcherState(); Object target = new Object();
        down.dispatch(callback,state,target);
        KeyEvent repeat = KeyEvent.changeTimeRepeat(down,600,1,KeyEvent.FLAG_LONG_PRESS); repeat.dispatch(callback,state,target);
        KeyEvent up = KeyEvent.changeAction(down,KeyEvent.ACTION_UP); up.dispatch(callback,state,target);
        check("DDLU".equals(order.toString()) && up.isTracking() && up.isCanceled() && (up.getFlags() & KeyEvent.FLAG_CANCELED_LONG_PRESS) != 0, "tracked long press cancels normal release");
        order.setLength(0); KeyEvent multiple = new KeyEvent(1,2,KeyEvent.ACTION_MULTIPLE,KeyEvent.KEYCODE_B,3);
        check(multiple.dispatch(callback) && "MDU".equals(order.toString()) && multiple.getAction() == KeyEvent.ACTION_MULTIPLE && multiple.getRepeatCount() == 3, "multiple-event callback fallback and restoration");
        KeyEvent text = new KeyEvent(42,"é😀",0,0); order.setLength(0);
        check(!text.dispatch(callback) && "M".equals(order.toString()) && "é😀".equals(text.getCharacters()), "committed text is not fabricated physical keys");
        KeyEvent copy = KeyEvent.changeFlags(new KeyEvent(down),KeyEvent.FLAG_CANCELED); check(copy.isCanceled() && !down.isCanceled(), "key copy owns flags");
        final int[] clicks = {0}, intercepted = {0}; View view = new View(context); view.layout(0,0,100,100); view.setFocusable(true); check(view.requestFocus(), "single view focus");
        view.setOnClickListener(new View.OnClickListener() { public void onClick(View target) { ++clicks[0]; } });
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER)); check(view.isPressed(), "keyboard pressed state");
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER)); check(clicks[0] == 1 && !view.isPressed(), "keyboard click on release");
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER)); view.dispatchKeyEvent(KeyEvent.changeFlags(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER),KeyEvent.FLAG_CANCELED));
        check(clicks[0] == 1 && !view.isPressed(), "canceled key cannot click");
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER)); view.setClickable(false); view.setClickable(true); view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));
        check(clicks[0] == 1 && !view.isPressed(), "changing clickability cancels keyboard activation");
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER)); view.setPressed(false); view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));
        check(clicks[0] == 1, "cleared pressed state cannot click on key release");
        view.setOnKeyListener(new View.OnKeyListener() { public boolean onKey(View target, int key, KeyEvent event) { ++intercepted[0]; return true; } });
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER)); check(intercepted[0] == 1 && !view.isPressed(), "key listener precedes default view callbacks");
        view.setEnabled(false); check(!view.hasFocus(), "disabled view clears focus");
        System.out.println("SHIRAZ_FIXTURE_KEY_VALUES_OK");
    }
    private static final int[] ACTIONS = {0,0,1,2,0,1,0,0,1,1,0,1};
    private static final int[] CODES = {29,29,29,0,111,111,59,30,30,59,66,66};
    private static final int[] SCANS = {30,30,30,0,1,1,42,48,48,42,28,28};
    private final long[] down = new long[128];
    private int events;
    private long last;
    private android.widget.EditText editor;
    private android.widget.CompoundButton toggle;
    void attach(final View view) {
        editor=new android.widget.EditText(view.getContext());editor.requestFocus();
        toggle=new android.widget.CompoundButton(view.getContext()){};toggle.setClickable(true);toggle.setFocusable(true);toggle.requestFocus();
        view.setFocusableInTouchMode(true); check(view.requestFocus(), "content keyboard focus");
        view.setOnKeyListener(new View.OnKeyListener() { public boolean onKey(View target, int code, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_MULTIPLE && "1".equals(System.getenv("SHIRAZ_FIXTURE_TEXT_FAIL"))) throw new AssertionError("SHIRAZ_FIXTURE_EXPECTED_TEXT_FAILURE");
            if (event.getAction() != KeyEvent.ACTION_MULTIPLE && "1".equals(System.getenv("SHIRAZ_FIXTURE_KEY_FAIL"))) throw new AssertionError("SHIRAZ_FIXTURE_EXPECTED_KEY_FAILURE");
            check(Looper.myLooper() == Looper.getMainLooper() && events < ACTIONS.length && event.getAction() == ACTIONS[events] && code == CODES[events], "native key ordering");
            check(event.getScanCode() == SCANS[events] && event.getDeviceId() == 0 && event.getSource() == 0x101 && event.getEventTime() >= last, "host key metadata/clock");
            last = event.getEventTime();
            if (event.getAction() == KeyEvent.ACTION_MULTIPLE) check("é😀".equals(event.getCharacters()) && event.getDownTime() == event.getEventTime(), "UTF-8 committed text preserves surrogate pairs");
            else {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) down[code] = event.getDownTime();
                check(down[code] == event.getDownTime() && event.getEventTime() >= event.getDownTime(), "captured key down time");
            }
            check(event.getRepeatCount() == (events == 1 ? 1 : 0), "native repeat count");
            check(event.isCanceled() == (events == 8 || events == 9), "focus cancellation flags");
            if (events <= 1) check(event.hasModifiers(KeyEvent.META_CTRL_ON), "left Ctrl native mapping");
            if (events == 6 || events == 7) check(event.hasModifiers(KeyEvent.META_SHIFT_ON), "left Shift native mapping");
            if(event.getAction()==KeyEvent.ACTION_MULTIPLE)check(editor.dispatchKeyEvent(event),"Native text reaches editable buffer");
            if(code==KeyEvent.KEYCODE_ENTER)toggle.dispatchKeyEvent(event);
            ++events; return true;
        } });
    }
    void verify() { check(toggle.isChecked(),"Native enter toggles compound button");check(editor.getText().toString().equals("é😀"),"Native committed text edited widget");check(events == ACTIONS.length, "all native keys and committed text delivered"); System.out.println("SHIRAZ_FIXTURE_KEYS_OK"); }
}
