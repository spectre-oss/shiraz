// SPDX-License-Identifier: GPL-3.0-only
typedef __SIZE_TYPE__ size_t;
extern size_t strlen(const char*);
extern void* memchr(const void*, int, size_t);
extern void* memcpy(void*, const void*, size_t);
extern void* memset(void*, int, size_t);
extern int memcmp(const void*, const void*, size_t);
int Java_shiraz_Test_answer(void* env, void* klass) {
    (void)env; (void)klass;
    char data[8];
    if (memset(data, 0, sizeof(data)) != data) return -1;
    if (memcpy(data, "a\200b", 4) != data) return -2;
    if (strlen(data) != 3 || strlen("") != 0) return -3;
    if (memchr(data, 0x80, 3) != data + 1) return -4;
    if (memchr(data, 'z', 3) || memchr(data, 'a', 0)) return -5;
    if (memcmp(data, "a\200b", 4) || memcmp("a", "b", 1) >= 0) return -6;
    return 42;
}
