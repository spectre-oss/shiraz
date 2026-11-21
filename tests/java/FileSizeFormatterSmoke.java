// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.res.*;
import android.text.format.Formatter;
import java.util.Locale;
public final class FileSizeFormatterSmoke {
    static void equal(String expected,String actual){if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args){
        Context base=new ContextWrapper(null){public Resources getResources(){return Resources.getSystem();}};
        Configuration english=new Configuration();english.setLocale(Locale.US);Context context=new shiraz.runtime.ConfigurationContext(base,english);
        equal("",Formatter.formatShortFileSize(null,123));equal("0 B",Formatter.formatShortFileSize(context,0));
        equal("900 B",Formatter.formatFileSize(context,900));equal("0.90 kB",Formatter.formatShortFileSize(context,901));
        equal("1.0 kB",Formatter.formatShortFileSize(context,1000));equal("1.00 kB",Formatter.formatFileSize(context,1000));
        equal("12 kB",Formatter.formatShortFileSize(context,12345));equal("12.35 kB",Formatter.formatFileSize(context,12345));
        equal("-12 kB",Formatter.formatShortFileSize(context,-12345));
        equal("0.90 MB",Formatter.formatFileSize(context,900001));
        equal("-9223 PB",Formatter.formatShortFileSize(context,Long.MIN_VALUE));equal("9223 PB",Formatter.formatFileSize(context,Long.MAX_VALUE));
        Configuration german=new Configuration();german.setLocale(Locale.GERMANY);Context localized=new shiraz.runtime.ConfigurationContext(base,german);
        equal("1,5 kB",Formatter.formatShortFileSize(localized,1500));
        equal("1.5 kB",Formatter.formatShortFileSize(context,1500));
        System.out.println("FILE_SIZE_FORMATTER_OK");
    }
}
