// SPDX-License-Identifier: GPL-3.0-only
package android.content.res;
import java.util.Locale;
import android.os.LocaleList;
import android.os.Parcelable;
import android.os.Parcel;

public final class Configuration implements Parcelable {
    public static final int MNC_ZERO = 0xffff;
    public int mcc, mnc;
    public static final int ORIENTATION_UNDEFINED = 0, ORIENTATION_PORTRAIT = 1, ORIENTATION_LANDSCAPE = 2;
    public static final int SCREENLAYOUT_LAYOUTDIR_MASK = 0xc0, SCREENLAYOUT_LAYOUTDIR_LTR = 0x40, SCREENLAYOUT_LAYOUTDIR_RTL = 0x80;
    public static final int UI_MODE_TYPE_NORMAL = 1, UI_MODE_NIGHT_MASK = 0x30, UI_MODE_NIGHT_NO = 0x10, UI_MODE_NIGHT_YES = 0x20;
    public static final int KEYBOARD_UNDEFINED=0,KEYBOARD_NOKEYS=1,KEYBOARD_QWERTY=2,KEYBOARD_12KEY=3;
    public static final int KEYBOARDHIDDEN_UNDEFINED=0,KEYBOARDHIDDEN_NO=1,KEYBOARDHIDDEN_YES=2,KEYBOARDHIDDEN_SOFT=3;
    public static final int HARDKEYBOARDHIDDEN_UNDEFINED=0,HARDKEYBOARDHIDDEN_NO=1,HARDKEYBOARDHIDDEN_YES=2;
    public static final int NAVIGATION_UNDEFINED=0,NAVIGATION_NONAV=1,NAVIGATION_DPAD=2,NAVIGATION_TRACKBALL=3,NAVIGATION_WHEEL=4;
    public static final int NAVIGATIONHIDDEN_UNDEFINED=0,NAVIGATIONHIDDEN_NO=1,NAVIGATIONHIDDEN_YES=2;
    public static final int TOUCHSCREEN_UNDEFINED=0,TOUCHSCREEN_NOTOUCH=1,TOUCHSCREEN_STYLUS=2,TOUCHSCREEN_FINGER=3;
    public int keyboard,keyboardHidden,hardKeyboardHidden,navigation,navigationHidden,touchscreen;
    public float fontScale;
    public Locale locale;
    public int orientation, screenWidthDp, screenHeightDp, smallestScreenWidthDp, densityDpi, screenLayout, uiMode;
    private LocaleList locales = LocaleList.getEmptyLocaleList();
    /** Asset-path and font-scale changes invalidate every resource, regardless of its qualifiers. */
    public static boolean needNewResources(int changes,int interests) {
        if ((changes & 0xc0000000) != 0) return true;
        return (changes & interests) != 0;
    }
    public Configuration() { }
    public Configuration(Configuration other) { setTo(other); }
    public void setTo(Configuration other) {
        mcc=other.mcc; mnc=other.mnc;
        keyboard=other.keyboard;keyboardHidden=other.keyboardHidden;hardKeyboardHidden=other.hardKeyboardHidden;
        navigation=other.navigation;navigationHidden=other.navigationHidden;touchscreen=other.touchscreen;
        fontScale = other.fontScale; locale = other.locale; locales = other.locales;
        orientation = other.orientation; screenWidthDp = other.screenWidthDp; screenHeightDp = other.screenHeightDp;
        smallestScreenWidthDp = other.smallestScreenWidthDp; densityDpi = other.densityDpi; screenLayout = other.screenLayout; uiMode = other.uiMode;
    }
    /** Report defined-field changes without modifying this configuration. */
    public int diff(Configuration delta) { return new Configuration(this).updateFrom(delta); }
    /** Merge defined fields of the implemented configuration subset. */
    public int updateFrom(Configuration delta) {
        if (delta == null) throw new NullPointerException("delta");
        int changed = 0;
        if (delta.mcc!=0 && mcc!=delta.mcc) { mcc=delta.mcc; changed|=1; }
        if (delta.mnc!=0 && mnc!=delta.mnc) { mnc=delta.mnc; changed|=2; }
        if(delta.keyboard!=0&&keyboard!=delta.keyboard){keyboard=delta.keyboard;changed|=0x10;}
        if(delta.keyboardHidden!=0&&keyboardHidden!=delta.keyboardHidden){keyboardHidden=delta.keyboardHidden;changed|=0x20;}
        if(delta.hardKeyboardHidden!=0&&hardKeyboardHidden!=delta.hardKeyboardHidden){hardKeyboardHidden=delta.hardKeyboardHidden;changed|=0x20;}
        if(delta.navigation!=0&&navigation!=delta.navigation){navigation=delta.navigation;changed|=0x40;}
        if(delta.navigationHidden!=0&&navigationHidden!=delta.navigationHidden){navigationHidden=delta.navigationHidden;changed|=0x20;}
        if(delta.touchscreen!=0&&touchscreen!=delta.touchscreen){touchscreen=delta.touchscreen;changed|=0x8;}
        if (delta.fontScale > 0 && fontScale != delta.fontScale) { fontScale = delta.fontScale; changed |= 0x40000000; }
        LocaleList incoming = delta.getLocales();
        if (!incoming.isEmpty() && !getLocales().equals(incoming)) {
            boolean firstChanged = !incoming.get(0).equals(locale);
            locales = incoming; locale = incoming.get(0); changed |= 4;
            if (firstChanged) { setLayoutDirection(locale); changed |= 0x2000; }
        }
        int[] masks = {15, 48, 192, 768};
        for (int mask : masks) {
            int value = delta.screenLayout & mask;
            if (value != 0 && value != (screenLayout & mask)) {
                screenLayout = (screenLayout & ~mask) | value;
                changed |= mask == 192 ? 0x2000 : 0x100;
            }
        }
        if (delta.screenLayout != 0 && ((screenLayout ^ delta.screenLayout) & 0x10000000) != 0) {
            screenLayout = (screenLayout & ~0x10000000) | (delta.screenLayout & 0x10000000); changed |= 0x100;
        }
        if (delta.orientation != 0 && orientation != delta.orientation) { orientation = delta.orientation; changed |= 0x80; }
        if (delta.screenWidthDp != 0 && screenWidthDp != delta.screenWidthDp) { screenWidthDp = delta.screenWidthDp; changed |= 0x400; }
        if (delta.screenHeightDp != 0 && screenHeightDp != delta.screenHeightDp) { screenHeightDp = delta.screenHeightDp; changed |= 0x400; }
        if (delta.smallestScreenWidthDp != 0 && smallestScreenWidthDp != delta.smallestScreenWidthDp) { smallestScreenWidthDp = delta.smallestScreenWidthDp; changed |= 0x800; }
        if (delta.densityDpi != 0 && densityDpi != delta.densityDpi) { densityDpi = delta.densityDpi; changed |= 0x1000; }
        if (delta.uiMode != 0 && uiMode != delta.uiMode) {
            int previous=uiMode;
            if ((delta.uiMode & 15) != 0) uiMode = (uiMode & ~15) | (delta.uiMode & 15);
            if ((delta.uiMode & 48) != 0) uiMode = (uiMode & ~48) | (delta.uiMode & 48);
            if(previous!=uiMode)changed|=0x200;
        }
        return changed;
    }
    public LocaleList getLocales() {
        if (locale == null) locales = LocaleList.getEmptyLocaleList();
        else if (!locale.equals(locales.get(0))) locales = new LocaleList(locale);
        return locales;
    }
    public void setLocales(LocaleList values) {
        if (values == null) throw new NullPointerException("locales");
        locales = values; locale = values.get(0); setLayoutDirection(locale);
    }
    public void setLocale(Locale value) { setLocales(value == null ? LocaleList.getEmptyLocaleList() : new LocaleList(value)); }
    public void setLayoutDirection(Locale value) {
        screenLayout = (screenLayout & ~SCREENLAYOUT_LAYOUTDIR_MASK) | (android.text.TextUtils.getLayoutDirectionFromLocale(value) == 1 ? SCREENLAYOUT_LAYOUTDIR_RTL : SCREENLAYOUT_LAYOUTDIR_LTR);
    }
    public int getLayoutDirection() { return (screenLayout & SCREENLAYOUT_LAYOUTDIR_MASK) == SCREENLAYOUT_LAYOUTDIR_RTL ? 1 : 0; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof Configuration)) return false;
        Configuration c = (Configuration) other;
        if (mcc!=c.mcc || mnc!=c.mnc) return false;
        return keyboard==c.keyboard&&keyboardHidden==c.keyboardHidden&&hardKeyboardHidden==c.hardKeyboardHidden&&navigation==c.navigation&&navigationHidden==c.navigationHidden&&touchscreen==c.touchscreen&&Float.compare(fontScale, c.fontScale) == 0 && getLocales().equals(c.getLocales())
            && orientation == c.orientation && screenWidthDp == c.screenWidthDp && screenHeightDp == c.screenHeightDp
            && smallestScreenWidthDp == c.smallestScreenWidthDp && densityDpi == c.densityDpi && screenLayout == c.screenLayout && uiMode == c.uiMode;
    }
    public boolean equals(Configuration other) { return equals((Object)other); }
    @Override public int hashCode() {
        return java.util.Objects.hash(mcc,mnc,keyboard,keyboardHidden,hardKeyboardHidden,navigation,navigationHidden,touchscreen,fontScale, getLocales(), orientation, screenWidthDp, screenHeightDp, smallestScreenWidthDp, densityDpi, screenLayout, uiMode);
    }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { throw new UnsupportedOperationException("Configuration parcel transport is not implemented"); }
}
