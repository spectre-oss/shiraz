// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.res.Resources;
public final class SavedStateSmoke {
 public static void main(String[] args){
  final Resources resources=Resources.shirazOpen(args[0],96,160);
  Context context=new ContextWrapper(null){public Resources getResources(){return resources;}public Resources.Theme getTheme(){return resources.newTheme();}};
  try{org.shiraz.fixture.SavedStateFixture.check(context);}finally{resources.shirazClose();}
  System.out.println("SAVED_STATE_OK");
 }
}
