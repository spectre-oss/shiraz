// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Resources;
import android.util.TypedValue;
public final class TranslucentThemeSmoke {
 public static void main(String[] args){
  Resources resources=Resources.shirazOpen(new java.io.File("build/apk-fixture/fixture.apk").getAbsolutePath(),360,640);
  try{
   int style=resources.getIdentifier("Theme.Translucent.NoTitleBar","style","android");
   if(style!=0x01030010)throw new AssertionError("Framework theme ABI");
   Resources.Theme theme=resources.newTheme();theme.applyStyle(style,true);
   for(int attr:new int[]{0x01010056,0x01010058}){
    TypedValue value=new TypedValue();
    if(!theme.resolveAttribute(attr,value,true)||value.type!=TypedValue.TYPE_INT_BOOLEAN||value.data==0)throw new AssertionError("Inherited window attribute missing");
   }
   TypedValue background=new TypedValue();
   if(!theme.resolveAttribute(0x01010054,background,true)||background.data!=0)throw new AssertionError("Transparent background missing");
  }finally{resources.shirazClose();}
  System.out.println("TRANSLUCENT_THEME_OK");
 }
}
