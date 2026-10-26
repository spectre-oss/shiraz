// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import android.os.*;
public final class Rating implements Parcelable {
 public static final int RATING_NONE=0,RATING_HEART=1,RATING_THUMB_UP_DOWN=2,RATING_3_STARS=3,RATING_4_STARS=4,RATING_5_STARS=5,RATING_PERCENTAGE=6;
 private final int style;private final float value;
 private Rating(int style,float value){this.style=style;this.value=value;}
 public static Rating newUnratedRating(int style){return style<1||style>6?null:new Rating(style,-1);}
 public static Rating newHeartRating(boolean value){return new Rating(1,value?1:0);}
 public static Rating newThumbRating(boolean value){return new Rating(2,value?1:0);}
 public static Rating newStarRating(int style,float value){return style<3||style>5||!Float.isFinite(value)||value<0||value>style?null:new Rating(style,value);}
 public static Rating newPercentageRating(float value){return !Float.isFinite(value)||value<0||value>100?null:new Rating(6,value);}
 public int getRatingStyle(){return style;}public boolean isRated(){return value>=0;}
 public boolean hasHeart(){return style==1&&value==1;}public boolean isThumbUp(){return style==2&&value==1;}
 public float getStarRating(){return style>=3&&style<=5?value:-1;}public float getPercentRating(){return style==6?value:-1;}
 public int describeContents(){return style;}
 public void writeToParcel(Parcel p,int flags){p.writeInt(style);p.writeFloat(value);}
 public static final Parcelable.Creator<Rating> CREATOR=new Parcelable.Creator<Rating>(){
  public Rating createFromParcel(Parcel p){return new Rating(p.readInt(),p.readFloat());}public Rating[] newArray(int n){return new Rating[n];}
 };
}
