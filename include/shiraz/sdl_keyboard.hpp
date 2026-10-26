// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <SDL.h>
#include <linux/input-event-codes.h>
namespace shiraz {
// Printable editor keys are committed by SDL_TEXTINPUT. Keep shortcuts and controls
// on the physical-key path; AltGr (right Alt/MODE) remains layout-produced text.
inline bool editor_text_key(SDL_Keycode key, SDL_Keymod mods) {
    bool altgr = (mods & (KMOD_RALT | KMOD_MODE)) != 0;
    if ((mods & KMOD_GUI) || (!altgr && (mods & (KMOD_CTRL | KMOD_ALT)))) return false;
    if (key >= 32 && key < 0x110000) return true;
    return ((mods & KMOD_NUM) && ((key >= SDLK_KP_1 && key <= SDLK_KP_0) || key == SDLK_KP_PERIOD)) ||
        key == SDLK_KP_DIVIDE || key == SDLK_KP_MULTIPLY || key == SDLK_KP_MINUS ||
        key == SDLK_KP_PLUS || key == SDLK_KP_EQUALS;
}
inline int android_key(SDL_Keycode key) {
    if (key >= SDLK_a && key <= SDLK_z) return 29 + key - SDLK_a;
    if (key >= SDLK_0 && key <= SDLK_9) return 7 + key - SDLK_0;
    if (key >= SDLK_F1 && key <= SDLK_F12) return 131 + key - SDLK_F1;
    if (key >= SDLK_KP_1 && key <= SDLK_KP_9) return 145 + key - SDLK_KP_1;
    switch (key) {
        case SDLK_BACKSPACE: return 67; case SDLK_TAB: return 61; case SDLK_RETURN: return 66;
        case SDLK_ESCAPE: return 111; case SDLK_SPACE: return 62; case SDLK_DELETE: return 112;
        case SDLK_LEFT: return 21; case SDLK_RIGHT: return 22; case SDLK_UP: return 19; case SDLK_DOWN: return 20;
        case SDLK_HOME: return 122; case SDLK_END: return 123; case SDLK_PAGEUP: return 92; case SDLK_PAGEDOWN: return 93;
        case SDLK_INSERT: return 124; case SDLK_LSHIFT: return 59; case SDLK_RSHIFT: return 60;
        case SDLK_LALT: return 57; case SDLK_RALT: return 58; case SDLK_LCTRL: return 113; case SDLK_RCTRL: return 114;
        case SDLK_LGUI: return 117; case SDLK_RGUI: return 118; case SDLK_CAPSLOCK: return 115; case SDLK_NUMLOCKCLEAR: return 143; case SDLK_SCROLLLOCK: return 116;
        case SDLK_COMMA: return 55; case SDLK_PERIOD: return 56; case SDLK_BACKQUOTE: return 68; case SDLK_MINUS: return 69; case SDLK_EQUALS: return 70;
        case SDLK_LEFTBRACKET: return 71; case SDLK_RIGHTBRACKET: return 72; case SDLK_BACKSLASH: return 73; case SDLK_SEMICOLON: return 74; case SDLK_QUOTE: return 75; case SDLK_SLASH: return 76; case SDLK_AT: return 77; case SDLK_PLUS: return 81;
        case SDLK_KP_0: return 144; case SDLK_KP_ENTER: return 160; case SDLK_KP_PERIOD: return 158; case SDLK_KP_PLUS: return 157; case SDLK_KP_MINUS: return 156; case SDLK_KP_MULTIPLY: return 155; case SDLK_KP_DIVIDE: return 154; case SDLK_KP_EQUALS: return 161;
        case SDLK_VOLUMEUP: return 24; case SDLK_VOLUMEDOWN: return 25; case SDLK_AUDIOMUTE: return 164;
        case SDLK_AUDIOPLAY: return 85; case SDLK_AUDIOSTOP: return 86; case SDLK_AUDIONEXT: return 87; case SDLK_AUDIOPREV: return 88;
        case SDLK_AC_BACK: return 4; case SDLK_AC_FORWARD: return 125; case SDLK_MENU: case SDLK_APPLICATION: return 82;
        case SDLK_PRINTSCREEN: return 120; case SDLK_PAUSE: return 121;
        default: return 0;
    }
}
inline int linux_scan(SDL_Scancode scan) {
    static constexpr int letters[] = {KEY_A,KEY_B,KEY_C,KEY_D,KEY_E,KEY_F,KEY_G,KEY_H,KEY_I,KEY_J,KEY_K,KEY_L,KEY_M,KEY_N,KEY_O,KEY_P,KEY_Q,KEY_R,KEY_S,KEY_T,KEY_U,KEY_V,KEY_W,KEY_X,KEY_Y,KEY_Z};
    if (scan >= SDL_SCANCODE_A && scan <= SDL_SCANCODE_Z) return letters[scan - SDL_SCANCODE_A];
    if (scan >= SDL_SCANCODE_1 && scan <= SDL_SCANCODE_9) return KEY_1 + scan - SDL_SCANCODE_1;
    if (scan >= SDL_SCANCODE_F1 && scan <= SDL_SCANCODE_F10) return KEY_F1 + scan - SDL_SCANCODE_F1;
    switch (scan) {
        case SDL_SCANCODE_0: return KEY_0; case SDL_SCANCODE_RETURN: return KEY_ENTER; case SDL_SCANCODE_ESCAPE: return KEY_ESC;
        case SDL_SCANCODE_BACKSPACE: return KEY_BACKSPACE; case SDL_SCANCODE_TAB: return KEY_TAB; case SDL_SCANCODE_SPACE: return KEY_SPACE;
        case SDL_SCANCODE_LSHIFT: return KEY_LEFTSHIFT; case SDL_SCANCODE_RSHIFT: return KEY_RIGHTSHIFT; case SDL_SCANCODE_LCTRL: return KEY_LEFTCTRL; case SDL_SCANCODE_RCTRL: return KEY_RIGHTCTRL;
        case SDL_SCANCODE_LALT: return KEY_LEFTALT; case SDL_SCANCODE_RALT: return KEY_RIGHTALT; case SDL_SCANCODE_LGUI: return KEY_LEFTMETA; case SDL_SCANCODE_RGUI: return KEY_RIGHTMETA;
        case SDL_SCANCODE_LEFT: return KEY_LEFT; case SDL_SCANCODE_RIGHT: return KEY_RIGHT; case SDL_SCANCODE_UP: return KEY_UP; case SDL_SCANCODE_DOWN: return KEY_DOWN;
        case SDL_SCANCODE_HOME: return KEY_HOME; case SDL_SCANCODE_END: return KEY_END; case SDL_SCANCODE_PAGEUP: return KEY_PAGEUP; case SDL_SCANCODE_PAGEDOWN: return KEY_PAGEDOWN;
        case SDL_SCANCODE_INSERT: return KEY_INSERT; case SDL_SCANCODE_DELETE: return KEY_DELETE; case SDL_SCANCODE_F11: return KEY_F11; case SDL_SCANCODE_F12: return KEY_F12;
        case SDL_SCANCODE_CAPSLOCK: return KEY_CAPSLOCK; case SDL_SCANCODE_NUMLOCKCLEAR: return KEY_NUMLOCK; case SDL_SCANCODE_SCROLLLOCK: return KEY_SCROLLLOCK;
        case SDL_SCANCODE_COMMA: return KEY_COMMA; case SDL_SCANCODE_PERIOD: return KEY_DOT; case SDL_SCANCODE_MINUS: return KEY_MINUS; case SDL_SCANCODE_EQUALS: return KEY_EQUAL;
        case SDL_SCANCODE_LEFTBRACKET: return KEY_LEFTBRACE; case SDL_SCANCODE_RIGHTBRACKET: return KEY_RIGHTBRACE; case SDL_SCANCODE_BACKSLASH: return KEY_BACKSLASH;
        case SDL_SCANCODE_SEMICOLON: return KEY_SEMICOLON; case SDL_SCANCODE_APOSTROPHE: return KEY_APOSTROPHE; case SDL_SCANCODE_GRAVE: return KEY_GRAVE; case SDL_SCANCODE_SLASH: return KEY_SLASH;
        case SDL_SCANCODE_KP_ENTER: return KEY_KPENTER; case SDL_SCANCODE_KP_0: return KEY_KP0; case SDL_SCANCODE_KP_1: return KEY_KP1; case SDL_SCANCODE_KP_2: return KEY_KP2; case SDL_SCANCODE_KP_3: return KEY_KP3;
        case SDL_SCANCODE_KP_4: return KEY_KP4; case SDL_SCANCODE_KP_5: return KEY_KP5; case SDL_SCANCODE_KP_6: return KEY_KP6; case SDL_SCANCODE_KP_7: return KEY_KP7; case SDL_SCANCODE_KP_8: return KEY_KP8; case SDL_SCANCODE_KP_9: return KEY_KP9;
        case SDL_SCANCODE_KP_PLUS: return KEY_KPPLUS; case SDL_SCANCODE_KP_MINUS: return KEY_KPMINUS; case SDL_SCANCODE_KP_MULTIPLY: return KEY_KPASTERISK; case SDL_SCANCODE_KP_DIVIDE: return KEY_KPSLASH; case SDL_SCANCODE_KP_PERIOD: return KEY_KPDOT;
        case SDL_SCANCODE_AC_BACK: return KEY_BACK; case SDL_SCANCODE_AC_FORWARD: return KEY_FORWARD;
        default: return 0;
    }
}
inline int android_meta(SDL_Keymod modifiers) {
    int result = 0;
    if (modifiers & KMOD_LSHIFT) result |= 1 | 64;
    if (modifiers & KMOD_RSHIFT) result |= 1 | 128;
    if (modifiers & KMOD_LALT) result |= 2 | 16;
    if (modifiers & KMOD_RALT) result |= 2 | 32;
    if (modifiers & KMOD_LCTRL) result |= 4096 | 8192;
    if (modifiers & KMOD_RCTRL) result |= 4096 | 16384;
    if (modifiers & KMOD_LGUI) result |= 65536 | 131072;
    if (modifiers & KMOD_RGUI) result |= 65536 | 262144;
    if (modifiers & KMOD_CAPS) result |= 0x100000;
    if (modifiers & KMOD_NUM) result |= 0x200000;
    if (modifiers & KMOD_SCROLL) result |= 0x400000;
    // AltGr's MODE bit has no independent Android modifier. Actual text is
    // supplied by SDL_TEXTINPUT instead of guessing a US character mapping.
    return result;
}
}
