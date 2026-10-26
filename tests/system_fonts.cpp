// SPDX-License-Identifier: GPL-3.0-only
#include <cassert>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <unistd.h>
struct AFont;struct ASystemFontIterator;
extern "C" {ASystemFontIterator* ASystemFontIterator_open();void ASystemFontIterator_close(ASystemFontIterator*);AFont* ASystemFontIterator_next(ASystemFontIterator*);void AFont_close(AFont*);const char* AFont_getFontFilePath(const AFont*);uint16_t AFont_getWeight(const AFont*);size_t AFont_getCollectionIndex(const AFont*);}
int main(){
 auto* iterator=ASystemFontIterator_open();assert(iterator);AFont* first=ASystemFontIterator_next(iterator);assert(first);size_t count=1;
 while(AFont* font=ASystemFontIterator_next(iterator)){const char* path=AFont_getFontFilePath(font);assert(path&&path[0]=='/'&&access(path,R_OK)==0);assert(AFont_getWeight(font)>=1&&AFont_getWeight(font)<=1000);AFont_close(font);++count;assert(count<=4096);}
 assert(!ASystemFontIterator_next(iterator));ASystemFontIterator_close(iterator);
 // Font handles retain their strings after iterator deletion.
 assert(access(AFont_getFontFilePath(first),R_OK)==0);AFont_close(first);
 iterator=ASystemFontIterator_open();assert(iterator);first=ASystemFontIterator_next(iterator);assert(first);AFont_close(first);ASystemFontIterator_close(iterator);
 ASystemFontIterator_close(nullptr);AFont_close(nullptr);puts("SYSTEM_FONTS_OK");
}
