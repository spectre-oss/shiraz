// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.os.*;
import java.util.*;

/** Key values and callback dispatch. Hardware character maps/parcel IPC are separate. */
public class KeyEvent extends InputEvent implements Parcelable {
    public static int getDeadChar(int accent,int character){return KeyCharacterMap.getDeadChar(accent,character);}
    // Public constant names/values follow the stock Android SDK 35 API metadata.
    public static final int ACTION_DOWN = 0, ACTION_MULTIPLE = 2, ACTION_UP = 1, FLAG_CANCELED = 32;
    public static final int FLAG_CANCELED_LONG_PRESS = 256, FLAG_EDITOR_ACTION = 16, FLAG_FALLBACK = 1024, FLAG_FROM_SYSTEM = 8;
    public static final int FLAG_KEEP_TOUCH_MODE = 4, FLAG_LONG_PRESS = 128, FLAG_SOFT_KEYBOARD = 2, FLAG_TRACKING = 512;
    public static final int FLAG_VIRTUAL_HARD_KEY = 64, FLAG_WOKE_HERE = 1, KEYCODE_0 = 7, KEYCODE_1 = 8;
    public static final int KEYCODE_11 = 227, KEYCODE_12 = 228, KEYCODE_2 = 9, KEYCODE_3 = 10;
    public static final int KEYCODE_3D_MODE = 206, KEYCODE_4 = 11, KEYCODE_5 = 12, KEYCODE_6 = 13;
    public static final int KEYCODE_7 = 14, KEYCODE_8 = 15, KEYCODE_9 = 16, KEYCODE_A = 29;
    public static final int KEYCODE_ALL_APPS = 284, KEYCODE_ALT_LEFT = 57, KEYCODE_ALT_RIGHT = 58, KEYCODE_APOSTROPHE = 75;
    public static final int KEYCODE_APP_SWITCH = 187, KEYCODE_ASSIST = 219, KEYCODE_AT = 77, KEYCODE_AVR_INPUT = 182;
    public static final int KEYCODE_AVR_POWER = 181, KEYCODE_B = 30, KEYCODE_BACK = 4, KEYCODE_BACKSLASH = 73;
    public static final int KEYCODE_BOOKMARK = 174, KEYCODE_BREAK = 121, KEYCODE_BRIGHTNESS_DOWN = 220, KEYCODE_BRIGHTNESS_UP = 221;
    public static final int KEYCODE_BUTTON_1 = 188, KEYCODE_BUTTON_10 = 197, KEYCODE_BUTTON_11 = 198, KEYCODE_BUTTON_12 = 199;
    public static final int KEYCODE_BUTTON_13 = 200, KEYCODE_BUTTON_14 = 201, KEYCODE_BUTTON_15 = 202, KEYCODE_BUTTON_16 = 203;
    public static final int KEYCODE_BUTTON_2 = 189, KEYCODE_BUTTON_3 = 190, KEYCODE_BUTTON_4 = 191, KEYCODE_BUTTON_5 = 192;
    public static final int KEYCODE_BUTTON_6 = 193, KEYCODE_BUTTON_7 = 194, KEYCODE_BUTTON_8 = 195, KEYCODE_BUTTON_9 = 196;
    public static final int KEYCODE_BUTTON_A = 96, KEYCODE_BUTTON_B = 97, KEYCODE_BUTTON_C = 98, KEYCODE_BUTTON_L1 = 102;
    public static final int KEYCODE_BUTTON_L2 = 104, KEYCODE_BUTTON_MODE = 110, KEYCODE_BUTTON_R1 = 103, KEYCODE_BUTTON_R2 = 105;
    public static final int KEYCODE_BUTTON_SELECT = 109, KEYCODE_BUTTON_START = 108, KEYCODE_BUTTON_THUMBL = 106, KEYCODE_BUTTON_THUMBR = 107;
    public static final int KEYCODE_BUTTON_X = 99, KEYCODE_BUTTON_Y = 100, KEYCODE_BUTTON_Z = 101, KEYCODE_C = 31;
    public static final int KEYCODE_CALCULATOR = 210, KEYCODE_CALENDAR = 208, KEYCODE_CALL = 5, KEYCODE_CAMERA = 27;
    public static final int KEYCODE_CAPS_LOCK = 115, KEYCODE_CAPTIONS = 175, KEYCODE_CHANNEL_DOWN = 167, KEYCODE_CHANNEL_UP = 166;
    public static final int KEYCODE_CLEAR = 28, KEYCODE_COMMA = 55, KEYCODE_CONTACTS = 207, KEYCODE_COPY = 278;
    public static final int KEYCODE_CTRL_LEFT = 113, KEYCODE_CTRL_RIGHT = 114, KEYCODE_CUT = 277, KEYCODE_D = 32;
    public static final int KEYCODE_DEL = 67, KEYCODE_DEMO_APP_1 = 301, KEYCODE_DEMO_APP_2 = 302, KEYCODE_DEMO_APP_3 = 303;
    public static final int KEYCODE_DEMO_APP_4 = 304, KEYCODE_DPAD_CENTER = 23, KEYCODE_DPAD_DOWN = 20, KEYCODE_DPAD_DOWN_LEFT = 269;
    public static final int KEYCODE_DPAD_DOWN_RIGHT = 271, KEYCODE_DPAD_LEFT = 21, KEYCODE_DPAD_RIGHT = 22, KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_UP_LEFT = 268, KEYCODE_DPAD_UP_RIGHT = 270, KEYCODE_DVR = 173, KEYCODE_E = 33;
    public static final int KEYCODE_EISU = 212, KEYCODE_EMOJI_PICKER = 317, KEYCODE_ENDCALL = 6, KEYCODE_ENTER = 66;
    public static final int KEYCODE_ENVELOPE = 65, KEYCODE_EQUALS = 70, KEYCODE_ESCAPE = 111, KEYCODE_EXPLORER = 64;
    public static final int KEYCODE_F = 34, KEYCODE_F1 = 131, KEYCODE_F10 = 140, KEYCODE_F11 = 141;
    public static final int KEYCODE_F12 = 142, KEYCODE_F2 = 132, KEYCODE_F3 = 133, KEYCODE_F4 = 134;
    public static final int KEYCODE_F5 = 135, KEYCODE_F6 = 136, KEYCODE_F7 = 137, KEYCODE_F8 = 138;
    public static final int KEYCODE_F9 = 139, KEYCODE_FEATURED_APP_1 = 297, KEYCODE_FEATURED_APP_2 = 298, KEYCODE_FEATURED_APP_3 = 299;
    public static final int KEYCODE_FEATURED_APP_4 = 300, KEYCODE_FOCUS = 80, KEYCODE_FORWARD = 125, KEYCODE_FORWARD_DEL = 112;
    public static final int KEYCODE_FUNCTION = 119, KEYCODE_G = 35, KEYCODE_GRAVE = 68, KEYCODE_GUIDE = 172;
    public static final int KEYCODE_H = 36, KEYCODE_HEADSETHOOK = 79, KEYCODE_HELP = 259, KEYCODE_HENKAN = 214;
    public static final int KEYCODE_HOME = 3, KEYCODE_I = 37, KEYCODE_INFO = 165, KEYCODE_INSERT = 124;
    public static final int KEYCODE_J = 38, KEYCODE_K = 39, KEYCODE_KANA = 218, KEYCODE_KATAKANA_HIRAGANA = 215;
    public static final int KEYCODE_KEYBOARD_BACKLIGHT_DOWN = 305, KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE = 307, KEYCODE_KEYBOARD_BACKLIGHT_UP = 306, KEYCODE_L = 40;
    public static final int KEYCODE_LANGUAGE_SWITCH = 204, KEYCODE_LAST_CHANNEL = 229, KEYCODE_LEFT_BRACKET = 71, KEYCODE_M = 41;
    public static final int KEYCODE_MACRO_1 = 313, KEYCODE_MACRO_2 = 314, KEYCODE_MACRO_3 = 315, KEYCODE_MACRO_4 = 316;
    public static final int KEYCODE_MANNER_MODE = 205, KEYCODE_MEDIA_AUDIO_TRACK = 222, KEYCODE_MEDIA_CLOSE = 128, KEYCODE_MEDIA_EJECT = 129;
    public static final int KEYCODE_MEDIA_FAST_FORWARD = 90, KEYCODE_MEDIA_NEXT = 87, KEYCODE_MEDIA_PAUSE = 127, KEYCODE_MEDIA_PLAY = 126;
    public static final int KEYCODE_MEDIA_PLAY_PAUSE = 85, KEYCODE_MEDIA_PREVIOUS = 88, KEYCODE_MEDIA_RECORD = 130, KEYCODE_MEDIA_REWIND = 89;
    public static final int KEYCODE_MEDIA_SKIP_BACKWARD = 273, KEYCODE_MEDIA_SKIP_FORWARD = 272, KEYCODE_MEDIA_STEP_BACKWARD = 275, KEYCODE_MEDIA_STEP_FORWARD = 274;
    public static final int KEYCODE_MEDIA_STOP = 86, KEYCODE_MEDIA_TOP_MENU = 226, KEYCODE_MENU = 82, KEYCODE_META_LEFT = 117;
    public static final int KEYCODE_META_RIGHT = 118, KEYCODE_MINUS = 69, KEYCODE_MOVE_END = 123, KEYCODE_MOVE_HOME = 122;
    public static final int KEYCODE_MUHENKAN = 213, KEYCODE_MUSIC = 209, KEYCODE_MUTE = 91, KEYCODE_N = 42;
    public static final int KEYCODE_NAVIGATE_IN = 262, KEYCODE_NAVIGATE_NEXT = 261, KEYCODE_NAVIGATE_OUT = 263, KEYCODE_NAVIGATE_PREVIOUS = 260;
    public static final int KEYCODE_NOTIFICATION = 83, KEYCODE_NUM = 78, KEYCODE_NUMPAD_0 = 144, KEYCODE_NUMPAD_1 = 145;
    public static final int KEYCODE_NUMPAD_2 = 146, KEYCODE_NUMPAD_3 = 147, KEYCODE_NUMPAD_4 = 148, KEYCODE_NUMPAD_5 = 149;
    public static final int KEYCODE_NUMPAD_6 = 150, KEYCODE_NUMPAD_7 = 151, KEYCODE_NUMPAD_8 = 152, KEYCODE_NUMPAD_9 = 153;
    public static final int KEYCODE_NUMPAD_ADD = 157, KEYCODE_NUMPAD_COMMA = 159, KEYCODE_NUMPAD_DIVIDE = 154, KEYCODE_NUMPAD_DOT = 158;
    public static final int KEYCODE_NUMPAD_ENTER = 160, KEYCODE_NUMPAD_EQUALS = 161, KEYCODE_NUMPAD_LEFT_PAREN = 162, KEYCODE_NUMPAD_MULTIPLY = 155;
    public static final int KEYCODE_NUMPAD_RIGHT_PAREN = 163, KEYCODE_NUMPAD_SUBTRACT = 156, KEYCODE_NUM_LOCK = 143, KEYCODE_O = 43;
    public static final int KEYCODE_P = 44, KEYCODE_PAGE_DOWN = 93, KEYCODE_PAGE_UP = 92, KEYCODE_PAIRING = 225;
    public static final int KEYCODE_PASTE = 279, KEYCODE_PERIOD = 56, KEYCODE_PICTSYMBOLS = 94, KEYCODE_PLUS = 81;
    public static final int KEYCODE_POUND = 18, KEYCODE_POWER = 26, KEYCODE_PROFILE_SWITCH = 288, KEYCODE_PROG_BLUE = 186;
    public static final int KEYCODE_PROG_GREEN = 184, KEYCODE_PROG_RED = 183, KEYCODE_PROG_YELLOW = 185, KEYCODE_Q = 45;
    public static final int KEYCODE_R = 46, KEYCODE_RECENT_APPS = 312, KEYCODE_REFRESH = 285, KEYCODE_RIGHT_BRACKET = 72;
    public static final int KEYCODE_RO = 217, KEYCODE_S = 47, KEYCODE_SCREENSHOT = 318, KEYCODE_SCROLL_LOCK = 116;
    public static final int KEYCODE_SEARCH = 84, KEYCODE_SEMICOLON = 74, KEYCODE_SETTINGS = 176, KEYCODE_SHIFT_LEFT = 59;
    public static final int KEYCODE_SHIFT_RIGHT = 60, KEYCODE_SLASH = 76, KEYCODE_SLEEP = 223, KEYCODE_SOFT_LEFT = 1;
    public static final int KEYCODE_SOFT_RIGHT = 2, KEYCODE_SOFT_SLEEP = 276, KEYCODE_SPACE = 62, KEYCODE_STAR = 17;
    public static final int KEYCODE_STB_INPUT = 180, KEYCODE_STB_POWER = 179, KEYCODE_STEM_1 = 265, KEYCODE_STEM_2 = 266;
    public static final int KEYCODE_STEM_3 = 267, KEYCODE_STEM_PRIMARY = 264, KEYCODE_STYLUS_BUTTON_PRIMARY = 308, KEYCODE_STYLUS_BUTTON_SECONDARY = 309;
    public static final int KEYCODE_STYLUS_BUTTON_TAIL = 311, KEYCODE_STYLUS_BUTTON_TERTIARY = 310, KEYCODE_SWITCH_CHARSET = 95, KEYCODE_SYM = 63;
    public static final int KEYCODE_SYSRQ = 120, KEYCODE_SYSTEM_NAVIGATION_DOWN = 281, KEYCODE_SYSTEM_NAVIGATION_LEFT = 282, KEYCODE_SYSTEM_NAVIGATION_RIGHT = 283;
    public static final int KEYCODE_SYSTEM_NAVIGATION_UP = 280, KEYCODE_T = 48, KEYCODE_TAB = 61, KEYCODE_THUMBS_DOWN = 287;
    public static final int KEYCODE_THUMBS_UP = 286, KEYCODE_TV = 170, KEYCODE_TV_ANTENNA_CABLE = 242, KEYCODE_TV_AUDIO_DESCRIPTION = 252;
    public static final int KEYCODE_TV_AUDIO_DESCRIPTION_MIX_DOWN = 254, KEYCODE_TV_AUDIO_DESCRIPTION_MIX_UP = 253, KEYCODE_TV_CONTENTS_MENU = 256, KEYCODE_TV_DATA_SERVICE = 230;
    public static final int KEYCODE_TV_INPUT = 178, KEYCODE_TV_INPUT_COMPONENT_1 = 249, KEYCODE_TV_INPUT_COMPONENT_2 = 250, KEYCODE_TV_INPUT_COMPOSITE_1 = 247;
    public static final int KEYCODE_TV_INPUT_COMPOSITE_2 = 248, KEYCODE_TV_INPUT_HDMI_1 = 243, KEYCODE_TV_INPUT_HDMI_2 = 244, KEYCODE_TV_INPUT_HDMI_3 = 245;
    public static final int KEYCODE_TV_INPUT_HDMI_4 = 246, KEYCODE_TV_INPUT_VGA_1 = 251, KEYCODE_TV_MEDIA_CONTEXT_MENU = 257, KEYCODE_TV_NETWORK = 241;
    public static final int KEYCODE_TV_NUMBER_ENTRY = 234, KEYCODE_TV_POWER = 177, KEYCODE_TV_RADIO_SERVICE = 232, KEYCODE_TV_SATELLITE = 237;
    public static final int KEYCODE_TV_SATELLITE_BS = 238, KEYCODE_TV_SATELLITE_CS = 239, KEYCODE_TV_SATELLITE_SERVICE = 240, KEYCODE_TV_TELETEXT = 233;
    public static final int KEYCODE_TV_TERRESTRIAL_ANALOG = 235, KEYCODE_TV_TERRESTRIAL_DIGITAL = 236, KEYCODE_TV_TIMER_PROGRAMMING = 258, KEYCODE_TV_ZOOM_MODE = 255;
    public static final int KEYCODE_U = 49, KEYCODE_UNKNOWN = 0, KEYCODE_V = 50, KEYCODE_VIDEO_APP_1 = 289;
    public static final int KEYCODE_VIDEO_APP_2 = 290, KEYCODE_VIDEO_APP_3 = 291, KEYCODE_VIDEO_APP_4 = 292, KEYCODE_VIDEO_APP_5 = 293;
    public static final int KEYCODE_VIDEO_APP_6 = 294, KEYCODE_VIDEO_APP_7 = 295, KEYCODE_VIDEO_APP_8 = 296, KEYCODE_VOICE_ASSIST = 231;
    public static final int KEYCODE_VOLUME_DOWN = 25, KEYCODE_VOLUME_MUTE = 164, KEYCODE_VOLUME_UP = 24, KEYCODE_W = 51;
    public static final int KEYCODE_WAKEUP = 224, KEYCODE_WINDOW = 171, KEYCODE_X = 52, KEYCODE_Y = 53;
    public static final int KEYCODE_YEN = 216, KEYCODE_Z = 54, KEYCODE_ZENKAKU_HANKAKU = 211, KEYCODE_ZOOM_IN = 168;
    public static final int KEYCODE_ZOOM_OUT = 169, MAX_KEYCODE = 84, META_ALT_LEFT_ON = 16, META_ALT_MASK = 50;
    public static final int META_ALT_ON = 2, META_ALT_RIGHT_ON = 32, META_CAPS_LOCK_ON = 1048576, META_CTRL_LEFT_ON = 8192;
    public static final int META_CTRL_MASK = 28672, META_CTRL_ON = 4096, META_CTRL_RIGHT_ON = 16384, META_FUNCTION_ON = 8;
    public static final int META_META_LEFT_ON = 131072, META_META_MASK = 458752, META_META_ON = 65536, META_META_RIGHT_ON = 262144;
    public static final int META_NUM_LOCK_ON = 2097152, META_SCROLL_LOCK_ON = 4194304, META_SHIFT_LEFT_ON = 64, META_SHIFT_MASK = 193;
    public static final int META_SHIFT_ON = 1, META_SHIFT_RIGHT_ON = 128, META_SYM_ON = 4;
    private static final int START_TRACKING = 0x40000000;
    private static final int MODIFIERS = META_SHIFT_MASK | META_ALT_MASK | META_CTRL_MASK | META_META_MASK | META_SYM_ON | META_FUNCTION_ON;
    private static final int ALL_META = MODIFIERS | META_CAPS_LOCK_ON | META_NUM_LOCK_ON | META_SCROLL_LOCK_ON;
    private long down, time;
    private int action, key, repeat, meta, device, scan, flags, source;
    private String characters;
    private boolean recycled;
    public interface Callback {
        boolean onKeyDown(int keyCode, KeyEvent event);
        boolean onKeyLongPress(int keyCode, KeyEvent event);
        boolean onKeyUp(int keyCode, KeyEvent event);
        boolean onKeyMultiple(int keyCode, int count, KeyEvent event);
    }
    public KeyEvent(int action, int key) { this(0,0,action,key,0,0,-1,0,0,0); }
    public KeyEvent(long down, long time, int action, int key, int repeat) { this(down,time,action,key,repeat,0); }
    public KeyEvent(long down, long time, int action, int key, int repeat, int meta) { this(down,time,action,key,repeat,meta,-1,0); }
    public KeyEvent(long down, long time, int action, int key, int repeat, int meta, int device, int scan) { this(down,time,action,key,repeat,meta,device,scan,0); }
    public KeyEvent(long down, long time, int action, int key, int repeat, int meta, int device, int scan, int flags) { this(down,time,action,key,repeat,meta,device,scan,flags,0); }
    public KeyEvent(long down, long time, int action, int key, int repeat, int meta, int device, int scan, int flags, int source) {
        if (action < 0 || action > 2 || repeat < 0) throw new IllegalArgumentException("Invalid key action or repeat count");
        this.down = down; this.time = time; this.action = action; this.key = key; this.repeat = repeat; this.meta = meta; this.device = device; this.scan = scan; this.flags = flags; this.source = source;
    }
    public KeyEvent(long time, String characters, int device, int flags) { this(time,time,ACTION_MULTIPLE,KEYCODE_UNKNOWN,0,0,device,0,flags,0x101); this.characters = Objects.requireNonNull(characters); }
    public KeyEvent(KeyEvent value) { this(value.getDownTime(),value.getEventTime(),value.getAction(),value.getKeyCode(),value.getRepeatCount(),value.getMetaState(),value.getDeviceId(),value.getScanCode(),value.getFlags(),value.getSource()); characters = value.characters; }
    public KeyEvent(KeyEvent value, long time, int repeat) { this(value); if (repeat < 0) throw new IllegalArgumentException("repeat"); this.time = time; this.repeat = repeat; }
    public final boolean isSystem() { return systemKey(getKeyCode()); }
    public static KeyEvent obtain(KeyEvent value) { return new KeyEvent(value); }
    public static KeyEvent changeAction(KeyEvent value, int action) { KeyEvent copy = new KeyEvent(value); if (action < 0 || action > 2) throw new IllegalArgumentException("action"); copy.action = action; return copy; }
    public static KeyEvent changeFlags(KeyEvent value, int flags) { KeyEvent copy = new KeyEvent(value); copy.flags = flags; return copy; }
    public static KeyEvent changeTimeRepeat(KeyEvent value, long time, int repeat) { return changeTimeRepeat(value,time,repeat,value.getFlags()); }
    public static KeyEvent changeTimeRepeat(KeyEvent value, long time, int repeat, int flags) { if (repeat < 0) throw new IllegalArgumentException("repeat"); KeyEvent copy = new KeyEvent(value); copy.time = time; copy.repeat = repeat; copy.flags = flags; return copy; }
    private void live() { if (recycled) throw new IllegalStateException("KeyEvent is recycled"); }
    public void recycle() { live(); recycled = true; }
    public int getAction() { live(); return action; }
    public int getKeyCode() { live(); return key; }
    public int getScanCode() { live(); return scan; }
    public int getRepeatCount() { live(); return repeat; }
    public int getMetaState() { live(); return meta; }
    public int getModifiers() { return normalizeMetaState(getMetaState()) & MODIFIERS; }
    public int getFlags() { live(); return flags; }
    public int getDeviceId() { live(); return device; }
    public long getDownTime() { live(); return down; }
    public long getEventTime() { live(); return time; }
    public int getSource() { live(); return source; }
    public void setSource(int source) { live(); this.source = source; }
    public String getCharacters() { live(); return characters; }
    public final boolean isCanceled() { return (getFlags() & FLAG_CANCELED) != 0; }
    public final boolean isTracking() { return (getFlags() & FLAG_TRACKING) != 0; }
    public final boolean isLongPress() { return (getFlags() & FLAG_LONG_PRESS) != 0; }
    public final void startTracking() { live(); flags |= START_TRACKING; }
    public boolean isShiftPressed() { return (getMetaState() & META_SHIFT_ON) != 0; }
    public boolean isAltPressed() { return (getMetaState() & META_ALT_ON) != 0; }
    public boolean isCtrlPressed() { return (getMetaState() & META_CTRL_ON) != 0; }
    public boolean isMetaPressed() { return (getMetaState() & META_META_ON) != 0; }
    public boolean isSymPressed() { return (getMetaState() & META_SYM_ON) != 0; }
    public boolean isFunctionPressed() { return (getMetaState() & META_FUNCTION_ON) != 0; }
    public boolean isCapsLockOn() { return (getMetaState() & META_CAPS_LOCK_ON) != 0; }
    public boolean isNumLockOn() { return (getMetaState() & META_NUM_LOCK_ON) != 0; }
    public boolean isScrollLockOn() { return (getMetaState() & META_SCROLL_LOCK_ON) != 0; }
    public boolean hasModifiers(int modifiers) { return metaStateHasModifiers(getMetaState(),modifiers); }
    public boolean hasNoModifiers() { return metaStateHasNoModifiers(getMetaState()); }
    public static int normalizeMetaState(int state) {
        if ((state & (META_SHIFT_LEFT_ON | META_SHIFT_RIGHT_ON)) != 0) state |= META_SHIFT_ON;
        if ((state & (META_ALT_LEFT_ON | META_ALT_RIGHT_ON)) != 0) state |= META_ALT_ON;
        if ((state & (META_CTRL_LEFT_ON | META_CTRL_RIGHT_ON)) != 0) state |= META_CTRL_ON;
        if ((state & (META_META_LEFT_ON | META_META_RIGHT_ON)) != 0) state |= META_META_ON;
        if ((state & 0x100) != 0) state |= META_CAPS_LOCK_ON;
        if ((state & 0x200) != 0) state |= META_ALT_ON;
        if ((state & 0x400) != 0) state |= META_SYM_ON;
        return state & ALL_META;
    }
    private static int directional(int state, int requested, int aggregate, int left, int right) {
        if ((requested & aggregate) != 0) { if ((requested & (left | right)) != 0) throw new IllegalArgumentException("Generic and directional modifiers cannot be combined"); return state & ~(left | right); }
        return (requested & (left | right)) != 0 ? state & ~aggregate : state;
    }
    public static boolean metaStateHasNoModifiers(int state) { return (normalizeMetaState(state) & MODIFIERS) == 0; }
    public static boolean metaStateHasModifiers(int state, int modifiers) {
        if ((modifiers & ~MODIFIERS) != 0) throw new IllegalArgumentException("Invalid modifier mask");
        state = normalizeMetaState(state) & MODIFIERS;
        state = directional(state,modifiers,META_SHIFT_ON,META_SHIFT_LEFT_ON,META_SHIFT_RIGHT_ON);
        state = directional(state,modifiers,META_ALT_ON,META_ALT_LEFT_ON,META_ALT_RIGHT_ON);
        state = directional(state,modifiers,META_CTRL_ON,META_CTRL_LEFT_ON,META_CTRL_RIGHT_ON);
        state = directional(state,modifiers,META_META_ON,META_META_LEFT_ON,META_META_RIGHT_ON);
        return state == modifiers;
    }
    public static int getModifierMetaStateMask() { return MODIFIERS; }
    public static boolean isModifierKey(int key) { return key == KEYCODE_SHIFT_LEFT || key == KEYCODE_SHIFT_RIGHT || key == KEYCODE_ALT_LEFT || key == KEYCODE_ALT_RIGHT || key == KEYCODE_CTRL_LEFT || key == KEYCODE_CTRL_RIGHT || key == KEYCODE_META_LEFT || key == KEYCODE_META_RIGHT || key == KEYCODE_SYM || key == KEYCODE_FUNCTION; }
    private static boolean systemKey(int key) {
        switch (key) {
            case KEYCODE_MENU: case KEYCODE_SOFT_RIGHT: case KEYCODE_HOME: case KEYCODE_RECENT_APPS:
            case KEYCODE_BACK: case KEYCODE_CALL: case KEYCODE_ENDCALL: case KEYCODE_VOLUME_UP:
            case KEYCODE_VOLUME_DOWN: case KEYCODE_VOLUME_MUTE: case KEYCODE_MUTE: case KEYCODE_POWER:
            case KEYCODE_HEADSETHOOK: case KEYCODE_MEDIA_PLAY: case KEYCODE_MEDIA_PAUSE: case KEYCODE_MEDIA_PLAY_PAUSE:
            case KEYCODE_MEDIA_STOP: case KEYCODE_MEDIA_NEXT: case KEYCODE_MEDIA_PREVIOUS: case KEYCODE_MEDIA_REWIND:
            case KEYCODE_MEDIA_RECORD: case KEYCODE_MEDIA_FAST_FORWARD: case KEYCODE_CAMERA: case KEYCODE_FOCUS:
            case KEYCODE_SEARCH: case KEYCODE_BRIGHTNESS_DOWN: case KEYCODE_BRIGHTNESS_UP: case KEYCODE_KEYBOARD_BACKLIGHT_DOWN:
            case KEYCODE_KEYBOARD_BACKLIGHT_UP: case KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE: case KEYCODE_MEDIA_AUDIO_TRACK: case KEYCODE_SYSTEM_NAVIGATION_UP:
            case KEYCODE_SYSTEM_NAVIGATION_DOWN: case KEYCODE_SYSTEM_NAVIGATION_LEFT: case KEYCODE_SYSTEM_NAVIGATION_RIGHT: case KEYCODE_STEM_PRIMARY:
                return true;
            default: return false;
        }
    }
    public static boolean isMediaSessionKey(int key) {
        switch (key) {
            case KEYCODE_MEDIA_PLAY: case KEYCODE_MEDIA_PAUSE: case KEYCODE_MEDIA_PLAY_PAUSE: case KEYCODE_HEADSETHOOK:
            case KEYCODE_MEDIA_STOP: case KEYCODE_MEDIA_NEXT: case KEYCODE_MEDIA_PREVIOUS: case KEYCODE_MEDIA_REWIND:
            case KEYCODE_MEDIA_RECORD: case KEYCODE_MEDIA_FAST_FORWARD:
                return true;
            default: return false;
        }
    }
    public static boolean isGamepadButton(int key) { return key >= KEYCODE_BUTTON_A && key <= KEYCODE_BUTTON_MODE || key >= KEYCODE_BUTTON_1 && key <= KEYCODE_BUTTON_16; }
    public static boolean isConfirmKey(int key) { return key == KEYCODE_DPAD_CENTER || key == KEYCODE_ENTER || key == KEYCODE_SPACE || key == KEYCODE_NUMPAD_ENTER; }
    public final boolean dispatch(Callback receiver) { return dispatch(receiver,null,null); }
    public final boolean dispatch(Callback receiver, DispatcherState state, Object target) {
        live(); Objects.requireNonNull(receiver);
        if (action == ACTION_DOWN) {
            flags &= ~START_TRACKING; boolean handled = receiver.onKeyDown(key,this);
            if (state != null) {
                if (handled && repeat == 0 && (flags & START_TRACKING) != 0) state.startTracking(this,target);
                else if (isLongPress() && state.isTracking(this) && receiver.onKeyLongPress(key,this)) { state.performedLongPress(this); handled = true; }
            }
            return handled;
        }
        if (action == ACTION_UP) { if (state != null) state.handleUpEvent(this); return receiver.onKeyUp(key,this); }
        if (receiver.onKeyMultiple(key,repeat,this)) return true;
        if (key == KEYCODE_UNKNOWN) return false;
        int previous = repeat;
        try { action = ACTION_DOWN; repeat = 0; boolean handled = receiver.onKeyDown(key,this); if (handled) { action = ACTION_UP; receiver.onKeyUp(key,this); } return handled; }
        finally { action = ACTION_MULTIPLE; repeat = previous; }
    }
    public static class DispatcherState {
        private int key;
        private Object target;
        private final HashSet<Integer> longPress = new HashSet<Integer>();
        public void reset() { key = 0; target = null; longPress.clear(); }
        public void reset(Object target) { if (this.target == target) { key = 0; this.target = null; } }
        public void startTracking(KeyEvent event, Object target) { if (event.getAction() != ACTION_DOWN) throw new IllegalArgumentException("Tracking requires a down event"); key = event.getKeyCode(); this.target = target; }
        public boolean isTracking(KeyEvent event) { return key == event.getKeyCode(); }
        public void performedLongPress(KeyEvent event) { if (longPress.size() >= 512) throw new IllegalStateException("Long-press tracking limit exceeded"); longPress.add(event.getKeyCode()); }
        public void handleUpEvent(KeyEvent event) { event.live(); if (longPress.remove(event.key)) event.flags |= FLAG_CANCELED | FLAG_CANCELED_LONG_PRESS; if (key == event.key) { event.flags |= FLAG_TRACKING; key = 0; target = null; } }
    }
    private static final class Names {
        static final HashMap<Integer,String> codes = new HashMap<Integer,String>();
        static final HashMap<String,Integer> names = new HashMap<String,Integer>();
        static int max;
        static {
            for (java.lang.reflect.Field field : KeyEvent.class.getFields()) if (field.getName().startsWith("KEYCODE_")) {
                try { int code = field.getInt(null); codes.put(code,field.getName()); names.put(field.getName(),code); max = Math.max(max,code); }
                catch (IllegalAccessException error) { throw new AssertionError(error); }
            }
        }
    }
    public static int getMaxKeyCode() { return Names.max; }
    public static String keyCodeToString(int key) { String name = Names.codes.get(key); return name == null ? Integer.toString(key) : name; }
    public static int keyCodeFromString(String name) {
        Objects.requireNonNull(name); try { int value = Integer.parseInt(name); return value >= 0 && value <= getMaxKeyCode() ? value : KEYCODE_UNKNOWN; }
        catch (NumberFormatException ignored) { Integer value = Names.names.get(name.startsWith("KEYCODE_") ? name : "KEYCODE_" + name); return value == null ? KEYCODE_UNKNOWN : value; }
    }
    public static String actionToString(int action) { return action == ACTION_DOWN ? "ACTION_DOWN" : action == ACTION_UP ? "ACTION_UP" : action == ACTION_MULTIPLE ? "ACTION_MULTIPLE" : Integer.toString(action); }
    public KeyCharacterMap getKeyCharacterMap(){live();return KeyCharacterMap.load(device);}
    public int getUnicodeChar(){return getKeyCharacterMap().get(key,meta);}
    public int getUnicodeChar(int meta){return getKeyCharacterMap().get(key,meta);}
    public char getDisplayLabel(){return getKeyCharacterMap().getDisplayLabel(key);}
    public char getNumber(){return getKeyCharacterMap().getNumber(key);}
    public boolean isPrintingKey(){return getKeyCharacterMap().isPrintingKey(key);}
    public char getMatch(char[] chars){return getKeyCharacterMap().getMatch(key,chars);}
    public char getMatch(char[] chars,int meta){return getKeyCharacterMap().getMatch(key,chars,meta);}
    @Deprecated public boolean getKeyData(KeyCharacterMap.KeyData results){return getKeyCharacterMap().getKeyData(key,results);}
    @Override public String toString() { return "KeyEvent{" + actionToString(getAction()) + ", " + keyCodeToString(key) + ", repeat=" + repeat + "}"; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int parcelFlags) {
        live();out.writeInt(2);out.writeInt(device);out.writeInt(source);out.writeInt(action);out.writeInt(key);out.writeInt(repeat);out.writeInt(meta);out.writeInt(scan);out.writeInt(flags);out.writeLong(down);out.writeLong(time);out.writeString(characters);
    }
    public static final Parcelable.Creator<KeyEvent> CREATOR = new Parcelable.Creator<KeyEvent>() {
        public KeyEvent createFromParcel(Parcel in) {
            if(in.readInt()!=2)throw new IllegalArgumentException("Invalid key event parcel token");
            int device=in.readInt(),source=in.readInt(),action=in.readInt(),key=in.readInt(),repeat=in.readInt(),meta=in.readInt(),scan=in.readInt(),flags=in.readInt();
            KeyEvent event=new KeyEvent(in.readLong(),in.readLong(),action,key,repeat,meta,device,scan,flags,source);event.characters=in.readString();return event;
        }
        public KeyEvent[] newArray(int size) { return new KeyEvent[size]; }
    };
}
