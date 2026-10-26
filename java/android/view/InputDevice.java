// SPDX-License-Identifier: GPL-3.0-only
package android.view;
/** Aggregate SDL input device; individual host peripherals are not enumerated. */
public final class InputDevice {
    public static final int SOURCE_KEYBOARD=0x101, SOURCE_MOUSE=0x2002, SOURCE_TOUCHSCREEN=0x1002;
    public static final int KEYBOARD_TYPE_NONE=0, KEYBOARD_TYPE_NON_ALPHABETIC=1, KEYBOARD_TYPE_ALPHABETIC=2;
    private static final InputDevice HOST=new InputDevice();
    private InputDevice() { }
    public static int[] getDeviceIds(){return new int[]{0};}
    public static InputDevice getDevice(int id){return id==0?HOST:null;}
    public int getId(){return 0;}
    public String getName(){return "Shiraz SDL input";}
    public String getDescriptor(){return "shiraz-sdl-input";}
    public int getSources(){return SOURCE_KEYBOARD|SOURCE_MOUSE|SOURCE_TOUCHSCREEN;}
    public boolean supportsSource(int source){return (getSources()&source)==source;}
    public int getKeyboardType(){return KEYBOARD_TYPE_ALPHABETIC;}
    public boolean isVirtual(){return true;}
    public boolean isExternal(){return false;}
    public int getVendorId(){return 0;}
    public int getProductId(){return 0;}
    public MotionRange getMotionRange(int axis){return axis==2||axis==3?new MotionRange(axis,0f,1f):null;}
    public static final class MotionRange {
        private final int axis;private final float min,max;
        private MotionRange(int axis,float min,float max){this.axis=axis;this.min=min;this.max=max;}
        public int getAxis(){return axis;}
        public float getMin(){return min;}
        public float getMax(){return max;}
        public float getRange(){return max-min;}
    }
}
