// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/keyboard.hpp"
#include "shiraz/sdl_keyboard.hpp"
#include <cassert>
#include <vector>
#include <iostream>
int main() {
    using namespace shiraz;
    std::vector<KeyEvent> events;
    KeyboardStream keys([&](const KeyEvent& event) { events.push_back(event); });
    keys.up(4,0,1); keys.down(4,29,30,0,true,1); assert(events.empty());
    keys.down(4,29,30,0x3000,false,100);
    keys.down(4,30,48,0,false,200); assert(events.size() == 1); // Duplicate down cannot replace the captured key.
    keys.down(4,30,48,0,true,400); keys.down(4,30,48,0,true,600); keys.down(4,30,48,0,true,700);
    assert(events[0].down == 100 && events[0].key == 29 && events[0].scan == 30);
    assert(events[1].repeat == 1 && !(events[1].flags & 128) && events[1].key == 29 && events[1].scan == 30);
    assert(events[2].repeat == 2 && events[2].flags & 128 && !(events[3].flags & 128));
    keys.up(4,0,500); assert(events.back().time == 700 && events.back().down == 100 && events.back().repeat == 0);
    keys.down(5,30,48,65,false,800); keys.down(225,59,42,65,false,810); keys.cancel(900);
    assert(events[events.size()-2].key == 30 && events.back().key == 59 && events.back().flags & 32);
    assert(events.back().meta == 0); auto count = events.size(); keys.cancel(1000); keys.up(5,0,1000); assert(events.size() == count);
    keys.down(-1,1,1,0,false,1000); keys.down(512,1,1,0,false,1000); assert(events.size() == count);
    assert(keys.textTime(1100) == 1100); keys.down(4,29,30,0,false,1000); assert(events.back().time == 1100);
    assert(android_key(SDLK_a) == 29 && android_key(SDLK_z) == 54 && android_key(SDLK_0) == 7 && android_key(SDLK_9) == 16);
    assert(android_key(SDLK_RETURN) == 66 && android_key(SDLK_ESCAPE) == 111 && android_key(SDLK_AC_BACK) == 4);
    assert(android_key(SDLK_KP_0) == 144 && android_key(SDLK_KP_9) == 153 && android_key(SDLK_F12) == 142 && android_key(SDLK_UNKNOWN) == 0);
    assert(linux_scan(SDL_SCANCODE_A) == KEY_A && linux_scan(SDL_SCANCODE_Z) == KEY_Z && linux_scan(SDL_SCANCODE_F12) == KEY_F12);
    assert(android_meta(SDL_Keymod(KMOD_LCTRL | KMOD_RSHIFT | KMOD_CAPS)) == (4096 | 8192 | 1 | 128 | 0x100000));
    events.clear(); KeyboardStream editor([&](const KeyEvent& event){events.push_back(event);});
    editor.down(10,35,34,0,false,1200,true); // g, followed by one independent SDL text commit
    editor.down(10,35,34,0,true,1300,false); // focus change cannot change ownership mid-press
    editor.up(10,0,1400); assert(events.empty());
    editor.down(10,35,34,4096,false,1500,false);editor.up(10,0,1600);
    assert(events.size()==2 && events[0].key==35 && events[1].action==1);
    assert(editor_text_key(SDLK_g,KMOD_NONE) && editor_text_key(SDLK_g,KMOD_SHIFT));
    assert(!editor_text_key(SDLK_g,KMOD_CTRL) && !editor_text_key(SDLK_g,KMOD_LALT));
    assert(editor_text_key(SDLK_q,SDL_Keymod(KMOD_CTRL|KMOD_RALT)));
    assert(!editor_text_key(SDLK_BACKSPACE,KMOD_NONE) && !editor_text_key(SDLK_RETURN,KMOD_NONE));
    assert(!editor_text_key(SDLK_KP_2,KMOD_NONE));
    assert(!editor_text_key(SDLK_LEFT,KMOD_NONE) && editor_text_key(SDLK_KP_2,KMOD_NUM));
    std::cout << "KEYBOARD_STREAM_MAPPING_AND_CANCELLATION_OK\n";
}
