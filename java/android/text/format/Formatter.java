// SPDX-License-Identifier: GPL-3.0-only
package android.text.format;

import android.content.Context;
import java.util.Locale;

/** Decimal file sizes for the nominal API-27 profile. Unit labels currently use SI symbols. */
public final class Formatter {
    private Formatter() {}
    public static String formatFileSize(Context context,long bytes){return fileSize(context,bytes,false);}
    public static String formatShortFileSize(Context context,long bytes){return fileSize(context,bytes,true);}
    private static String fileSize(Context context,long bytes,boolean shorter){
        if(context==null)return "";
        android.os.LocaleList locales=context.getResources().getConfiguration().getLocales();
        Locale locale=locales.isEmpty()?Locale.getDefault():locales.get(0);
        String[] units={"B","kB","MB","GB","TB","PB"};
        // Convert before taking the magnitude: negating Long.MIN_VALUE overflows.
        double magnitude=Math.abs((double)bytes);
        int unit=0;
        while(magnitude>900&&unit<units.length-1){magnitude/=1000;unit++;}
        int digits=unit==0||magnitude>=100?0:magnitude<1?2:shorter?(magnitude<10?1:0):2;
        double signed=bytes<0?-magnitude:magnitude;
        return String.format(locale,"%."+digits+"f %s",signed,units[unit]);
    }
}
