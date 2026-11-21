// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Resources;
import android.util.DisplayMetrics;

public final class StableDensitySmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  // Reflection verifies the runtime field, not an inlined compiler constant.
  java.lang.reflect.Field field=DisplayMetrics.class.getField("DENSITY_DEVICE_STABLE");
  int stable=field.getInt(null);
  check(stable==160&&java.lang.reflect.Modifier.isFinal(field.getModifiers()),"virtual device base density");
  Resources.shirazSetSystemDisplay(360,640,240);
  Resources resources=Resources.getSystem();
  android.hardware.display.DisplayManager manager=new android.hardware.display.DisplayManager(resources);
  android.graphics.Point size=manager.getStableDisplaySize();
  check(size.x==720&&size.y==1280,"virtual device stable size");
  size.set(1,1);
  check(resources.getDisplayMetrics().densityDpi==240,"current density is independent");
  Resources.shirazSetSystemDisplay(1280,720,320);
  check(resources.getDisplayMetrics().densityDpi==320,"current density follows display change");
  check(field.getInt(null)==stable,"stable density survives resize and scale change");
  size=manager.getStableDisplaySize();
  check(size.x==720&&size.y==1280,"stable size survives resize and caller mutation");
  check(manager.getDisplay(0).getWidth()==1280,"current display remains live");
  System.out.println("STABLE_DENSITY_OK");
 }
}
