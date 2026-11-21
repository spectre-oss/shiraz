// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.input;

import android.os.Handler;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VerifiedInputEvent;
import android.view.VerifiedKeyEvent;
import android.view.VerifiedMotionEvent;
import java.util.IdentityHashMap;
import java.util.Objects;

/** Fixed aggregate SDL device registry; host peripheral hotplug does not change its identity. */
public final class InputManager {
    public static final String ACTION_QUERY_KEYBOARD_LAYOUTS =
            "android.hardware.input.action.QUERY_KEYBOARD_LAYOUTS";
    public static final String META_DATA_KEYBOARD_LAYOUTS =
            "android.hardware.input.metadata.KEYBOARD_LAYOUTS";

    private static final InputManager HOST = new InputManager();
    private final IdentityHashMap<InputDeviceListener, Handler> listeners =
            new IdentityHashMap<InputDeviceListener, Handler>();

    private InputManager() {
    }

    public static InputManager getInstance() {
        return HOST;
    }

    public int[] getInputDeviceIds() {
        return InputDevice.getDeviceIds();
    }

    public InputDevice getInputDevice(int id) {
        return InputDevice.getDevice(id);
    }

    public InputDevice.ViewBehavior getInputDeviceViewBehavior(int deviceId) {
        InputDevice device = getInputDevice(deviceId);
        return device == null ? null : device.getViewBehavior();
    }

    public synchronized void registerInputDeviceListener(
            InputDeviceListener listener, Handler handler) {
        if (listener == null) throw new IllegalArgumentException("listener");
        if (!listeners.containsKey(listener)) {
            listeners.put(listener, handler == null ? new Handler() : handler);
        }
    }

    public synchronized void unregisterInputDeviceListener(InputDeviceListener listener) {
        if (listener == null) throw new IllegalArgumentException("listener");
        listeners.remove(listener);
    }

    public float getMaximumObscuringOpacityForTouch() {
        return 0.8f;
    }

    public VerifiedInputEvent verifyInputEvent(InputEvent event) {
        Objects.requireNonNull(event, "event");
        if (event instanceof KeyEvent) {
            KeyEvent ke = (KeyEvent) event;
            return new VerifiedKeyEvent(
                    ke.getDeviceId(),
                    ke.getEventTime() * 1000000L,
                    ke.getSource(),
                    Display.DEFAULT_DISPLAY,
                    ke.getAction(),
                    ke.getDownTime() * 1000000L,
                    ke.getFlags(),
                    ke.getKeyCode(),
                    ke.getScanCode(),
                    ke.getMetaState(),
                    ke.getRepeatCount());
        } else if (event instanceof MotionEvent) {
            MotionEvent me = (MotionEvent) event;
            return new VerifiedMotionEvent(
                    me.getDeviceId(),
                    me.getEventTime() * 1000000L,
                    me.getSource(),
                    Display.DEFAULT_DISPLAY,
                    me.getRawX(),
                    me.getRawY(),
                    me.getActionMasked(),
                    me.getDownTime() * 1000000L,
                    me.getFlags(),
                    me.getMetaState(),
                    me.getButtonState());
        }
        return null;
    }

    public boolean isStylusPointerIconEnabled() {
        return true;
    }

    public HostUsiVersion getHostUsiVersion(Display display) {
        Objects.requireNonNull(display, "display should not be null");
        return null;
    }

    public interface InputDeviceListener {
        void onInputDeviceAdded(int deviceId);

        void onInputDeviceRemoved(int deviceId);

        void onInputDeviceChanged(int deviceId);
    }
}
