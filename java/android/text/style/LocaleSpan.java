// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.os.LocaleList;
import android.text.TextPaint;
/** Records the run locale; shaping does not consume it yet. */
public class LocaleSpan extends CharacterStyle implements UpdateAppearance {
 private final LocaleList locales;
 public LocaleSpan(LocaleList locales){if(locales==null)throw new NullPointerException("locales");this.locales=locales;}
 public LocaleSpan(java.util.Locale locale){this(new LocaleList(locale));}
 public LocaleList getLocales(){return locales;}
 public java.util.Locale getLocale(){return locales.get(0);}
 public void updateDrawState(TextPaint paint){}
}
