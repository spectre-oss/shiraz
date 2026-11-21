// SPDX-License-Identifier: GPL-3.0-only
package android.accessibilityservice;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AccessibilityGestureEvent implements Parcelable {
    private final int mGestureId;
    private final int mDisplayId;
    private final List<MotionEvent> mMotionEvents;

    public AccessibilityGestureEvent(
            int gestureId, int displayId, List<MotionEvent> motionEvents) {
        Objects.requireNonNull(motionEvents, "motionEvents");
        mGestureId = gestureId;
        mDisplayId = displayId;
        mMotionEvents = Collections.unmodifiableList(new ArrayList<MotionEvent>(motionEvents));
    }

    private AccessibilityGestureEvent(Parcel parcel) {
        mGestureId = parcel.readInt();
        mDisplayId = parcel.readInt();
        int eventCount = parcel.readInt();
        ArrayList<MotionEvent> events = new ArrayList<MotionEvent>(Math.max(0, eventCount));
        for (int i = 0; i < eventCount; i++) {
            events.add(readMotionEvent(parcel));
        }
        mMotionEvents = Collections.unmodifiableList(events);
    }

    public int getDisplayId() {
        return mDisplayId;
    }

    public int getGestureId() {
        return mGestureId;
    }

    public List<MotionEvent> getMotionEvents() {
        return mMotionEvents;
    }

    @Override
    public String toString() {
        return "AccessibilityGestureEvent["
                + "gestureId: " + gestureIdToString(mGestureId) + ", "
                + "displayId: " + mDisplayId + ", "
                + "Motion Events: " + mMotionEvents + ']';
    }

    public static String gestureIdToString(int id) {
        switch (id) {
            case AccessibilityService.GESTURE_UNKNOWN:
                return "GESTURE_UNKNOWN";
            case AccessibilityService.GESTURE_SWIPE_UP:
                return "GESTURE_SWIPE_UP";
            case AccessibilityService.GESTURE_SWIPE_DOWN:
                return "GESTURE_SWIPE_DOWN";
            case AccessibilityService.GESTURE_SWIPE_LEFT:
                return "GESTURE_SWIPE_LEFT";
            case AccessibilityService.GESTURE_SWIPE_RIGHT:
                return "GESTURE_SWIPE_RIGHT";
            case AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT:
                return "GESTURE_SWIPE_LEFT_AND_RIGHT";
            case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT:
                return "GESTURE_SWIPE_RIGHT_AND_LEFT";
            case AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN:
                return "GESTURE_SWIPE_UP_AND_DOWN";
            case AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP:
                return "GESTURE_SWIPE_DOWN_AND_UP";
            case AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP:
                return "GESTURE_SWIPE_LEFT_AND_UP";
            case AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN:
                return "GESTURE_SWIPE_LEFT_AND_DOWN";
            case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP:
                return "GESTURE_SWIPE_RIGHT_AND_UP";
            case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN:
                return "GESTURE_SWIPE_RIGHT_AND_DOWN";
            case AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT:
                return "GESTURE_SWIPE_UP_AND_LEFT";
            case AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT:
                return "GESTURE_SWIPE_UP_AND_RIGHT";
            case AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT:
                return "GESTURE_SWIPE_DOWN_AND_LEFT";
            case AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT:
                return "GESTURE_SWIPE_DOWN_AND_RIGHT";
            case AccessibilityService.GESTURE_DOUBLE_TAP:
                return "GESTURE_DOUBLE_TAP";
            case AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD:
                return "GESTURE_DOUBLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP:
                return "GESTURE_2_FINGER_SINGLE_TAP";
            case AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP:
                return "GESTURE_2_FINGER_DOUBLE_TAP";
            case AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD:
                return "GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP:
                return "GESTURE_2_FINGER_TRIPLE_TAP";
            case AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD:
                return "GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_2_FINGER_SWIPE_UP:
                return "GESTURE_2_FINGER_SWIPE_UP";
            case AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN:
                return "GESTURE_2_FINGER_SWIPE_DOWN";
            case AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT:
                return "GESTURE_2_FINGER_SWIPE_LEFT";
            case AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT:
                return "GESTURE_2_FINGER_SWIPE_RIGHT";
            case AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP:
                return "GESTURE_3_FINGER_SINGLE_TAP";
            case AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD:
                return "GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP:
                return "GESTURE_3_FINGER_DOUBLE_TAP";
            case AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD:
                return "GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP:
                return "GESTURE_3_FINGER_TRIPLE_TAP";
            case AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD:
                return "GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_3_FINGER_SWIPE_UP:
                return "GESTURE_3_FINGER_SWIPE_UP";
            case AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN:
                return "GESTURE_3_FINGER_SWIPE_DOWN";
            case AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT:
                return "GESTURE_3_FINGER_SWIPE_LEFT";
            case AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT:
                return "GESTURE_3_FINGER_SWIPE_RIGHT";
            case AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP:
                return "GESTURE_4_FINGER_SINGLE_TAP";
            case AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP:
                return "GESTURE_4_FINGER_DOUBLE_TAP";
            case AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD:
                return "GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP:
                return "GESTURE_4_FINGER_TRIPLE_TAP";
            case AccessibilityService.GESTURE_4_FINGER_SWIPE_UP:
                return "GESTURE_4_FINGER_SWIPE_UP";
            case AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN:
                return "GESTURE_4_FINGER_SWIPE_DOWN";
            case AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT:
                return "GESTURE_4_FINGER_SWIPE_LEFT";
            case AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT:
                return "GESTURE_4_FINGER_SWIPE_RIGHT";
            default:
                return Integer.toHexString(id);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        Objects.requireNonNull(parcel, "parcel");
        parcel.writeInt(mGestureId);
        parcel.writeInt(mDisplayId);
        parcel.writeInt(mMotionEvents.size());
        for (int i = 0; i < mMotionEvents.size(); i++) {
            writeMotionEvent(parcel, mMotionEvents.get(i));
        }
    }

    private static void writeMotionEvent(Parcel out, MotionEvent ev) {
        if (ev == null) {
            out.writeInt(0);
            return;
        }
        int pointerCount = ev.getPointerCount();
        out.writeInt(pointerCount);
        out.writeLong(ev.getDownTime());
        out.writeLong(ev.getEventTime());
        out.writeInt(ev.getAction());
        out.writeInt(ev.getMetaState());
        out.writeInt(ev.getButtonState());
        out.writeFloat(ev.getXPrecision());
        out.writeFloat(ev.getYPrecision());
        out.writeInt(ev.getDeviceId());
        out.writeInt(ev.getEdgeFlags());
        out.writeInt(ev.getSource());
        out.writeInt(ev.getFlags());
        MotionEvent.PointerProperties prop = new MotionEvent.PointerProperties();
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        for (int i = 0; i < pointerCount; i++) {
            ev.getPointerProperties(i, prop);
            ev.getPointerCoords(i, coords);
            out.writeInt(prop.id);
            out.writeInt(prop.toolType);
            out.writeFloat(coords.x);
            out.writeFloat(coords.y);
            out.writeFloat(coords.pressure);
            out.writeFloat(coords.size);
            out.writeFloat(coords.touchMajor);
            out.writeFloat(coords.touchMinor);
            out.writeFloat(coords.toolMajor);
            out.writeFloat(coords.toolMinor);
            out.writeFloat(coords.orientation);
        }
    }

    private static MotionEvent readMotionEvent(Parcel in) {
        int pointerCount = in.readInt();
        if (pointerCount <= 0) {
            return null;
        }
        long downTime = in.readLong();
        long eventTime = in.readLong();
        int action = in.readInt();
        int meta = in.readInt();
        int buttons = in.readInt();
        float px = in.readFloat();
        float py = in.readFloat();
        int device = in.readInt();
        int edge = in.readInt();
        int source = in.readInt();
        int flags = in.readInt();
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerCount];
        for (int i = 0; i < pointerCount; i++) {
            MotionEvent.PointerProperties p = new MotionEvent.PointerProperties();
            p.id = in.readInt();
            p.toolType = in.readInt();
            props[i] = p;
            MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
            c.x = in.readFloat();
            c.y = in.readFloat();
            c.pressure = in.readFloat();
            c.size = in.readFloat();
            c.touchMajor = in.readFloat();
            c.touchMinor = in.readFloat();
            c.toolMajor = in.readFloat();
            c.toolMinor = in.readFloat();
            c.orientation = in.readFloat();
            coords[i] = c;
        }
        return MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                pointerCount,
                props,
                coords,
                meta,
                buttons,
                px,
                py,
                device,
                edge,
                source,
                flags);
    }

    public static final Parcelable.Creator<AccessibilityGestureEvent> CREATOR =
            new Parcelable.Creator<AccessibilityGestureEvent>() {
                @Override
                public AccessibilityGestureEvent createFromParcel(Parcel parcel) {
                    return new AccessibilityGestureEvent(parcel);
                }

                @Override
                public AccessibilityGestureEvent[] newArray(int size) {
                    return new AccessibilityGestureEvent[size];
                }
            };
}
