// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
public final class HostLayoutInflater extends android.view.LayoutInflater {
 public HostLayoutInflater(android.content.Context context){super(context);}
 private HostLayoutInflater(HostLayoutInflater original,android.content.Context context){super(original,context);}
 public android.view.LayoutInflater cloneInContext(android.content.Context context){return new HostLayoutInflater(this,context);}
}
