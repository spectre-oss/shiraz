// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
import android.view.*;
import android.media.*;
import android.media.browse.MediaBrowser;
import android.os.Parcel;
public final class WindowMetadataSmoke {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        System.setProperty("shiraz.frameworkResources",new java.io.File("build/framework-res/framework-res.apk").getAbsolutePath());
        final android.content.res.Resources resources=android.content.res.Resources.getSystem();
        final android.content.res.Resources.Theme theme=resources.newTheme();
        final shiraz.runtime.HostWindowManager manager=new shiraz.runtime.HostWindowManager(resources);
        android.content.ContextWrapper context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return resources;}public android.content.res.Resources.Theme getTheme(){return theme;}public Object getSystemService(String name){return WINDOW_SERVICE.equals(name)?manager:null;}};
        Window window=new Window(context);check(window.peekDecorView()==null&&window.getWindowManager()==manager,"window identity");
        window.setFormat(PixelFormat.TRANSLUCENT);window.setType(2);check(window.getAttributes().format==PixelFormat.TRANSLUCENT&&window.getAttributes().type==2,"window attribute updates");
        window.setFormat(PixelFormat.UNKNOWN);check(window.getAttributes().format==PixelFormat.OPAQUE,"default format reset");
        WindowManager.LayoutParams layout=new WindowManager.LayoutParams(320,480,7,8,2,32,1);check(layout.width==320&&layout.height==480&&layout.x==7&&layout.y==8&&layout.type==2&&layout.flags==32&&layout.format==1,"complete layout constructor");
        layout.preferredRefreshRate=120f;WindowManager.LayoutParams copied=new WindowManager.LayoutParams(layout);check(copied.preferredRefreshRate==120f&&copied.copyFrom(layout)==0,"refresh preference copied");layout.preferredRefreshRate=60f;check(copied.copyFrom(layout)!=0&&copied.preferredRefreshRate==60f,"refresh preference change");
        new android.widget.EdgeEffect(context,null).finish();
        window.setNavigationBarContrastEnforced(false);window.setStatusBarContrastEnforced(false);window.setNavigationBarDividerColor(Color.RED);
        check(!window.isNavigationBarContrastEnforced()&&!window.isStatusBarContrastEnforced()&&window.getNavigationBarDividerColor()==Color.RED,"retained bar metadata");
        check(!android.provider.Settings.canDrawOverlays(context),"host overlays not advertised");
        RingtoneManager sounds=new RingtoneManager(context);sounds.setType(RingtoneManager.TYPE_NOTIFICATION);check(sounds.getCursor().getCount()==0,"absent ringtone catalog");sounds.getCursor().close();
        MediaDescription description=new MediaDescription.Builder().setMediaId("fixture-item").setTitle("Metadata").build();
        MediaBrowser.MediaItem item=new MediaBrowser.MediaItem(description,MediaBrowser.MediaItem.FLAG_PLAYABLE);Parcel parcel=Parcel.obtain();item.writeToParcel(parcel,0);parcel.setDataPosition(0);MediaBrowser.MediaItem restored=MediaBrowser.MediaItem.CREATOR.createFromParcel(parcel);check(restored.isPlayable()&&!restored.isBrowsable()&&"fixture-item".equals(restored.getMediaId()),"media item parcel");parcel.recycle();
        Bitmap bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);Paint paint=new Paint();paint.setShader(new RadialGradient(4,4,4,new long[]{Color.valueOf(Color.RED).pack(),Color.valueOf(Color.BLUE).pack()},null,Shader.TileMode.CLAMP));new Canvas(bitmap).drawRect(0,0,8,8,paint);check(bitmap.getPixel(0,0)==Color.BLUE&&Color.red(bitmap.getPixel(4,4))>Color.blue(bitmap.getPixel(4,4)),"real sRGB color-long gradient raster");bitmap.recycle();
        System.out.println("WINDOW_METADATA_OK");
    }
}
