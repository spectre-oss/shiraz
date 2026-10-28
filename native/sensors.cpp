// SPDX-License-Identifier: GPL-3.0-only
// Desktop NDK sensor enumeration: no sensor backend is currently connected.
// Empty queues never synthesize hardware readings or successful registrations.
#include <cerrno>
#include <cstddef>
#include <new>
#include <sys/types.h>
struct ALooper;
struct ASensor;
struct ASensorEvent;
struct ASensorManager {};
struct ASensorEventQueue { ALooper* looper; };
extern "C" void ALooper_acquire(ALooper*);
extern "C" void ALooper_release(ALooper*);
namespace { ASensorManager manager; const ASensor* const emptyList[]={nullptr}; }
extern "C" ASensorManager* ASensorManager_getInstance(){return &manager;}
extern "C" ASensorManager* ASensorManager_getInstanceForPackage(const char*){return &manager;}
extern "C" int ASensorManager_getSensorList(ASensorManager* owner,const ASensor* const** list){
 if(owner!=&manager||!list)return -EINVAL;
 *list=emptyList;return 0;
}
extern "C" const ASensor* ASensorManager_getDefaultSensor(ASensorManager*,int){return nullptr;}
extern "C" const ASensor* ASensorManager_getDefaultSensorEx(ASensorManager*,int,bool){return nullptr;}
extern "C" ASensorEventQueue* ASensorManager_createEventQueue(ASensorManager* owner,ALooper* looper,int ident,int (*callback)(int,int,void*),void*){
 if(owner!=&manager||!looper||(!callback&&ident<0))return nullptr;
 auto* queue=new(std::nothrow) ASensorEventQueue{looper};
 if(queue)ALooper_acquire(looper);
 return queue;
}
extern "C" int ASensorManager_destroyEventQueue(ASensorManager* owner,ASensorEventQueue* queue){
 if(owner!=&manager||!queue)return -EINVAL;
 ALooper_release(queue->looper);delete queue;return 0;
}
extern "C" int ASensorEventQueue_hasEvents(ASensorEventQueue* queue){return queue?0:-EINVAL;}
extern "C" ssize_t ASensorEventQueue_getEvents(ASensorEventQueue* queue,ASensorEvent* events,size_t count){return !queue||(!events&&count)?-EINVAL:0;}
extern "C" int ASensorEventQueue_enableSensor(ASensorEventQueue*,const ASensor*){return -ENODEV;}
extern "C" int ASensorEventQueue_disableSensor(ASensorEventQueue*,const ASensor*){return -ENODEV;}
extern "C" int ASensorEventQueue_setEventRate(ASensorEventQueue*,const ASensor*,int){return -ENODEV;}
extern "C" int ASensor_getType(const ASensor*){return -1;}
extern "C" float ASensor_getResolution(const ASensor*){return 0;}
extern "C" int ASensor_getMinDelay(const ASensor*){return 0;}
extern "C" const char* ASensor_getName(const ASensor*){return nullptr;}
extern "C" const char* ASensor_getVendor(const ASensor*){return nullptr;}
