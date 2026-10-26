// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;import android.content.res.*;import android.graphics.*;import android.graphics.drawable.*;
public final class IconSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  Context context=new ContextWrapper(null){public Resources getResources(){return Resources.getSystem();}public String getPackageName(){return "org.shiraz.fixture";}};
  Bitmap source=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);source.eraseColor(0xffff0000);Icon icon=Icon.createWithBitmap(source);check(icon.getType()==Icon.TYPE_BITMAP,"bitmap type");
  Drawable original=icon.loadDrawable(context);icon.setTint(0xff00ff00);Drawable tinted=icon.loadDrawable(context);
  Bitmap output=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);original.setBounds(0,0,2,2);original.draw(new Canvas(output));check(output.getPixel(0,0)==0xffff0000,"original drawable independent tint");
  tinted.setBounds(0,0,2,2);tinted.draw(new Canvas(output));check(output.getPixel(0,0)==0xff00ff00,"icon tint pixels");
  icon.setTintList(null);check(icon.loadDrawable(context)!=original,"fresh drawable per load");
  Icon resource=Icon.createWithResource(context,17);check(resource.getResId()==17&&resource.getResPackage().equals(context.getPackageName()),"resource identity");
  check(Icon.createWithContentUri("file:///tmp/icon.png").getUri().getPath().equals("/tmp/icon.png"),"URI identity");
  try{icon.getResId();throw new AssertionError("wrong type");}catch(IllegalStateException expected){}
  try{Icon.createWithData(new byte[2],1,Integer.MAX_VALUE);throw new AssertionError("overflow");}catch(IllegalArgumentException expected){}
  try{Icon.createWithContentUri("content://icons/1").loadDrawable(context);throw new AssertionError("unsupported provider claimed");}catch(UnsupportedOperationException expected){}
  System.out.println("ICON_VALUES_OK");
 }
}
