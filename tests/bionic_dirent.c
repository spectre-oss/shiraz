// SPDX-License-Identifier: GPL-3.0-only
#include <assert.h>
#include <dirent.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
extern struct dirent* bionic_readdir(DIR*);
int main(void) {
    DIR *first=opendir("."), *second=opendir(".");
    assert(first&&second);
    struct dirent* entry=bionic_readdir(first);assert(entry);
    char saved[256];snprintf(saved,sizeof(saved),"%s",entry->d_name);
    int count=0;
    while(bionic_readdir(second)) {
        assert(!strcmp(entry->d_name,saved));
        assert(++count<100000);
    }
    assert(count>1);closedir(second);closedir(first);
    puts("BIONIC_DIRENT_INDEPENDENT_STREAMS_OK");
}
