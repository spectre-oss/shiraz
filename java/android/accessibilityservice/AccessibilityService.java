// SPDX-License-Identifier: GPL-3.0-only
package android.accessibilityservice;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.ColorSpace;
import android.graphics.Region;
import android.hardware.HardwareBuffer;
import android.hardware.usb.UsbDevice;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.util.SparseArray;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public abstract class AccessibilityService extends Service {
    public static final int GESTURE_UNKNOWN = 0;
    public static final int GESTURE_SWIPE_UP = 1;
    public static final int GESTURE_SWIPE_DOWN = 2;
    public static final int GESTURE_SWIPE_LEFT = 3;
    public static final int GESTURE_SWIPE_RIGHT = 4;
    public static final int GESTURE_SWIPE_LEFT_AND_RIGHT = 5;
    public static final int GESTURE_SWIPE_RIGHT_AND_LEFT = 6;
    public static final int GESTURE_SWIPE_UP_AND_DOWN = 7;
    public static final int GESTURE_SWIPE_DOWN_AND_UP = 8;
    public static final int GESTURE_SWIPE_LEFT_AND_UP = 9;
    public static final int GESTURE_SWIPE_LEFT_AND_DOWN = 10;
    public static final int GESTURE_SWIPE_RIGHT_AND_UP = 11;
    public static final int GESTURE_SWIPE_RIGHT_AND_DOWN = 12;
    public static final int GESTURE_SWIPE_UP_AND_LEFT = 13;
    public static final int GESTURE_SWIPE_UP_AND_RIGHT = 14;
    public static final int GESTURE_SWIPE_DOWN_AND_LEFT = 15;
    public static final int GESTURE_SWIPE_DOWN_AND_RIGHT = 16;
    public static final int GESTURE_DOUBLE_TAP = 17;
    public static final int GESTURE_DOUBLE_TAP_AND_HOLD = 18;
    public static final int GESTURE_2_FINGER_SINGLE_TAP = 19;
    public static final int GESTURE_2_FINGER_DOUBLE_TAP = 20;
    public static final int GESTURE_2_FINGER_TRIPLE_TAP = 21;
    public static final int GESTURE_3_FINGER_SINGLE_TAP = 22;
    public static final int GESTURE_3_FINGER_DOUBLE_TAP = 23;
    public static final int GESTURE_3_FINGER_TRIPLE_TAP = 24;
    public static final int GESTURE_2_FINGER_SWIPE_UP = 25;
    public static final int GESTURE_2_FINGER_SWIPE_DOWN = 26;
    public static final int GESTURE_2_FINGER_SWIPE_LEFT = 27;
    public static final int GESTURE_2_FINGER_SWIPE_RIGHT = 28;
    public static final int GESTURE_3_FINGER_SWIPE_UP = 29;
    public static final int GESTURE_3_FINGER_SWIPE_DOWN = 30;
    public static final int GESTURE_3_FINGER_SWIPE_LEFT = 31;
    public static final int GESTURE_3_FINGER_SWIPE_RIGHT = 32;
    public static final int GESTURE_4_FINGER_SWIPE_UP = 33;
    public static final int GESTURE_4_FINGER_SWIPE_DOWN = 34;
    public static final int GESTURE_4_FINGER_SWIPE_LEFT = 35;
    public static final int GESTURE_4_FINGER_SWIPE_RIGHT = 36;
    public static final int GESTURE_4_FINGER_SINGLE_TAP = 37;
    public static final int GESTURE_4_FINGER_DOUBLE_TAP = 38;
    public static final int GESTURE_4_FINGER_TRIPLE_TAP = 39;
    public static final int GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD = 40;
    public static final int GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD = 41;
    public static final int GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD = 42;
    public static final int GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD = 43;
    public static final int GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD = 44;
    public static final int GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD = 45;

    public static final String SERVICE_INTERFACE =
            "android.accessibilityservice.AccessibilityService";
    public static final String SERVICE_META_DATA = "android.accessibilityservice";

    public static final int GLOBAL_ACTION_BACK = 1;
    public static final int GLOBAL_ACTION_HOME = 2;
    public static final int GLOBAL_ACTION_RECENTS = 3;
    public static final int GLOBAL_ACTION_NOTIFICATIONS = 4;
    public static final int GLOBAL_ACTION_QUICK_SETTINGS = 5;
    public static final int GLOBAL_ACTION_POWER_DIALOG = 6;
    public static final int GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN = 7;
    public static final int GLOBAL_ACTION_LOCK_SCREEN = 8;
    public static final int GLOBAL_ACTION_TAKE_SCREENSHOT = 9;
    public static final int GLOBAL_ACTION_KEYCODE_HEADSETHOOK = 10;
    public static final int GLOBAL_ACTION_ACCESSIBILITY_BUTTON = 11;
    public static final int GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER = 12;
    public static final int GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT = 13;
    public static final int GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS = 14;
    public static final int GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE = 15;
    public static final int GLOBAL_ACTION_DPAD_UP = 16;
    public static final int GLOBAL_ACTION_DPAD_DOWN = 17;
    public static final int GLOBAL_ACTION_DPAD_LEFT = 18;
    public static final int GLOBAL_ACTION_DPAD_RIGHT = 19;
    public static final int GLOBAL_ACTION_DPAD_CENTER = 20;

    public static final int SHOW_MODE_AUTO = 0;
    public static final int SHOW_MODE_HIDDEN = 1;
    public static final int SHOW_MODE_IGNORE_HARD_KEYBOARD = 2;

    public static final int ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR = 1;
    public static final int ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS = 2;
    public static final int ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT = 3;
    public static final int ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY = 4;
    public static final int ERROR_TAKE_SCREENSHOT_INVALID_WINDOW = 5;
    public static final int ERROR_TAKE_SCREENSHOT_SECURE_WINDOW = 6;

    private final Object mLock = new Object();
    private MagnificationController mMagnificationController;
    private SoftKeyboardController mSoftKeyboardController;
    private FingerprintGestureController mFingerprintGestureController;
    private BrailleDisplayController mBrailleDisplayController;
    private InputMethod mInputMethod;
    private final SparseArray<AccessibilityButtonController> mButtonControllers =
            new SparseArray<AccessibilityButtonController>(1);
    private final SparseArray<TouchInteractionController> mTouchInteractionControllers =
            new SparseArray<TouchInteractionController>(1);
    private AccessibilityServiceInfo mInfo;
    private boolean mCacheEnabled = true;
    private IBinder mBinder;

    public AccessibilityService() {
    }

    public abstract void onAccessibilityEvent(AccessibilityEvent event);

    public abstract void onInterrupt();

    protected void onServiceConnected() {
    }

    @Deprecated
    protected boolean onGesture(int gestureId) {
        return false;
    }

    public boolean onGesture(AccessibilityGestureEvent gestureEvent) {
        if (gestureEvent != null && gestureEvent.getDisplayId() == Display.DEFAULT_DISPLAY) {
            onGesture(gestureEvent.getGestureId());
        }
        return false;
    }

    protected boolean onKeyEvent(KeyEvent event) {
        return false;
    }

    public void onMotionEvent(MotionEvent event) {
    }

    public List<AccessibilityWindowInfo> getWindows() {
        return Collections.emptyList();
    }

    public final SparseArray<List<AccessibilityWindowInfo>> getWindowsOnAllDisplays() {
        return new SparseArray<List<AccessibilityWindowInfo>>();
    }

    public AccessibilityNodeInfo getRootInActiveWindow() {
        return getRootInActiveWindow(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID);
    }

    public AccessibilityNodeInfo getRootInActiveWindow(int prefetchingStrategy) {
        return null;
    }

    public final void disableSelf() {
        if (getBaseContext() != null) {
            try {
                stopSelf();
            } catch (RuntimeException ignored) {
            }
        }
    }

    public Context createDisplayContext(Display display) {
        Objects.requireNonNull(display, "display");
        return new ContextWrapper(this);
    }

    public Context createWindowContext(int type, Bundle options) {
        return new ContextWrapper(this);
    }

    public Context createWindowContext(Display display, int type, Bundle options) {
        Objects.requireNonNull(display, "display");
        return new ContextWrapper(this);
    }

    public final MagnificationController getMagnificationController() {
        synchronized (mLock) {
            if (mMagnificationController == null) {
                mMagnificationController = new MagnificationController(this, mLock);
            }
            return mMagnificationController;
        }
    }

    public final FingerprintGestureController getFingerprintGestureController() {
        synchronized (mLock) {
            if (mFingerprintGestureController == null) {
                mFingerprintGestureController = new FingerprintGestureController();
            }
            return mFingerprintGestureController;
        }
    }

    public final boolean dispatchGesture(
            final GestureDescription gesture,
            final GestureResultCallback callback,
            Handler handler) {
        Objects.requireNonNull(gesture, "gesture");
        if (callback != null) {
            Runnable cancelTask = new Runnable() {
                @Override
                public void run() {
                    callback.onCancelled(gesture);
                }
            };
            if (handler != null) {
                handler.post(cancelTask);
            } else {
                cancelTask.run();
            }
        }
        return false;
    }

    public final SoftKeyboardController getSoftKeyboardController() {
        synchronized (mLock) {
            if (mSoftKeyboardController == null) {
                mSoftKeyboardController = new SoftKeyboardController(this, mLock);
            }
            return mSoftKeyboardController;
        }
    }

    public InputMethod onCreateInputMethod() {
        return new InputMethod(this);
    }

    public final InputMethod getInputMethod() {
        synchronized (mLock) {
            if (mInputMethod == null) {
                mInputMethod = onCreateInputMethod();
            }
            return mInputMethod;
        }
    }

    public final AccessibilityButtonController getAccessibilityButtonController() {
        return getAccessibilityButtonController(Display.DEFAULT_DISPLAY);
    }

    public final AccessibilityButtonController getAccessibilityButtonController(int displayId) {
        synchronized (mLock) {
            AccessibilityButtonController controller = mButtonControllers.get(displayId);
            if (controller == null) {
                controller = new AccessibilityButtonController();
                mButtonControllers.put(displayId, controller);
            }
            return controller;
        }
    }

    public boolean setCacheEnabled(boolean enabled) {
        mCacheEnabled = enabled;
        return true;
    }

    public boolean clearCachedSubtree(AccessibilityNodeInfo node) {
        Objects.requireNonNull(node, "node");
        return true;
    }

    public boolean clearCache() {
        return true;
    }

    public boolean isNodeInCache(AccessibilityNodeInfo node) {
        return false;
    }

    public boolean isCacheEnabled() {
        return mCacheEnabled;
    }

    public void onSystemActionsChanged() {
    }

    public final List<AccessibilityNodeInfo.AccessibilityAction> getSystemActions() {
        return Collections.emptyList();
    }

    public final boolean performGlobalAction(int action) {
        return false;
    }

    public AccessibilityNodeInfo findFocus(int focus) {
        return null;
    }

    public final AccessibilityServiceInfo getServiceInfo() {
        synchronized (mLock) {
            return mInfo;
        }
    }

    public final void setServiceInfo(AccessibilityServiceInfo info) {
        synchronized (mLock) {
            mInfo = info;
        }
    }

    @Override
    public Object getSystemService(String name) {
        if (getBaseContext() == null) {
            throw new IllegalStateException(
                    "System services not available to Activities before onCreate()");
        }
        return super.getSystemService(name);
    }

    public void takeScreenshot(
            int displayId,
            Executor executor,
            final TakeScreenshotCallback callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onFailure(ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS);
            }
        });
    }

    public void takeScreenshotOfWindow(
            int accessibilityWindowId,
            Executor executor,
            final TakeScreenshotCallback callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onFailure(ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS);
            }
        });
    }

    public void setAccessibilityFocusAppearance(int strokeWidth, int color) {
    }

    @Override
    public final IBinder onBind(Intent intent) {
        if (mBinder == null) {
            mBinder = new AccessibilityServiceBinder(this);
        }
        return mBinder;
    }

    public void setGestureDetectionPassthroughRegion(int displayId, Region region) {
        Objects.requireNonNull(region, "region");
    }

    public void setTouchExplorationPassthroughRegion(int displayId, Region region) {
        Objects.requireNonNull(region, "region");
    }

    public void setAnimationScale(float scale) {
    }

    public final TouchInteractionController getTouchInteractionController(int displayId) {
        synchronized (mLock) {
            TouchInteractionController controller = mTouchInteractionControllers.get(displayId);
            if (controller == null) {
                controller = new TouchInteractionController(displayId);
                mTouchInteractionControllers.put(displayId, controller);
            }
            return controller;
        }
    }

    public void attachAccessibilityOverlayToDisplay(int displayId, SurfaceControl sc) {
        Objects.requireNonNull(sc, "sc");
    }

    public void attachAccessibilityOverlayToWindow(int accessibilityWindowId, SurfaceControl sc) {
        Objects.requireNonNull(sc, "sc");
    }

    public final BrailleDisplayController getBrailleDisplayController() {
        synchronized (mLock) {
            if (mBrailleDisplayController == null) {
                mBrailleDisplayController = new DefaultBrailleDisplayController();
            }
            return mBrailleDisplayController;
        }
    }

    private static final class AccessibilityServiceBinder extends Binder implements IInterface {
        private final AccessibilityService mService;

        AccessibilityServiceBinder(AccessibilityService service) {
            mService = service;
            attachInterface(this, SERVICE_INTERFACE);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }

    private static final class DefaultBrailleDisplayController
            implements BrailleDisplayController {
        @Override
        public void connect(
                BluetoothDevice bluetoothDevice, BrailleDisplayCallback callback) {
            Objects.requireNonNull(bluetoothDevice, "bluetoothDevice");
            Objects.requireNonNull(callback, "callback");
            callback.onConnectionFailed(BrailleDisplayCallback.FLAG_ERROR_CANNOT_ACCESS);
        }

        @Override
        public void connect(
                BluetoothDevice bluetoothDevice,
                Executor callbackExecutor,
                final BrailleDisplayCallback callback) {
            Objects.requireNonNull(bluetoothDevice, "bluetoothDevice");
            Objects.requireNonNull(callbackExecutor, "callbackExecutor");
            Objects.requireNonNull(callback, "callback");
            callbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    callback.onConnectionFailed(BrailleDisplayCallback.FLAG_ERROR_CANNOT_ACCESS);
                }
            });
        }

        @Override
        public void connect(UsbDevice usbDevice, BrailleDisplayCallback callback) {
            Objects.requireNonNull(usbDevice, "usbDevice");
            Objects.requireNonNull(callback, "callback");
            callback.onConnectionFailed(BrailleDisplayCallback.FLAG_ERROR_CANNOT_ACCESS);
        }

        @Override
        public void connect(
                UsbDevice usbDevice,
                Executor callbackExecutor,
                final BrailleDisplayCallback callback) {
            Objects.requireNonNull(usbDevice, "usbDevice");
            Objects.requireNonNull(callbackExecutor, "callbackExecutor");
            Objects.requireNonNull(callback, "callback");
            callbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    callback.onConnectionFailed(BrailleDisplayCallback.FLAG_ERROR_CANNOT_ACCESS);
                }
            });
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public void write(byte[] buffer) throws IOException {
            Objects.requireNonNull(buffer, "buffer");
            throw new IOException("BrailleDisplayController is not connected");
        }

        @Override
        public void disconnect() {
        }
    }

    public abstract static class GestureResultCallback {
        public GestureResultCallback() {
        }

        public void onCompleted(GestureDescription gestureDescription) {
        }

        public void onCancelled(GestureDescription gestureDescription) {
        }
    }

    public static final class MagnificationController {
        private final Object mLock;
        private final IdentityHashMap<OnMagnificationChangedListener, Handler> mListeners =
                new IdentityHashMap<OnMagnificationChangedListener, Handler>();
        private float mScale = 1.0f;
        private float mCenterX;
        private float mCenterY;
        private MagnificationConfig mConfig;

        MagnificationController(AccessibilityService service, Object lock) {
            mLock = lock;
        }

        public void addListener(OnMagnificationChangedListener listener) {
            addListener(listener, null);
        }

        public void addListener(
                OnMagnificationChangedListener listener, Handler handler) {
            Objects.requireNonNull(listener, "listener");
            synchronized (mLock) {
                mListeners.put(listener, handler);
            }
        }

        public boolean removeListener(OnMagnificationChangedListener listener) {
            Objects.requireNonNull(listener, "listener");
            synchronized (mLock) {
                boolean present = mListeners.containsKey(listener);
                mListeners.remove(listener);
                return present;
            }
        }

        public MagnificationConfig getMagnificationConfig() {
            synchronized (mLock) {
                return mConfig;
            }
        }

        public float getScale() {
            synchronized (mLock) {
                return mScale;
            }
        }

        public float getCenterX() {
            synchronized (mLock) {
                return mCenterX;
            }
        }

        public float getCenterY() {
            synchronized (mLock) {
                return mCenterY;
            }
        }

        public Region getMagnificationRegion() {
            return new Region();
        }

        public Region getCurrentMagnificationRegion() {
            return new Region();
        }

        public boolean reset(boolean animate) {
            synchronized (mLock) {
                mScale = 1.0f;
                mCenterX = 0.0f;
                mCenterY = 0.0f;
                mConfig = null;
            }
            return false;
        }

        public boolean resetCurrentMagnification(boolean animate) {
            return reset(animate);
        }

        public boolean setMagnificationConfig(MagnificationConfig config, boolean animate) {
            Objects.requireNonNull(config, "config");
            synchronized (mLock) {
                mConfig = config;
                if (!Float.isNaN(config.getScale())) {
                    mScale = config.getScale();
                }
                if (!Float.isNaN(config.getCenterX())) {
                    mCenterX = config.getCenterX();
                }
                if (!Float.isNaN(config.getCenterY())) {
                    mCenterY = config.getCenterY();
                }
            }
            return false;
        }

        @Deprecated
        public boolean setScale(float scale, boolean animate) {
            synchronized (mLock) {
                mScale = scale;
            }
            return false;
        }

        @Deprecated
        public boolean setCenter(float centerX, float centerY, boolean animate) {
            synchronized (mLock) {
                mCenterX = centerX;
                mCenterY = centerY;
            }
            return false;
        }

        public interface OnMagnificationChangedListener {
            @Deprecated
            void onMagnificationChanged(
                    MagnificationController controller,
                    Region region,
                    float scale,
                    float centerX,
                    float centerY);

            default void onMagnificationChanged(
                    MagnificationController controller,
                    Region region,
                    MagnificationConfig config) {
                onMagnificationChanged(
                        controller,
                        region,
                        config.getScale(),
                        config.getCenterX(),
                        config.getCenterY());
            }
        }
    }

    public static final class SoftKeyboardController {
        public static final int ENABLE_IME_SUCCESS = 0;
        public static final int ENABLE_IME_FAIL_BY_ADMIN = 1;
        public static final int ENABLE_IME_FAIL_UNKNOWN = 2;

        private final Object mLock;
        private final IdentityHashMap<OnShowModeChangedListener, Handler> mListeners =
                new IdentityHashMap<OnShowModeChangedListener, Handler>();
        private int mShowMode = SHOW_MODE_AUTO;

        SoftKeyboardController(AccessibilityService service, Object lock) {
            mLock = lock;
        }

        public void addOnShowModeChangedListener(OnShowModeChangedListener listener) {
            addOnShowModeChangedListener(listener, null);
        }

        public void addOnShowModeChangedListener(
                OnShowModeChangedListener listener, Handler handler) {
            Objects.requireNonNull(listener, "listener");
            synchronized (mLock) {
                mListeners.put(listener, handler);
            }
        }

        public boolean removeOnShowModeChangedListener(OnShowModeChangedListener listener) {
            Objects.requireNonNull(listener, "listener");
            synchronized (mLock) {
                boolean present = mListeners.containsKey(listener);
                mListeners.remove(listener);
                return present;
            }
        }

        public int getShowMode() {
            synchronized (mLock) {
                return mShowMode;
            }
        }

        public boolean setShowMode(int showMode) {
            if (showMode != SHOW_MODE_AUTO
                    && showMode != SHOW_MODE_HIDDEN
                    && showMode != SHOW_MODE_IGNORE_HARD_KEYBOARD) {
                return false;
            }
            synchronized (mLock) {
                mShowMode = showMode;
            }
            return true;
        }

        public boolean switchToInputMethod(String imeId) {
            Objects.requireNonNull(imeId, "imeId");
            return false;
        }

        public int setInputMethodEnabled(String imeId, boolean enabled) throws SecurityException {
            Objects.requireNonNull(imeId, "imeId");
            return ENABLE_IME_FAIL_UNKNOWN;
        }

        public interface OnShowModeChangedListener {
            void onShowModeChanged(SoftKeyboardController controller, int showMode);
        }
    }

    public static final class ScreenshotResult {
        private final HardwareBuffer mHardwareBuffer;
        private final ColorSpace mColorSpace;
        private final long mTimestamp;

        ScreenshotResult(
                HardwareBuffer hardwareBuffer, ColorSpace colorSpace, long timestamp) {
            mHardwareBuffer = Objects.requireNonNull(hardwareBuffer, "hardwareBuffer");
            mColorSpace = Objects.requireNonNull(colorSpace, "colorSpace");
            mTimestamp = timestamp;
        }

        public ColorSpace getColorSpace() {
            return mColorSpace;
        }

        public HardwareBuffer getHardwareBuffer() {
            return mHardwareBuffer;
        }

        public long getTimestamp() {
            return mTimestamp;
        }
    }

    public interface TakeScreenshotCallback {
        void onSuccess(ScreenshotResult screenshot);

        void onFailure(int errorCode);
    }
}
