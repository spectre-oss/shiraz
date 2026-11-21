// SPDX-License-Identifier: GPL-3.0-only
import android.view.InputDevice;
import android.view.MotionEvent;

public final class InputDeviceRangeSmoke {
 public static void main(String[] args) {
  InputDevice device=InputDevice.getDevice(0);
  InputDevice.MotionRange pressure=device.getMotionRange(MotionEvent.AXIS_PRESSURE,InputDevice.SOURCE_TOUCHSCREEN);
  if(pressure==null||pressure.getAxis()!=MotionEvent.AXIS_PRESSURE||pressure.getSource()!=InputDevice.SOURCE_TOUCHSCREEN||pressure.getMin()!=0||pressure.getMax()!=1)
   throw new AssertionError("Touch pressure range");
  if(device.getMotionRange(MotionEvent.AXIS_SIZE)==null||device.getMotionRange(MotionEvent.AXIS_SIZE,InputDevice.SOURCE_TOUCHSCREEN)==null)
   throw new AssertionError("Touch size range");
  if(device.getMotionRange(MotionEvent.AXIS_VSCROLL,InputDevice.SOURCE_MOUSE)!=null||device.getMotionRange(MotionEvent.AXIS_PRESSURE,InputDevice.SOURCE_MOUSE)!=null||device.getMotionRange(MotionEvent.AXIS_PRESSURE,InputDevice.SOURCE_TOUCHSCREEN|InputDevice.SOURCE_MOUSE)!=null)
   throw new AssertionError("Unreported or mismatched ranges must be absent");
  System.out.println("INPUT_DEVICE_RANGE_OK");
 }
}
