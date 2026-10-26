// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.res.Resources;
import android.view.ViewTreeObserver;
public final class SystemUiSmoke {
 public static void main(String[] args){final Resources resources=Resources.shirazOpen(args[0],96,160);Context context=new ContextWrapper(null){public Resources getResources(){return resources;}};
  try{final org.shiraz.fixture.SystemUiFixture fixture=new org.shiraz.fixture.SystemUiFixture(context);fixture.checkDetached();ViewTreeObserver window=ViewTreeObserver.shirazCreateWindowObserver();fixture.root.shirazAttachTree(window);try{fixture.checkAttached(new Runnable(){public void run(){fixture.root.shirazUpdateSystemUiVisibility();}});}finally{fixture.root.shirazDetachTree();window.shirazKill();}}
  finally{resources.shirazClose();}
  System.out.println("SYSTEM_UI_OK");
 }
}
