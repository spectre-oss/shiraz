// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.LayoutInflater;
/** Configuration-scoped resources with the owning application's services and storage. */
public final class ConfigurationContext extends ContextWrapper {
 private final Resources resources;
 private Resources.Theme theme;
 private LayoutInflater inflater;
 private HostWindowManager windows;
 public ConfigurationContext(Context base,Configuration override) {
  super(base);if(base==null)throw new NullPointerException("base");
  resources=base.getResources().shirazWithConfiguration(override);
 }
 @Override public Context createConfigurationContext(Configuration configuration){return new ConfigurationContext(this,configuration);}
 @Override public Resources getResources(){return resources;}
 @Override public synchronized Resources.Theme getTheme(){if(theme==null){Resources.Theme initial=resources.newTheme();initial.setTo(getBaseContext().getTheme());theme=initial;}return theme;}
 @Override public synchronized void setTheme(int id){getTheme().applyStyle(id,true);}
 @Override public ClassLoader getClassLoader(){return getBaseContext().getClassLoader();}
 @Override public synchronized Object getSystemService(String name){
  if(LAYOUT_INFLATER_SERVICE.equals(name)){if(inflater==null)inflater=LayoutInflater.from(getBaseContext()).cloneInContext(this);return inflater;}
  if(WINDOW_SERVICE.equals(name)){if(windows==null)windows=new HostWindowManager(resources,(android.view.WindowManager)getBaseContext().getSystemService(WINDOW_SERVICE));return windows;}
  return super.getSystemService(name);
 }
}
