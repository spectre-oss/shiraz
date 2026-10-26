// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;
public abstract class EGLObjectHandle {
 private final long handle;
 protected EGLObjectHandle(long handle){this.handle=handle;}
 public long getNativeHandle(){return handle;}
 @Override public int hashCode(){return (int)(handle^(handle>>>32));}
 @Override public boolean equals(Object other){return other!=null&&other.getClass()==getClass()&&((EGLObjectHandle)other).handle==handle;}
}
