// SPDX-License-Identifier: GPL-3.0-only
public final class ViewConfigurationSmoke {
 private static android.content.Context context(final android.content.res.Resources resources){return new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return resources;}public android.content.Context createConfigurationContext(android.content.res.Configuration config){return context(resources.shirazWithConfiguration(config));}};}
 public static void main(String[] args){
  final android.content.res.Resources resources=android.content.res.Resources.shirazOpen(args[0],720,1280);
  android.content.Context context=context(resources);
  try{
   org.shiraz.fixture.ViewConfigurationFixture.check(context);
   android.view.ViewConfiguration first=android.view.ViewConfiguration.get(context);
   resources.shirazResizeDisplay(1280,720);
   if(android.view.ViewConfiguration.get(context).getScaledMaximumDrawingCacheSize()!=720*1280*4)throw new AssertionError("Rotation cache size");
   resources.shirazResizeDisplay(800,600);
   if(android.view.ViewConfiguration.get(context).getScaledMaximumDrawingCacheSize()!=800*600*4||first.getScaledMaximumDrawingCacheSize()!=720*1280*4)throw new AssertionError("Resize snapshot");
   try{android.view.ViewConfiguration.get(null);throw new AssertionError("Null context accepted");}catch(NullPointerException expected){}
  }finally{resources.shirazClose();}
 }
}
