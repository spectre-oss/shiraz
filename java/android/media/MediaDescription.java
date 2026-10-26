// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import android.os.*;
import android.net.Uri;
import android.graphics.Bitmap;

/** Immutable description of an item in an in-process media queue. */
public final class MediaDescription implements Parcelable {
 private final Builder values;
 private MediaDescription(Builder b){values=new Builder();
  values.mediaId=b.mediaId;
  values.title=b.title==null?null:b.title.toString();
  values.subtitle=b.subtitle==null?null:b.subtitle.toString();
  values.description=b.description==null?null:b.description.toString();
  values.iconBitmap=b.iconBitmap;
  values.iconUri=b.iconUri;
  values.extras=b.extras==null?null:new Bundle(b.extras);
  values.mediaUri=b.mediaUri;
 }
 public String getMediaId(){return values.mediaId;}
 public CharSequence getTitle(){return values.title;}
 public CharSequence getSubtitle(){return values.subtitle;}
 public CharSequence getDescription(){return values.description;}
 public Bitmap getIconBitmap(){return values.iconBitmap;}
 public Uri getIconUri(){return values.iconUri;}
 public Bundle getExtras(){return values.extras==null?null:new Bundle(values.extras);}
 public Uri getMediaUri(){return values.mediaUri;}
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel p,int flags){
  if(values.iconBitmap!=null)throw new UnsupportedOperationException("MediaDescription bitmap parceling");
  p.writeString(values.mediaId);
  p.writeString(values.title==null?null:values.title.toString());
  p.writeString(values.subtitle==null?null:values.subtitle.toString());
  p.writeString(values.description==null?null:values.description.toString());
  p.writeParcelable(null,flags);
  p.writeParcelable(values.iconUri,flags);
  p.writeParcelable(values.extras,flags);
  p.writeParcelable(values.mediaUri,flags);
 }
 public static final Parcelable.Creator<MediaDescription> CREATOR=new Parcelable.Creator<MediaDescription>(){
  public MediaDescription createFromParcel(Parcel p){Builder b=new Builder();
   b.mediaId=p.readString();
   b.title=p.readString();
   b.subtitle=p.readString();
   b.description=p.readString();
   if(p.readParcelable(MediaDescription.class.getClassLoader())!=null)throw new UnsupportedOperationException("MediaDescription bitmap parceling");
   b.iconUri=p.readParcelable(MediaDescription.class.getClassLoader());
   b.extras=p.readParcelable(MediaDescription.class.getClassLoader());
   b.mediaUri=p.readParcelable(MediaDescription.class.getClassLoader());
   return b.build();}
  public MediaDescription[] newArray(int size){return new MediaDescription[size];}
 };
 public static final class Builder {
  private String mediaId;
  public Builder setMediaId(String value){mediaId=value;return this;}
  private CharSequence title;
  public Builder setTitle(CharSequence value){title=value;return this;}
  private CharSequence subtitle;
  public Builder setSubtitle(CharSequence value){subtitle=value;return this;}
  private CharSequence description;
  public Builder setDescription(CharSequence value){description=value;return this;}
  private Bitmap iconBitmap;
  public Builder setIconBitmap(Bitmap value){iconBitmap=value;return this;}
  private Uri iconUri;
  public Builder setIconUri(Uri value){iconUri=value;return this;}
  private Bundle extras;
  public Builder setExtras(Bundle value){extras=value;return this;}
  private Uri mediaUri;
  public Builder setMediaUri(Uri value){mediaUri=value;return this;}
  public MediaDescription build(){return new MediaDescription(this);}
 }
}
