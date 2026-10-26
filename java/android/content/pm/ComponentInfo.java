// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
/** Loaded component identity and enabled-state metadata. */
public class ComponentInfo extends PackageItemInfo {
 public ApplicationInfo applicationInfo;
 public String processName;
 public boolean enabled=true,exported,directBootAware;
 public boolean isEnabled(){return enabled&&applicationInfo!=null&&applicationInfo.enabled;}
 @Override protected ApplicationInfo getApplicationInfo(){return applicationInfo;}
}
