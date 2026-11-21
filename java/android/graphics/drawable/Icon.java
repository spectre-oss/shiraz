// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlendMode;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
/** Local icon values. Cross-package resources and content providers require separate host bridges. */
public final class Icon implements Parcelable {
 public interface OnDrawableLoadedListener {
  void onDrawableLoaded(Drawable d);
 }
 public static final int TYPE_BITMAP=1,TYPE_RESOURCE=2,TYPE_DATA=3,TYPE_URI=4,TYPE_ADAPTIVE_BITMAP=5,TYPE_URI_ADAPTIVE_BITMAP=6;
 private final int type;private Bitmap bitmap;private byte[] data;private String resourcePackage,uri;private int resourceId;
 private ColorStateList tint;private PorterDuff.Mode tintMode=PorterDuff.Mode.SRC_IN;
 private Icon(int type){this.type=type;}
 public static Icon createWithBitmap(Bitmap bitmap){if(bitmap==null)throw new IllegalArgumentException("bitmap");Icon icon=new Icon(TYPE_BITMAP);icon.bitmap=bitmap;return icon;}
 public static Icon createWithAdaptiveBitmap(Bitmap bitmap){if(bitmap==null)throw new IllegalArgumentException("bitmap");Icon icon=new Icon(TYPE_ADAPTIVE_BITMAP);icon.bitmap=bitmap;return icon;}
 public static Icon createWithResource(Context context,int id){if(context==null)throw new IllegalArgumentException("context");return createWithResource(context.getPackageName(),id);}
 public static Icon createWithResource(String packageName,int id){if(packageName==null)throw new IllegalArgumentException("resource package");Icon icon=new Icon(TYPE_RESOURCE);icon.resourcePackage=packageName;icon.resourceId=id;return icon;}
 public static Icon createWithResource(Resources resources,String packageName,int id){return createWithResource(packageName,id);}
 public static Icon createWithData(byte[] bytes,int offset,int length){if(bytes==null)throw new IllegalArgumentException("data");if(offset<0||length<0||offset>bytes.length-length||length>16*1024*1024)throw new IllegalArgumentException("icon data bounds");Icon icon=new Icon(TYPE_DATA);icon.data=java.util.Arrays.copyOfRange(bytes,offset,offset+length);return icon;}
 public static Icon createWithContentUri(String uri){if(uri==null||uri.isEmpty()||uri.length()>16384)throw new IllegalArgumentException("uri");Icon icon=new Icon(TYPE_URI);icon.uri=uri;return icon;}
 public static Icon createWithContentUri(Uri uri){if(uri==null)throw new IllegalArgumentException("uri");return createWithContentUri(uri.toString());}
 public static Icon createWithAdaptiveBitmapContentUri(String uri){if(uri==null||uri.isEmpty()||uri.length()>16384)throw new IllegalArgumentException("uri");Icon icon=new Icon(TYPE_URI_ADAPTIVE_BITMAP);icon.uri=uri;return icon;}
 public static Icon createWithAdaptiveBitmapContentUri(Uri uri){if(uri==null)throw new IllegalArgumentException("uri");return createWithAdaptiveBitmapContentUri(uri.toString());}
 public static Icon createWithFilePath(String path){if(path==null)throw new IllegalArgumentException("path");Icon icon=new Icon(TYPE_URI);icon.uri=path;return icon;}
 public int getType(){return type;}
 public int getResId(){require(TYPE_RESOURCE);return resourceId;}
 public String getResPackage(){require(TYPE_RESOURCE);return resourcePackage;}
 public Uri getUri(){if(type!=TYPE_URI&&type!=TYPE_URI_ADAPTIVE_BITMAP)throw new IllegalStateException("Wrong icon type: "+type);return Uri.parse(uri);}
 private void require(int expected){if(type!=expected)throw new IllegalStateException("Wrong icon type: "+type);}
 public Icon setTint(int color){return setTintList(ColorStateList.valueOf(color));}
 public Icon setTintList(ColorStateList value){tint=value;return this;}
 public Icon setTintMode(PorterDuff.Mode value){if(value==null)throw new IllegalArgumentException("tint mode");tintMode=value;return this;}
 public Icon setTintBlendMode(BlendMode value){if(value==null)throw new IllegalArgumentException("blend mode");tintMode=PorterDuff.Mode.valueOf(value==BlendMode.PLUS?"ADD":value==BlendMode.MODULATE?"MULTIPLY":value.name());return this;}
 public void loadDrawableAsync(final Context context,final Message andThen){
  if(andThen==null||andThen.getTarget()==null)throw new IllegalArgumentException("andThen must have a target handler");
  andThen.obj=loadDrawable(context);andThen.sendToTarget();
 }
 public void loadDrawableAsync(final Context context,final OnDrawableLoadedListener listener,Handler handler){
  if(listener==null)throw new IllegalArgumentException("listener");
  final Drawable d=loadDrawable(context);
  if(handler!=null){handler.post(new Runnable(){public void run(){listener.onDrawableLoaded(d);}});}
  else listener.onDrawableLoaded(d);
 }
 public Drawable loadDrawable(Context context){
  if(context==null)throw new NullPointerException("context");Drawable drawable;
  if(type==TYPE_RESOURCE){Resources resources;String pkg=resourcePackage==null||resourcePackage.isEmpty()?context.getPackageName():resourcePackage;if("android".equals(pkg))resources=Resources.getSystem();else if(pkg.equals(context.getPackageName()))resources=context.getResources();else throw new UnsupportedOperationException("Foreign package icon resources");try{drawable=resources.getDrawable(resourceId,context.getTheme());}catch(Resources.NotFoundException missing){return null;}}
  else{Bitmap image=bitmap;if(type==TYPE_DATA)image=BitmapFactory.decodeByteArray(data,0,data.length);
   if(type==TYPE_URI||type==TYPE_URI_ADAPTIVE_BITMAP){Uri value=Uri.parse(uri);String scheme=value.getScheme();if(scheme!=null&&!"file".equals(scheme))throw new UnsupportedOperationException("Icon URI provider: "+scheme);image=BitmapFactory.decodeFile(scheme==null?uri:value.getPath());}
   if(image==null)return null;drawable=new BitmapDrawable(context.getResources(),image);
  }
  if(tint!=null){drawable=drawable.mutate();drawable.setTintList(tint);drawable.setTintMode(tintMode);}return drawable;
 }
 @Override public String toString(){return "Icon(typ="+type+")";}
 public int describeContents(){return (type==TYPE_BITMAP||type==TYPE_ADAPTIVE_BITMAP)?Parcelable.CONTENTS_FILE_DESCRIPTOR:0;}
 public void writeToParcel(Parcel dest,int flags){
  dest.writeInt(type);
  switch(type){
   case TYPE_BITMAP:case TYPE_ADAPTIVE_BITMAP:bitmap.writeToParcel(dest,flags);break;
   case TYPE_RESOURCE:dest.writeString(resourcePackage);dest.writeInt(resourceId);break;
   case TYPE_DATA:dest.writeInt(data.length);dest.writeByteArray(data);break;
   case TYPE_URI:case TYPE_URI_ADAPTIVE_BITMAP:dest.writeString(uri);break;
  }
 }
 public static final Parcelable.Creator<Icon> CREATOR=new Parcelable.Creator<Icon>(){
  public Icon createFromParcel(Parcel in){
   int t=in.readInt();Icon ic=new Icon(t);
   switch(t){
    case TYPE_BITMAP:case TYPE_ADAPTIVE_BITMAP:ic.bitmap=Bitmap.CREATOR.createFromParcel(in);break;
    case TYPE_RESOURCE:ic.resourcePackage=in.readString();ic.resourceId=in.readInt();break;
    case TYPE_DATA:{int len=in.readInt();ic.data=new byte[len];in.readByteArray(ic.data);break;}
    case TYPE_URI:case TYPE_URI_ADAPTIVE_BITMAP:ic.uri=in.readString();break;
   }
   return ic;
  }
  public Icon[] newArray(int size){return new Icon[size];}
 };
}
