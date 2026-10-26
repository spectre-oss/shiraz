// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;
import android.content.ComponentName;
public final class PhoneAccountHandle {
 private final ComponentName component;private final String id;
 public PhoneAccountHandle(ComponentName component,String id){this.component=component;this.id=id;}
 public PhoneAccountHandle(ComponentName component,String id,android.os.UserHandle user){this(component,id);}
 public ComponentName getComponentName(){return component;}
 public String getId(){return id;}
 @Override public boolean equals(Object other){if(!(other instanceof PhoneAccountHandle))return false;PhoneAccountHandle h=(PhoneAccountHandle)other;return java.util.Objects.equals(component,h.component)&&java.util.Objects.equals(id,h.id);}
 @Override public int hashCode(){return java.util.Objects.hash(component,id);}
}
