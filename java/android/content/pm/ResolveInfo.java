// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
public class ResolveInfo {
 public ProviderInfo providerInfo;public ActivityInfo activityInfo;public ServiceInfo serviceInfo;public android.content.IntentFilter filter;public int priority,preferredOrder,match,specificIndex=-1,labelRes,icon;public boolean isDefault;public CharSequence nonLocalizedLabel;
}
