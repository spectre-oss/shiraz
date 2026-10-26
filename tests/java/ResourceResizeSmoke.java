// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.*;
import android.util.DisplayMetrics;
import android.view.Display;
public final class ResourceResizeSmoke {
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args) {
  Resources root=Resources.shirazOpen(args[0],96,160);
  try {
   check(new Configuration(root.getConfiguration()).updateFrom(root.shirazPreviewDisplay(96,160))==0,"initial configuration matches unchanged display");
   check((new Configuration(root.getConfiguration()).updateFrom(root.shirazPreviewDisplay(100,164))&0x100)==0,"initial small resize preserves screen layout");
   java.util.TreeSet<Integer> widths=new java.util.TreeSet<Integer>(),heights=new java.util.TreeSet<Integer>(),smallest=new java.util.TreeSet<Integer>();
   Configuration[] sizes=root.getSizeConfigurations();for(Configuration size:sizes){if(size.screenWidthDp>0)widths.add(size.screenWidthDp);if(size.screenHeightDp>0)heights.add(size.screenHeightDp);if(size.smallestScreenWidthDp>0)smallest.add(size.smallestScreenWidthDp);}
   check(widths.toString().equals("[120, 140]")&&heights.toString().equals("[200]")&&smallest.toString().equals("[110]"),"APK qualifier enumeration includes mipmap and excludes framework: "+widths+heights+smallest);
   sizes[0].screenWidthDp=999;check(root.getSizeConfigurations()[0].screenWidthDp!=999,"Configuration result ownership");
   int orientation=root.getIdentifier("orientation_probe","string","org.shiraz.fixture");
   android.util.TypedValue cached=new android.util.TypedValue();root.getValue(orientation,cached,true);cached.string="mutated caller value";root.getValue(orientation,cached,true);check("portrait-default".contentEquals(cached.string),"Cached values are owned independently of callers");
   Display display=Display.shirazForResources(root);DisplayMetrics physical=new DisplayMetrics();
   Configuration fixed=new Configuration();fixed.orientation=1;fixed.screenWidthDp=96;fixed.screenHeightDp=160;
   Resources pinned=root.shirazWithConfiguration(fixed),inherited=root.shirazWithConfiguration(new Configuration());
   Display childDisplay=Display.shirazForResources(pinned);
   for(int i=0;i<4;i++) {
    int width=i%2==0?160:96,height=i%2==0?96:160;
    Configuration preview=root.shirazPreviewDisplay(width,height);
    check(pinned.shirazPreviewInheritedConfiguration(preview).orientation==1,"scoped preview retains override");
    root.shirazResizeDisplay(width,height);
    check(root.getDisplayMetrics().widthPixels==width&&root.getConfiguration().screenWidthDp==width,"root size/dp");
    check(inherited.getDisplayMetrics().heightPixels==height&&inherited.getConfiguration().orientation==(width>height?2:1),"inherited configuration and pixels");
    root.getValue(orientation,cached,true);check((width>height?"landscape-selected":"portrait-default").contentEquals(cached.string),"Typed value cache follows configuration");
    check(root.getString(orientation).equals(width>height?"landscape-selected":"portrait-default"),"native qualifier changes");
    check(pinned.getConfiguration().orientation==1&&pinned.getConfiguration().screenWidthDp==96&&pinned.getString(orientation).equals("portrait-default"),"explicit overrides retained");
    check(pinned.getDisplayMetrics().widthPixels==width,"overrides do not freeze physical pixels");
    display.getRealMetrics(physical);check(physical.widthPixels==width&&physical.heightPixels==height,"existing Display refreshed");
    childDisplay.getRealMetrics(physical);check(physical.widthPixels==width,"scoped Display refreshed");
   }
   Configuration density=new Configuration();density.densityDpi=320;root.updateConfiguration(density,null);root.shirazResizeDisplay(320,160);
   check(root.getConfiguration().screenWidthDp==160&&root.getConfiguration().screenHeightDp==80&&root.getDisplayMetrics().density==2,"density-aware dp");
   display.getRealMetrics(physical);check(physical.widthPixels==320&&physical.density==1,"physical density separate from resource override");
   root.shirazResizeDisplay(320,160,320);
   display.getRealMetrics(physical);check(physical.densityDpi==320,"host density updates even when resource override already matches");
   root.shirazResizeDisplay(192,320,320);
   check(root.getConfiguration().screenWidthDp==96&&root.getConfiguration().screenHeightDp==160,"host 2x preserves logical size");
   display.getRealMetrics(physical);check(physical.densityDpi==320&&physical.widthPixels==192,"host physical density updated");
   check(inherited.getDisplayMetrics().densityDpi==320,"child inherits host density");
   root.shirazResizeDisplay(120,200,200);
   check(root.getConfiguration().screenWidthDp==96&&root.getConfiguration().screenHeightDp==160,"fractional scale preserves logical size");
   check(inherited.getDisplayMetrics().density==1.25f,"fractional child metrics");
   root.shirazResizeDisplay(120,200,160);
   check(root.getConfiguration().screenWidthDp==120,"density-only change updates dp");
   Resources.shirazSetSystemDisplay(1080,2152,320);
   check(Resources.getSystem().getDisplayMetrics().densityDpi==320&&Resources.getSystem().getConfiguration().screenWidthDp==540,"system display density");
   android.graphics.Bitmap target=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888);
   android.graphics.Bitmap source=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888);
   try {
    check(target.getDensity()==320&&source.getDensity()==320,"new bitmaps inherit host density");
    source.setDensity(160);source.eraseColor(0xffff0000);
    new android.graphics.Canvas(target).drawBitmap(source,0,0,null);
    check(target.getPixel(3,3)==0xffff0000&&target.getPixel(4,4)==0,"positioned mdpi bitmap scales on high-DPI Canvas");
    Resources.shirazSetSystemDisplay(720,1280,160);
    check(target.getDensity()==320,"existing bitmap retains its density across monitor changes");
   } finally {target.recycle();source.recycle();Resources.shirazSetSystemDisplay(720,1280,160);}

   Configuration prior=root.getConfiguration();DisplayMetrics pixels=root.getDisplayMetrics();
   for(int[] bad:new int[][]{{0,100},{8193,2},{8192,8192}})try{root.shirazResizeDisplay(bad[0],bad[1]);throw new AssertionError("invalid resize accepted");}catch(IllegalArgumentException expected){}
   check(root.getConfiguration().equals(prior)&&root.getDisplayMetrics().widthPixels==pixels.widthPixels,"rejected resize rollback");
   try{pinned.shirazResizeDisplay(100,100);throw new AssertionError("child resized display");}catch(IllegalStateException expected){}
  } finally {root.shirazClose();}
  try{root.getSizeConfigurations();throw new AssertionError("closed configuration enumeration");}catch(IllegalStateException expected){}
  try{root.shirazResizeDisplay(10,10);throw new AssertionError("closed resources resized");}catch(IllegalStateException expected){}
  System.out.println("RESOURCE_RESIZE_OK");
 }
}
