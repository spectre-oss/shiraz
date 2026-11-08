// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;
import android.os.Parcel;
import android.os.Parcelable;
/** Immutable Android IME subtype metadata, independent of host IME availability. */
public final class InputMethodSubtype implements Parcelable {
    private final int nameId,iconId,id;private final String locale,languageTag,mode,extra;private final boolean auxiliary,overrides,ascii;
    private InputMethodSubtype(InputMethodSubtypeBuilder b){nameId=b.nameId;iconId=b.iconId;id=b.id;locale=b.locale;languageTag=b.languageTag;mode=b.mode;extra=b.extra;auxiliary=b.auxiliary;overrides=b.overrides;ascii=b.ascii;}
    @Deprecated public InputMethodSubtype(int name,int icon,String locale,String mode,String extra,boolean auxiliary,boolean overrides){this(new InputMethodSubtypeBuilder().setSubtypeNameResId(name).setSubtypeIconResId(icon).setSubtypeLocale(locale).setSubtypeMode(mode).setSubtypeExtraValue(extra).setIsAuxiliary(auxiliary).setOverridesImplicitlyEnabledSubtype(overrides));}
    public String getLocale(){return locale;}public String getLanguageTag(){return languageTag;}public String getMode(){return mode;}public String getExtraValue(){return extra;}
    public int getNameResId(){return nameId;}public int getIconResId(){return iconId;}public int getSubtypeId(){return id;}
    public boolean isAuxiliary(){return auxiliary;}public boolean overridesImplicitlyEnabledSubtype(){return overrides;}public boolean isAsciiCapable(){return ascii;}
    public java.util.Locale getLocaleObject(){if(!languageTag.isEmpty())return java.util.Locale.forLanguageTag(languageTag);if(locale.isEmpty())return null;return java.util.Locale.forLanguageTag(locale.replace('_','-'));}
    public boolean containsExtraValueKey(String key){for(String item:extra.split(","))if(item.split("=",2)[0].equals(key))return true;return false;}
    public String getExtraValueOf(String key){for(String item:extra.split(",")){String[] pair=item.split("=",2);if(pair[0].equals(key))return pair.length>1?pair[1]:null;}return null;}
    @Override public boolean equals(Object value){if(!(value instanceof InputMethodSubtype))return false;InputMethodSubtype s=(InputMethodSubtype)value;if(id!=0||s.id!=0)return id==s.id;return locale.equals(s.locale)&&languageTag.equals(s.languageTag)&&mode.equals(s.mode)&&extra.equals(s.extra)&&auxiliary==s.auxiliary&&overrides==s.overrides&&ascii==s.ascii;}
    @Override public int hashCode(){return id!=0?id:java.util.Objects.hash(locale,languageTag,mode,extra,auxiliary,overrides,ascii);}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel output,int flags){output.writeInt(nameId);output.writeInt(iconId);output.writeString(locale);output.writeString(languageTag);output.writeString(mode);output.writeString(extra);output.writeInt(auxiliary?1:0);output.writeInt(overrides?1:0);output.writeInt(ascii?1:0);output.writeInt(id);}
    public static final Parcelable.Creator<InputMethodSubtype> CREATOR=new Parcelable.Creator<InputMethodSubtype>(){public InputMethodSubtype createFromParcel(Parcel p){return new InputMethodSubtypeBuilder().setSubtypeNameResId(p.readInt()).setSubtypeIconResId(p.readInt()).setSubtypeLocale(p.readString()).setLanguageTag(p.readString()).setSubtypeMode(p.readString()).setSubtypeExtraValue(p.readString()).setIsAuxiliary(p.readInt()!=0).setOverridesImplicitlyEnabledSubtype(p.readInt()!=0).setIsAsciiCapable(p.readInt()!=0).setSubtypeId(p.readInt()).build();}public InputMethodSubtype[] newArray(int size){return new InputMethodSubtype[size];}};
    public static final class InputMethodSubtypeBuilder {
        private int nameId,iconId,id;private String locale="",languageTag="",mode="",extra="";private boolean auxiliary,overrides,ascii;
        private static String text(String value){if(value==null)return "";if(value.length()>4096)throw new IllegalArgumentException("IME metadata limit");return value;}
        public InputMethodSubtypeBuilder(){}
        public InputMethodSubtypeBuilder setSubtypeNameResId(int value){nameId=value;return this;}public InputMethodSubtypeBuilder setSubtypeIconResId(int value){iconId=value;return this;}public InputMethodSubtypeBuilder setSubtypeId(int value){id=value;return this;}
        public InputMethodSubtypeBuilder setSubtypeLocale(String value){locale=text(value);return this;}public InputMethodSubtypeBuilder setLanguageTag(String value){languageTag=text(value);return this;}public InputMethodSubtypeBuilder setSubtypeMode(String value){mode=text(value);return this;}public InputMethodSubtypeBuilder setSubtypeExtraValue(String value){extra=text(value);return this;}
        public InputMethodSubtypeBuilder setIsAuxiliary(boolean value){auxiliary=value;return this;}public InputMethodSubtypeBuilder setOverridesImplicitlyEnabledSubtype(boolean value){overrides=value;return this;}public InputMethodSubtypeBuilder setIsAsciiCapable(boolean value){ascii=value;return this;}
        public InputMethodSubtype build(){return new InputMethodSubtype(this);}
    }
}
