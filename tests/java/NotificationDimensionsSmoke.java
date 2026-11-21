// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Resources;
import android.util.TypedValue;
public final class NotificationDimensionsSmoke {
 public static void main(String[] args){
  Resources resources=Resources.shirazOpen(new java.io.File("build/apk-fixture/fixture.apk").getAbsolutePath(),360,640);
  try{
   String[] names={"notification_large_icon_width","notification_large_icon_height"};
   for(int i=0;i<names.length;i++){
    int id=0x01050005+i;
    if(resources.getIdentifier(names[i],"dimen","android")!=id)throw new AssertionError("Public dimension ABI");
    TypedValue value=new TypedValue();resources.getValue(id,value,true);
    if(value.type!=TypedValue.TYPE_DIMENSION)throw new AssertionError("Dimension type");
    if(Math.abs(resources.getDimension(id)-64*resources.getDisplayMetrics().density)>.01f)throw new AssertionError("Notification dimension scaling");
   }
  }finally{resources.shirazClose();}
  System.out.println("NOTIFICATION_DIMENSIONS_OK");
 }
}
