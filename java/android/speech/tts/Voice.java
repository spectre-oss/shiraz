// SPDX-License-Identifier: GPL-3.0-only
package android.speech.tts;
import java.util.*;
import android.os.*;
public final class Voice implements Parcelable {
 public static final int QUALITY_VERY_LOW=100,QUALITY_LOW=200,QUALITY_NORMAL=300,QUALITY_HIGH=400,QUALITY_VERY_HIGH=500;
 public static final int LATENCY_VERY_LOW=100,LATENCY_LOW=200,LATENCY_NORMAL=300,LATENCY_HIGH=400,LATENCY_VERY_HIGH=500;
 private final String name;private final Locale locale;private final int quality,latency;private final boolean network;private final Set<String> features;
 public Voice(String name,Locale locale,int quality,int latency,boolean network,Set<String> features){this.name=Objects.requireNonNull(name);this.locale=Objects.requireNonNull(locale);this.quality=quality;this.latency=latency;this.network=network;this.features=Collections.unmodifiableSet(new HashSet<String>(Objects.requireNonNull(features)));}
 public String getName(){return name;}public Locale getLocale(){return locale;}public int getQuality(){return quality;}public int getLatency(){return latency;}public boolean isNetworkConnectionRequired(){return network;}public Set<String> getFeatures(){return features;}
 public boolean equals(Object other){if(!(other instanceof Voice))return false;Voice v=(Voice)other;return name.equals(v.name)&&locale.equals(v.locale)&&quality==v.quality&&latency==v.latency&&network==v.network&&features.equals(v.features);}
 public int hashCode(){return Objects.hash(name,locale,quality,latency,network,features);}
 public String toString(){return "Voice{name="+name+", locale="+locale+", quality="+quality+", latency="+latency+", network="+network+", features="+features+"}";}
 public int describeContents(){return 0;}public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("Voice parcel transport is unavailable");}
 public static final Parcelable.Creator<Voice> CREATOR=new Parcelable.Creator<Voice>(){public Voice createFromParcel(Parcel source){throw new UnsupportedOperationException("Voice parcel transport is unavailable");}public Voice[] newArray(int size){return new Voice[size];}};
}
