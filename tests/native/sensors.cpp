// SPDX-License-Identifier: GPL-3.0-only
#include <cassert>
#include <cerrno>
#include <cstddef>
#include <cstdio>
#include <sys/types.h>
struct ALooper;struct ASensorManager;struct ASensor;struct ASensorEvent;struct ASensorEventQueue;
extern "C" {
ALooper* ALooper_prepare(int);
ASensorManager* ASensorManager_getInstance();
int ASensorManager_getSensorList(ASensorManager*,const ASensor* const**);
const ASensor* ASensorManager_getDefaultSensor(ASensorManager*,int);
ASensorEventQueue* ASensorManager_createEventQueue(ASensorManager*,ALooper*,int,int (*)(int,int,void*),void*);
int ASensorManager_destroyEventQueue(ASensorManager*,ASensorEventQueue*);
int ASensorEventQueue_hasEvents(ASensorEventQueue*);
ssize_t ASensorEventQueue_getEvents(ASensorEventQueue*,ASensorEvent*,size_t);
int ASensorEventQueue_enableSensor(ASensorEventQueue*,const ASensor*);
}
int main(){
 auto* manager=ASensorManager_getInstance();const ASensor* const* list=nullptr;
 assert(manager&&ASensorManager_getSensorList(manager,&list)==0&&list);
 assert(ASensorManager_getSensorList(manager,nullptr)==-EINVAL);
 assert(!ASensorManager_getDefaultSensor(manager,1));
 assert(!ASensorManager_createEventQueue(manager,nullptr,1,nullptr,nullptr));
 for(int i=0;i<1000;i++){
  auto* queue=ASensorManager_createEventQueue(manager,ALooper_prepare(1),1,nullptr,nullptr);
  assert(queue&&ASensorEventQueue_hasEvents(queue)==0);
  assert(ASensorEventQueue_getEvents(queue,nullptr,0)==0);
  assert(ASensorEventQueue_getEvents(queue,nullptr,1)==-EINVAL);
  assert(ASensorEventQueue_enableSensor(queue,nullptr)==-ENODEV);
  assert(ASensorManager_destroyEventQueue(manager,queue)==0);
 }
 std::puts("NDK_NO_SENSORS_OK");
}
