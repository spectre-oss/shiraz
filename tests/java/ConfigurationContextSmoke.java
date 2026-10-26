// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.res.*;
public final class ConfigurationContextSmoke {
 public static void main(String[] args) {
  final Resources resources=Resources.shirazOpen(args[0],96,160);
  Context context=new ContextWrapper(null){
   public Resources getResources(){return resources;}
   public Context createConfigurationContext(Configuration c){return new shiraz.runtime.ConfigurationContext(this,c);}
   public Context getApplicationContext(){return this;}
   public java.io.File getFilesDir(){return new java.io.File("build/configuration-context-smoke");}
   public Object getSystemService(String name){if(LAYOUT_INFLATER_SERVICE.equals(name))return new shiraz.runtime.HostLayoutInflater(this);throw new UnsupportedOperationException(name);}
   public Resources.Theme getTheme(){return resources.newTheme();}
  };
  Resources child=null;
  try {
   org.shiraz.fixture.ConfigurationContextFixture.check(context);
   Configuration empty=new Configuration();empty.fontScale=0;
   child=context.createConfigurationContext(empty).getResources();
   Resources nested=child;
   for(int i=1;i<32;i++)nested=nested.shirazWithConfiguration(empty);
   try{nested.shirazWithConfiguration(empty);throw new AssertionError("depth limit ignored");}catch(IllegalStateException expected){}
   // A borrower closing a child must not invalidate the root.
   child.shirazClose();resources.getConfiguration();
   try{nested.getConfiguration();throw new AssertionError("descendant survived owner close");}catch(IllegalStateException expected){}
   child=resources.shirazWithConfiguration(empty);
   for(int i=0;i<140;i++){Resources temporary=resources.shirazWithConfiguration(empty);temporary.shirazClose();}
   java.util.ArrayList<Resources> held=new java.util.ArrayList<Resources>();
   try{for(int i=0;i<129;i++)held.add(resources.shirazWithConfiguration(empty));throw new AssertionError("family limit ignored");}catch(IllegalStateException expected){}
   for(Resources heldResource:held)heldResource.shirazClose();
   resources.shirazWithConfiguration(empty).shirazClose();
   final Resources live=child;final Throwable[] errors=new Throwable[1];
   Thread reader=new Thread(new Runnable(){public void run(){try{for(int i=0;i<100;i++)live.getConfiguration();}catch(Throwable error){errors[0]=error;}}});reader.start();
   for(int i=0;i<100;i++){Configuration delta=new Configuration();delta.uiMode=(i%2==0?16:32);resources.updateConfiguration(delta,null);}
   try{reader.join(5000);}catch(InterruptedException error){throw new AssertionError(error);}
   if(reader.isAlive()||errors[0]!=null)throw new AssertionError("concurrent inherited reads",errors[0]);
  } finally {resources.shirazClose();}
  try{child.getConfiguration();throw new AssertionError("child survived root close");}catch(IllegalStateException expected){}
  System.out.println("CONFIGURATION_CONTEXT_OWNERSHIP_OK");
 }
}
