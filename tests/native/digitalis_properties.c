// SPDX-License-Identifier: GPL-3.0-only
#include <stdint.h>
struct prop_info;
extern const struct prop_info* __system_property_find(const char*);
extern int __system_property_read(const struct prop_info*,char*,char*);
extern int __system_property_get(const char*,char*);
extern int __system_property_foreach(void(*)(const struct prop_info*,void*),void*);
extern void __system_property_read_callback(const struct prop_info*,void(*)(void*,const char*,const char*,uint32_t),void*);
static void read_callback(void* cookie,const char* name,const char* value,uint32_t serial) {
    if (name[0]=='r' && value[0]=='2' && value[1]=='7' && value[2]==0 && serial==(2U<<24)) ++*(int*)cookie;
}
static void each(const struct prop_info* property,void* cookie) {
    char name[128],value[92];
    if (__system_property_read(property,name,value)>=0 && name[0]) ++*(int*)cookie;
}
int Java_shiraz_Test_answer(void* env,void* klass) {
    (void)env;(void)klass;
    char model[92];
    if (__system_property_get("ro.product.model", model) != 7 ||
        model[0] != 'F' || model[6] != 'e' || model[7] != 0) return -4;
    int count=0,matched=0;
    const struct prop_info* sdk=__system_property_find("ro.build.version.sdk");
    if (!sdk || __system_property_find("shiraz.nonexistent")!=0) return -1;
    __system_property_read_callback(sdk,read_callback,&matched);
    if (__system_property_foreach(each,&count)!=0) return -2;
    return matched==1 && count>=1 ? 42 : -3;
}
