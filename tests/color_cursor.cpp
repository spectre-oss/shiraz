// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/presentation.hpp"
#include <SDL.h>
#include <cassert>
#include <cstdlib>
#include <stdexcept>
int main(){
 uint32_t pixels[]={0xffff0000,0xff00ff00,0xff0000ff,0};
 bool rejected=false;
 try{shiraz::request_color_pointer_icon(pixels,2,2,1,0);}catch(const std::runtime_error&){rejected=true;}
 assert(rejected);
 shiraz::present(shiraz::Surface(32,32,0xff000000),"color cursor",1,[&](shiraz::Surface&){
  SDL_Cursor* previous=SDL_GetCursor();
  for(int i=0;i<20;i++){
   shiraz::request_color_pointer_icon(pixels,2,2,i%2,0);
   if(std::getenv("SHIRAZ_TEST_REAL_CURSOR")){assert(SDL_GetCursor());assert(SDL_GetCursor()!=previous);previous=SDL_GetCursor();}
  }
  rejected=false;
  try{shiraz::request_color_pointer_icon(pixels,2,2,2,0);}catch(const std::invalid_argument&){rejected=true;}
  assert(rejected);
  if(std::getenv("SHIRAZ_TEST_REAL_CURSOR"))assert(SDL_GetCursor()==previous);
  shiraz::request_pointer_icon(0);assert(SDL_ShowCursor(SDL_QUERY)==SDL_DISABLE);
  shiraz::request_pointer_icon(1000);assert(SDL_ShowCursor(SDL_QUERY)==SDL_ENABLE);
  return true;
 });
 return 0;
}
