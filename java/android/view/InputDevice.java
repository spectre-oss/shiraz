// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.hardware.BatteryState;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.lights.Light;
import android.hardware.lights.LightState;
import android.hardware.lights.LightsManager;
import android.hardware.lights.LightsRequest;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Vibrator;
import android.os.VibratorManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Aggregate SDL input device; individual host peripherals are not enumerated. */
public final class InputDevice implements Parcelable {
    public static final int SOURCE_CLASS_MASK = 0x000000ff;
    public static final int SOURCE_CLASS_NONE = 0x00000000;
    public static final int SOURCE_CLASS_BUTTON = 0x00000001;
    public static final int SOURCE_CLASS_POINTER = 0x00000002;
    public static final int SOURCE_CLASS_TRACKBALL = 0x00000004;
    public static final int SOURCE_CLASS_POSITION = 0x00000008;
    public static final int SOURCE_CLASS_JOYSTICK = 0x00000010;

    public static final int SOURCE_UNKNOWN = 0x00000000;
    public static final int SOURCE_KEYBOARD = 0x00000101;
    public static final int SOURCE_DPAD = 0x00000201;
    public static final int SOURCE_GAMEPAD = 0x00000401;
    public static final int SOURCE_TOUCHSCREEN = 0x00001002;
    public static final int SOURCE_MOUSE = 0x00002002;
    public static final int SOURCE_STYLUS = 0x00004002;
    public static final int SOURCE_BLUETOOTH_STYLUS = 0x0000c002;
    public static final int SOURCE_TRACKBALL = 0x00010004;
    public static final int SOURCE_MOUSE_RELATIVE = 0x00020004;
    public static final int SOURCE_TOUCHPAD = 0x00100008;
    public static final int SOURCE_TOUCH_NAVIGATION = 0x00200000;
    public static final int SOURCE_ROTARY_ENCODER = 0x00400000;
    public static final int SOURCE_JOYSTICK = 0x01000010;
    public static final int SOURCE_HDMI = 0x02000001;
    public static final int SOURCE_SENSOR = 0x04000000;
    public static final int SOURCE_ANY = 0xffffff00;

    @Deprecated public static final int MOTION_RANGE_X = MotionEvent.AXIS_X;
    @Deprecated public static final int MOTION_RANGE_Y = MotionEvent.AXIS_Y;
    @Deprecated public static final int MOTION_RANGE_PRESSURE = MotionEvent.AXIS_PRESSURE;
    @Deprecated public static final int MOTION_RANGE_SIZE = MotionEvent.AXIS_SIZE;
    @Deprecated public static final int MOTION_RANGE_TOUCH_MAJOR = MotionEvent.AXIS_TOUCH_MAJOR;
    @Deprecated public static final int MOTION_RANGE_TOUCH_MINOR = MotionEvent.AXIS_TOUCH_MINOR;
    @Deprecated public static final int MOTION_RANGE_TOOL_MAJOR = MotionEvent.AXIS_TOOL_MAJOR;
    @Deprecated public static final int MOTION_RANGE_TOOL_MINOR = MotionEvent.AXIS_TOOL_MINOR;
    @Deprecated public static final int MOTION_RANGE_ORIENTATION = MotionEvent.AXIS_ORIENTATION;

    public static final int KEYBOARD_TYPE_NONE = 0;
    public static final int KEYBOARD_TYPE_NON_ALPHABETIC = 1;
    public static final int KEYBOARD_TYPE_ALPHABETIC = 2;

    private static final InputDevice HOST = new InputDevice(0);

    private final int mId;
    private final Vibrator vibrator = new Vibrator();
    private final ViewBehavior mViewBehavior = new ViewBehavior(this);
    private VibratorManager mVibratorManager;
    private BatteryState mBatteryState;
    private LightsManager mLightsManager;
    private SensorManager mSensorManager;

    private InputDevice(int id) {
        mId = id;
    }

    public static int[] getDeviceIds() {
        return new int[] { 0 };
    }

    public static InputDevice getDevice(int id) {
        return id == 0 ? HOST : null;
    }

    public int getId() {
        return mId;
    }

    public int getControllerNumber() {
        return 0;
    }

    public int getVendorId() {
        return 0;
    }

    public int getProductId() {
        return 0;
    }

    public String getDescriptor() {
        return "shiraz-sdl-input";
    }

    public boolean isVirtual() {
        return true;
    }

    public boolean isExternal() {
        return false;
    }

    public String getName() {
        return "Shiraz SDL input";
    }

    public int getSources() {
        return SOURCE_KEYBOARD | SOURCE_MOUSE | SOURCE_TOUCHSCREEN;
    }

    public boolean supportsSource(int source) {
        return (getSources() & source) == source;
    }

    public int getKeyboardType() {
        return KEYBOARD_TYPE_ALPHABETIC;
    }

    public KeyCharacterMap getKeyCharacterMap() {
        return KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
    }

    public boolean[] hasKeys(int... keys) {
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null");
        }
        boolean[] res = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) {
            res[i] = keys[i] > 0;
        }
        return res;
    }

    public int getKeyCodeForKeyLocation(int locationKeyCode) {
        return locationKeyCode;
    }

    public MotionRange getMotionRange(int axis) {
        return getMotionRange(axis, SOURCE_TOUCHSCREEN);
    }

    public MotionRange getMotionRange(int axis, int source) {
        return source == SOURCE_TOUCHSCREEN
                        && (axis == MotionEvent.AXIS_PRESSURE || axis == MotionEvent.AXIS_SIZE)
                ? new MotionRange(axis, source, 0f, 1f, 0f, 0f, 0f)
                : null;
    }

    public List<MotionRange> getMotionRanges() {
        return Collections.unmodifiableList(Arrays.asList(
                new MotionRange(MotionEvent.AXIS_PRESSURE, SOURCE_TOUCHSCREEN, 0f, 1f, 0f, 0f, 0f),
                new MotionRange(MotionEvent.AXIS_SIZE, SOURCE_TOUCHSCREEN, 0f, 1f, 0f, 0f, 0f)));
    }

    public ViewBehavior getViewBehavior() {
        return mViewBehavior;
    }

    @Deprecated
    public Vibrator getVibrator() {
        return vibrator;
    }

    public synchronized VibratorManager getVibratorManager() {
        if (mVibratorManager == null) {
            mVibratorManager = new VibratorManager() {
                @Override
                public int[] getVibratorIds() {
                    return new int[0];
                }

                @Override
                public Vibrator getVibrator(int vibratorId) {
                    return vibrator;
                }

                @Override
                public Vibrator getDefaultVibrator() {
                    return vibrator;
                }

                @Override
                public void cancel() {
                    vibrator.cancel();
                }
            };
        }
        return mVibratorManager;
    }

    public synchronized BatteryState getBatteryState() {
        if (mBatteryState == null) {
            mBatteryState = new BatteryState() {
                @Override
                public boolean isPresent() {
                    return false;
                }

                @Override
                public int getStatus() {
                    return STATUS_UNKNOWN;
                }

                @Override
                public float getCapacity() {
                    return Float.NaN;
                }
            };
        }
        return mBatteryState;
    }

    public synchronized LightsManager getLightsManager() {
        if (mLightsManager == null) {
            mLightsManager = new LightsManager() {
                @Override
                public List<Light> getLights() {
                    return Collections.emptyList();
                }

                @Override
                public LightState getLightState(Light light) {
                    return new LightState.Builder().build();
                }

                @Override
                public LightsSession openSession() {
                    return new LightsSession() {
                        @Override
                        public void requestLights(LightsRequest request) {
                        }

                        @Override
                        public void close() {
                        }
                    };
                }
            };
        }
        return mLightsManager;
    }

    public synchronized SensorManager getSensorManager() {
        if (mSensorManager == null) {
            mSensorManager = new SensorManager() {
                @Override
                public List<Sensor> getSensorList(int type) {
                    return Collections.emptyList();
                }

                @Override
                public boolean registerListener(
                        SensorEventListener listener,
                        Sensor sensor,
                        int delay,
                        int latency,
                        Handler handler) {
                    return false;
                }

                @Override
                public void unregisterListener(SensorEventListener listener) {
                }

                @Override
                public void unregisterListener(SensorEventListener listener, Sensor sensor) {
                }

                @Override
                public boolean flush(SensorEventListener listener) {
                    return false;
                }
            };
        }
        return mSensorManager;
    }

    public boolean isEnabled() {
        return true;
    }

    public boolean hasMicrophone() {
        return false;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "Input Device " + mId + ": " + getName();
    }

    public static final Parcelable.Creator<InputDevice> CREATOR =
            new Parcelable.Creator<InputDevice>() {
                @Override
                public InputDevice createFromParcel(Parcel in) {
                    int id = in.readInt();
                    return id == 0 ? HOST : new InputDevice(id);
                }

                @Override
                public InputDevice[] newArray(int size) {
                    return new InputDevice[size];
                }
            };

    public static final class MotionRange {
        private final int axis;
        private final int source;
        private final float min;
        private final float max;
        private final float flat;
        private final float fuzz;
        private final float resolution;

        private MotionRange(
                int axis,
                int source,
                float min,
                float max,
                float flat,
                float fuzz,
                float resolution) {
            this.axis = axis;
            this.source = source;
            this.min = min;
            this.max = max;
            this.flat = flat;
            this.fuzz = fuzz;
            this.resolution = resolution;
        }

        public int getAxis() {
            return axis;
        }

        public int getSource() {
            return source;
        }

        public boolean isFromSource(int testSource) {
            return (source & testSource) == testSource;
        }

        public float getMin() {
            return min;
        }

        public float getMax() {
            return max;
        }

        public float getRange() {
            return max - min;
        }

        public float getFlat() {
            return flat;
        }

        public float getFuzz() {
            return fuzz;
        }

        public float getResolution() {
            return resolution;
        }
    }

    public static final class ViewBehavior {
        private final InputDevice mInputDevice;

        ViewBehavior(InputDevice inputDevice) {
            mInputDevice = inputDevice;
        }

        public boolean shouldSmoothScroll(int axis, int source) {
            return mInputDevice.getMotionRange(axis, source) != null
                    || (source == SOURCE_MOUSE
                            && (axis == MotionEvent.AXIS_VSCROLL
                                    || axis == MotionEvent.AXIS_HSCROLL));
        }
    }
}
