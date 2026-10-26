// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.util.TypedValue;
import android.util.DisplayMetrics;
import java.util.Locale;

public final class TypedResourceFixture {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static void near(float value,float expected,String message) { check(Math.abs(value-expected) < .0001f,message+": "+value); }
    private static int id(Resources resources,String name) {
        int value=resources.getIdentifier("org.shiraz.fixture:"+name,null,null); check(value != 0,"fixture resource "+name); return value;
    }
    private static void equal(TypedValue first,TypedValue second) {
        check(first.type == second.type && first.data == second.data && first.resourceId == second.resourceId && first.assetCookie == second.assetCookie && first.density == second.density && first.changingConfigurations == second.changingConfigurations && (first.string == null ? second.string == null : first.string.equals(second.string)),"failed lookup must preserve all output fields");
    }
    public static void units() {
        near(TypedValue.complexToFloat(123 << 8),123,"integer complex mantissa");
        near(TypedValue.complexToFloat((1600 << 8)|16),12.5f,"16p7 radix");
        near(TypedValue.complexToFloat((409600 << 8)|32),12.5f,"8p15 radix");
        near(TypedValue.complexToFloat((1 << 30)|48),.5f,"0p23 radix");
        near(TypedValue.complexToFloat((-1600 << 8)|16),-12.5f,"signed complex mantissa");
        DisplayMetrics metrics=new DisplayMetrics(); metrics.density=2; metrics.scaledDensity=3; metrics.xdpi=144;
        near(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,2,metrics),2,"px unit");
        near(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,2,metrics),4,"dp unit");
        near(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,2,metrics),6,"linear sp unit");
        near(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PT,72,metrics),144,"pt unit");
        near(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_IN,1,metrics),144,"inch unit");
        near(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM,25.4f,metrics),144,"mm unit");
        int positive=(1600 << 8)|16|TypedValue.COMPLEX_UNIT_PX, negative=(-1600 << 8)|16;
        check(TypedValue.complexToDimensionPixelOffset(positive,metrics) == 12 && TypedValue.complexToDimensionPixelSize(positive,metrics) == 13,"offset truncates while size rounds");
        check(TypedValue.complexToDimensionPixelOffset(negative,metrics) == -12 && TypedValue.complexToDimensionPixelSize(negative,metrics) == -13,"negative pixel rounding");
        check(TypedValue.complexToDimensionPixelSize((1 << 8)|32,metrics) == 1 && TypedValue.complexToDimensionPixelSize((-1 << 8)|32,metrics) == -1,"nonzero dimensions retain minimum pixel");
        near(TypedValue.complexToFraction((1 << 30)|48,20,30),10,"base fraction");
        near(TypedValue.complexToFraction((1 << 30)|48|1,20,30),15,"parent fraction");
    }
    public static void check(Resources resources) {
        units(); TypedValue out=new TypedValue(),saved=new TypedValue();
        int string=id(resources,"string/welcome"),alias=id(resources,"string/welcome_alias"),integer=id(resources,"integer/not_string");
        resources.getValue(alias,out,false); check(out.type == TypedValue.TYPE_REFERENCE && out.data == string && out.resourceId == alias && out.string == null,"unresolved reference metadata");
        resources.getValue(alias,out,true); check(out.type == TypedValue.TYPE_STRING && out.resourceId == string && out.assetCookie > 0 && resources.getString(string).contentEquals(out.string),"resolved string/reference metadata");
        CharSequence retained=out.string; resources.getValue(integer,out,true); check(out.type == TypedValue.TYPE_INT_DEC && out.data == 42 && out.resourceId == integer && out.string == null,"integer clears stale text");
        check(retained.toString().equals(resources.getString(string)),"previous strings remain stable");
        resources.getValue("org.shiraz.fixture:string/welcome",out,true); check(retained.equals(out.string),"fully qualified named lookup");
        resources.getValue(Integer.toString(integer),out,true); check(out.data == 42,"numeric resource name");
        resources.getValue(id(resources,"string/typed_empty"),out,true); check(out.type == TypedValue.TYPE_NULL && out.data == TypedValue.DATA_NULL_EMPTY,"explicit empty resource");
        int selector=id(resources,"color/primary_text_light"); resources.getValue(selector,out,true); int appCookie=out.assetCookie;
        resources.getValue(android.R.color.primary_text_light,out,true); check(out.type == TypedValue.TYPE_STRING && out.assetCookie != appCookie && out.string.toString().endsWith(".xml"),"framework and app string pools use distinct cookies");
        out.type=TypedValue.TYPE_FLOAT; out.data=99; out.assetCookie=7; out.resourceId=123; out.changingConfigurations=456; out.density=320; out.string="sentinel"; saved.setTo(out);
        for (int bad:new int[]{0,0x7fffffff,id(resources,"style/FixtureBase"),id(resources,"string/typed_cycle_a"),id(resources,"string/typed_depth_0")}) {
            try { resources.getValue(bad,out,true); throw new AssertionError("missing/bag/cycle accepted"); } catch (Resources.NotFoundException expected) { }
            equal(out,saved);
        }
        resources.getValue(id(resources,"string/typed_depth_32"),out,true); check("deep".contentEquals(out.string),"short suffix of long reference chain resolves");
        resources.getValue(id(resources,"string/typed_cycle_a"),out,false); check(out.type == TypedValue.TYPE_REFERENCE,"unresolved cyclic reference can be inspected");
        try { resources.getString(id(resources,"string/typed_cycle_a")); throw new AssertionError("cyclic string silently coerced"); } catch (Resources.NotFoundException expected) { }
        out.setTo(saved);
        try { resources.getValueForDensity(string,-1,out,true); throw new AssertionError("negative density accepted"); } catch (IllegalArgumentException expected) { } equal(out,saved);
        try { resources.getValue(string,null,true); throw new AssertionError("null output accepted"); } catch (NullPointerException expected) { }
        check(resources.getInteger(integer) == 42 && resources.getBoolean(id(resources,"bool/flag")),"integer and boolean getters");
        near(resources.getFloat(id(resources,"dimen/typed_float")),1.25f,"float getter"); near(resources.getFloat(integer),42,"integer-to-float coercion");
        near(resources.getFraction(id(resources,"fraction/typed_fraction"),200,400),50,"fraction resource");
        near(resources.getFraction(id(resources,"fraction/typed_parent_fraction"),200,400),-200,"parent fraction resource");
        try { resources.getDimension(integer); throw new AssertionError("integer dimension accepted"); } catch (Resources.NotFoundException expected) { }
        Configuration original=resources.getConfiguration(); DisplayMetrics originalMetrics=resources.getDisplayMetrics();
        try {
            Configuration config=new Configuration(original); config.densityDpi=320; config.fontScale=1.5f; resources.updateConfiguration(config,null);
            near(resources.getDimension(id(resources,"dimen/distance")),25,"density-aware dimensions");
            check(resources.getDimensionPixelOffset(id(resources,"dimen/typed_negative")) == -25 && resources.getDimensionPixelSize(id(resources,"dimen/typed_tiny")) == 1,"resource dimension rounding");
            int drawable=id(resources,"drawable/typed_density"),drawableAlias=id(resources,"drawable/typed_density_alias");
            resources.getValueForDensity(drawableAlias,160,out,true); check(out.density == 160 && out.resourceId == drawable && out.string.toString().contains("mdpi"),"density override preserved through alias");
            check((out.changingConfigurations & (ActivityInfo.CONFIG_DENSITY|ActivityInfo.CONFIG_LOCALE)) == (ActivityInfo.CONFIG_DENSITY|ActivityInfo.CONFIG_LOCALE),"alias and target flags accumulate with Java mask positions");
            resources.getValueForDensity(drawableAlias,320,out,true); check(out.density == 320 && out.string.toString().contains("xhdpi"),"xhdpi selection");
            resources.getValue(drawableAlias,out,true); check(out.density == 320,"density override does not change manager configuration");
            resources.getValue(id(resources,"string/localized"),out,true); check((out.changingConfigurations & ActivityInfo.CONFIG_LOCALE) != 0,"locale configuration metadata");
            config.setLocale(Locale.FRENCH); config.orientation=Configuration.ORIENTATION_LANDSCAPE; config.uiMode=(config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_YES; resources.updateConfiguration(config,null);
            resources.getValue(id(resources,"string/localized"),out,true); check("Langue française".contentEquals(out.string),"typed locale update");
            resources.getValue(id(resources,"string/orientation_probe"),out,true); check("landscape-selected".contentEquals(out.string) && (out.changingConfigurations & ActivityInfo.CONFIG_ORIENTATION) != 0,"typed orientation update");
            resources.getValue(id(resources,"color/accent"),out,true); check(out.data == 0xffeeddcc && out.isColorType() && (out.changingConfigurations & ActivityInfo.CONFIG_UI_MODE) != 0,"typed night color and Java configuration mask");
            Resources.Theme theme=resources.newTheme(); theme.applyStyle(id(resources,"style/FixtureBase"),true);
            check(theme.resolveAttribute(id(resources,"attr/themeAccent"),out,true) && (out.changingConfigurations & ActivityInfo.CONFIG_UI_MODE) != 0,"theme masks use the same public configuration mapping");
        } finally { resources.updateConfiguration(original,originalMetrics); }
        System.out.println("SHIRAZ_FIXTURE_TYPED_RESOURCES_OK");
    }
}
