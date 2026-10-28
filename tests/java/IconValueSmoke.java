// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.drawable.Icon;
import android.content.ContextWrapper;
public class IconValueSmoke {
 public static void main(String[] args){
  ContextWrapper context=new ContextWrapper(null){public String getPackageName(){return "org.shiraz.fixture";}};
  Icon unresolved=Icon.createWithResource(context,0);
  if(unresolved.getType()!=Icon.TYPE_RESOURCE||unresolved.getResId()!=0||!unresolved.getResPackage().equals(context.getPackageName()))throw new AssertionError("deferred resource value");
  if(Icon.createWithResource("",0).getResId()!=0)throw new AssertionError("empty package with deferred id");
  try{Icon.createWithResource((String)null,1);throw new AssertionError("null package");}catch(IllegalArgumentException expected){}
  try{Icon.createWithResource((android.content.Context)null,1);throw new AssertionError("null context");}catch(IllegalArgumentException expected){}
  System.out.println("ICON_DEFERRED_RESOURCE_OK");
 }
}
