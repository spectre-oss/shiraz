// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
static jint answer(JNIEnv* env, jclass klass) { return 42; }
static jobjectArray values(JNIEnv* env,jclass klass) {
    jclass object=(*env)->FindClass(env,"java/lang/Object");
    jobjectArray result=(*env)->NewObjectArray(env,2,object,0);
    jintArray numbers=(*env)->NewIntArray(env,1);jint number=42;
    (*env)->SetIntArrayRegion(env,numbers,0,1,&number);(*env)->SetObjectArrayElement(env,result,0,numbers);
    jclass string=(*env)->FindClass(env,"java/lang/String");jstring message=(*env)->NewStringUTF(env,"fixture");
    jobjectArray messages=(*env)->NewObjectArray(env,1,string,message);(*env)->SetObjectArrayElement(env,result,1,messages);
    return result;
}
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env=0;
    if ((*vm)->GetEnv(vm,(void**)&env,JNI_VERSION_1_6)!=JNI_OK) return JNI_ERR;
    jclass klass=(*env)->FindClass(env,"RegisteredNativeSmoke");
    if (!klass) return JNI_ERR;
    JNINativeMethod methods[]={{"answer","()I",(void*)answer},{"values","()[Ljava/lang/Object;",(void*)values}};
    return (*env)->RegisterNatives(env,klass,methods,2)==JNI_OK?JNI_VERSION_1_6:JNI_ERR;
}
