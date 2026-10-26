// SPDX-License-Identifier: GPL-3.0-only
#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
extern char* bionic_getenv(const char*);
int main(void) {
    const char* expected=getenv("HOME");
    const char* actual=bionic_getenv("HOME");
    assert(expected ? actual && !strcmp(expected,actual) : !actual);
    assert(setenv("SHIRAZ_TEST_NATIVE_ENV","initial",1)==0);
    assert(!strcmp(bionic_getenv("SHIRAZ_TEST_NATIVE_ENV"),"initial"));
    assert(setenv("SHIRAZ_TEST_NATIVE_ENV","updated",1)==0);
    assert(!strcmp(bionic_getenv("SHIRAZ_TEST_NATIVE_ENV"),"updated"));
    assert(unsetenv("SHIRAZ_TEST_NATIVE_ENV")==0&&!bionic_getenv("SHIRAZ_TEST_NATIVE_ENV"));
    puts("BIONIC_ENVIRONMENT_OK");
}
