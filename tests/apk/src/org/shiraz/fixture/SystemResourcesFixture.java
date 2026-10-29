// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.res.Resources;
import android.util.DisplayMetrics;
public final class SystemResourcesFixture {
    public static void check(Resources app){
        Resources system=Resources.getSystem();if(system==app||system!=Resources.getSystem())throw new AssertionError("system singleton ownership");
        DisplayMetrics dm=system.getDisplayMetrics(),actual=app.getDisplayMetrics();if(dm.widthPixels!=actual.widthPixels||dm.heightPixels!=actual.heightPixels||dm.densityDpi!=160)throw new AssertionError("system display geometry");
        int ok=system.getIdentifier("ok","string","android");if(ok==0||system.getString(ok).isEmpty())throw new AssertionError("genuine framework string");
        if(system.getIdentifier("localized","string","org.shiraz.fixture")!=0)throw new AssertionError("app resource leaked into system manager");
        int colors=system.getIdentifier("primary_text_light","color","android");if(colors==0||!system.getColorStateList(colors).isStateful())throw new AssertionError("framework selector resource");
        try(java.io.InputStream input=system.openRawResource(colors)){if(input.read()!=3||input.read()!=0)throw new AssertionError("framework XML file ownership/cookie");}catch(java.io.IOException failure){throw new AssertionError(failure);}
        dm.widthPixels=1;if(system.getDisplayMetrics().widthPixels==1)throw new AssertionError("system metrics leaked mutable backing state");
        int button=system.getIdentifier("Widget.Material.Button.Colored","style","android");if(button!=0x010302d3)throw new AssertionError("Colored button public ID");
        Resources.Theme theme=app.newTheme();theme.applyStyle(android.R.style.Theme_Material_Light_NoActionBar,true);theme.applyStyle(button,true);
        android.content.res.TypedArray values=theme.obtainStyledAttributes(new int[]{android.R.attr.background,android.R.attr.textAppearance,android.R.attr.minHeight});
        try{if(values.getResourceId(0,0)==0||values.getResourceId(1,0)==0||values.getDimensionPixelSize(2,0)<=0)throw new AssertionError("Colored button resources and inherited dimensions");}finally{values.recycle();}
        Resources.Theme fullscreen=app.newTheme();fullscreen.applyStyle(android.R.style.Theme_Black_NoTitleBar_Fullscreen,true);
        android.content.res.TypedArray window=fullscreen.obtainStyledAttributes(new int[]{android.R.attr.windowFullscreen,android.R.attr.windowNoTitle,android.R.attr.windowBackground});
        try{if(!window.getBoolean(0,false)||!window.getBoolean(1,false)||window.getResourceId(2,0)==0)throw new AssertionError("Black fullscreen theme and inherited window attributes");}finally{window.recycle();}
        System.out.println("SHIRAZ_FIXTURE_SYSTEM_RESOURCES_OK");
    }
}
