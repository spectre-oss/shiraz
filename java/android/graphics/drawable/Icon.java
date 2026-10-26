// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
/** Local icon values. Cross-package resources and content providers require separate host bridges. */
public final class Icon implements Parcelable {
 public static final int TYPE_BITMAP=1,TYPE_RESOURCE=2,TYPE_DATA=3,TYPE_URI=4,TYPE_ADAPTIVE_BITMAP=5;
 private final int type;private Bitmap bitmap;private byte[] data;private String resourcePackage,uri;private int resourceId;
 private ColorStateList tint;private PorterDuff.Mode tintMode=PorterDuff.Mode.SRC_IN;
 private Icon(int type){this.type=type;}
 public static Icon createWithBitmap(Bitmap bitmap){if(bitmap==null)throw new IllegalArgumentException("bitmap");Icon icon=new Icon(TYPE_BITMAP);icon.bitmap=bitmap;return icon;}
 public static Icon createWithAdaptiveBitmap(Bitmap bitmap){if(bitmap==null)throw new IllegalArgumentException("bitmap");Icon icon=new Icon(TYPE_ADAPTIVE_BITMAP);icon.bitmap=bitmap;return icon;}
 public static Icon createWithResource(Context context,int id){if(context==null)throw new IllegalArgumentException("context");return createWithResource(context.getPackageName(),id);}
 public static Icon createWithResource(String packageName,int id){if(id==0)throw new IllegalArgumentException("resource");Icon icon=new Icon(TYPE_RESOURCE);icon.resourcePackage=packageName;icon.resourceId=id;return icon;}
 public static Icon createWithResource(Resources resources,String packageName,int id){if(id==0)throw new IllegalArgumentException("resource");Icon icon=new Icon(TYPE_RESOURCE);icon.resourcePackage=packageName;icon.resourceId=id;return icon;}
 public static Icon createWithData(byte[] bytes,int offset,int length){if(bytes==null)throw new IllegalArgumentException("data");if(offset<0||length<0||offset>bytes.length-length||length>16*1024*1024)throw new IllegalArgumentException("icon data bounds");Icon icon=new Icon(TYPE_DATA);icon.data=java.util.Arrays.copyOfRange(bytes,offset,offset+length);return icon;}
 public static Icon createWithContentUri(String uri){if(uri==null||uri.isEmpty()||uri.length()>16384)throw new IllegalArgumentException("uri");Icon icon=new Icon(TYPE_URI);icon.uri=uri;return icon;}
 public static Icon createWithContentUri(Uri uri){if(uri==null)throw new IllegalArgumentException("uri");return createWithContentUri(uri.toString());}
 public int getType(){return type;}
 public int getResId(){require(TYPE_RESOURCE);return resourceId;}
 public String getResPackage(){require(TYPE_RESOURCE);return resourcePackage;}
 public Uri getUri(){require(TYPE_URI);return Uri.parse(uri);}
 private void require(int expected){if(type!=expected)throw new IllegalStateException("Wrong icon type: "+type);}
 public Icon setTint(int color){return setTintList(ColorStateList.valueOf(color));}
 public Icon setTintList(ColorStateList value){tint=value;return this;}
 public Icon setTintMode(PorterDuff.Mode value){if(value==null)throw new IllegalArgumentException("tint mode");tintMode=value;return this;}
 public Drawable loadDrawable(Context context){
  if(context==null)throw new NullPointerException("context");Drawable drawable;
  if(type==TYPE_RESOURCE){Resources resources;String pkg=resourcePackage==null||resourcePackage.isEmpty()?context.getPackageName():resourcePackage;if("android".equals(pkg))resources=Resources.getSystem();else if(pkg.equals(context.getPackageName()))resources=context.getResources();else throw new UnsupportedOperationException("Foreign package icon resources");try{drawable=resources.getDrawable(resourceId,context.getTheme());}catch(Resources.NotFoundException missing){return null;}}
  else{Bitmap image=bitmap;if(type==TYPE_DATA)image=BitmapFactory.decodeByteArray(data,0,data.length);
   if(type==TYPE_URI){Uri value=Uri.parse(uri);String scheme=value.getScheme();if(scheme!=null&&!"file".equals(scheme))throw new UnsupportedOperationException("Icon URI provider: "+scheme);image=BitmapFactory.decodeFile(scheme==null?uri:value.getPath());}
   if(image==null)return null;drawable=new BitmapDrawable(context.getResources(),image);
  }
  if(tint!=null){drawable=drawable.mutate();drawable.setTintList(tint);drawable.setTintMode(tintMode);}return drawable;
 }
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel destination,int flags){throw new UnsupportedOperationException("Icon parcel transport");}
}
