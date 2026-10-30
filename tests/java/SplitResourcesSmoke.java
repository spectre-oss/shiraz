// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.*;
import android.util.TypedValue;
import java.io.*;
import java.util.*;

public final class SplitResourcesSmoke {
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    private static String text(InputStream input)throws Exception {
        try(InputStream stream=input;ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] block=new byte[128];int count;
            while((count=stream.read(block))!=-1)bytes.write(block,0,count);
            return new String(bytes.toByteArray(),"UTF-8");
        }
    }
    private static int id(Resources resources,String type,String name) {
        int id=resources.getIdentifier(name,type,"org.shiraz.splitfixture");
        check(id!=0,"missing resource identifier");return id;
    }
    private static Configuration locale(Locale locale) {
        Configuration config=new Configuration();config.setLocale(locale);return config;
    }
    public static void main(String[] args)throws Exception {
        Locale.setDefault(Locale.ENGLISH);
        Resources base=Resources.shirazOpen(args[0],160,240);
        String[] splits={args[1]};
        Resources resources=Resources.shirazOpen(args[0],splits,160,240,160);
        splits[0]="changed"; // Manager and configuration children own their path list.
        int label=id(resources,"string","label"),raw=id(resources,"raw","payload");
        int color=id(resources,"color","state");
        check("base".equals(resources.getString(label)),"base locale");
        base.updateConfiguration(locale(Locale.FRENCH),null);
        check("base".equals(base.getString(label)),"split must not leak into base-only manager");
        Resources child=resources.shirazWithConfiguration(locale(Locale.FRENCH));
        check("split".equals(child.getString(label)),"configuration child lost split");
        resources.updateConfiguration(locale(Locale.FRENCH),null);
        check("split".equals(resources.getString(label)),"split locale");
        resources.updateConfiguration(locale(Locale.ENGLISH),null);
        check("base".equals(resources.getString(label)),"base locale restored");
        check("split".equals(child.getString(label)),"child locale override preserved");
        resources.updateConfiguration(locale(Locale.FRENCH),null);
        TypedValue value=new TypedValue();resources.getValue(label,value,true);
        check(value.assetCookie==3,"framework/base/split cookie ordering");
        check(resources.getColorStateList(color).getDefaultColor()==0xff123456,"split color selector owner");
        check("split raw".equals(text(resources.openRawResource(raw))),"split raw resource owner");
        resources.getValue(raw,value,true);
        check("split raw".equals(text(resources.getAssets().openNonAsset(value.assetCookie,value.string.toString(),2))),"explicit split cookie");
        check(Arrays.equals(resources.getAssets().list(""),new String[]{"base.txt","common.txt","compressed.txt","dir","split.txt"}),"merged asset listing");
        check(Arrays.equals(resources.getAssets().list("dir"),new String[]{"base.txt","split.txt"}),"merged child listing");
        check("split asset".equals(text(resources.getAssets().open("common.txt"))),"split asset precedence");
        check("split asset".equals(text(resources.getAssets().open("split.txt"))),"split asset stream");
        check("compressed split".equals(text(resources.getAssets().open("compressed.txt"))),"compressed split stream");
        try {resources.getAssets().openFd("compressed.txt");throw new AssertionError("compressed asset descriptor accepted");}
        catch(IOException expected) {}
        try {resources.getAssets().openNonAsset(4,"res/raw/payload.txt",2);throw new AssertionError("invalid cookie accepted");}
        catch(IllegalArgumentException expected) {}
        try {resources.newTheme().setTo(base.newTheme());throw new AssertionError("cross-package-set theme accepted");}
        catch(UnsupportedOperationException expected) {}
        AssetFileDescriptor rawFd=resources.openRawResourceFd(raw);
        AssetFileDescriptor assetFd=resources.getAssets().openFd("split.txt");
        InputStream retained=resources.getAssets().open("split.txt");
        check(rawFd!=null,"stored split raw descriptor");
        resources.shirazClose();
        check("split asset".equals(text(retained)),"stream owner lifetime");
        try(AssetFileDescriptor fd=rawFd) {check("split raw".equals(text(fd.createInputStream())),"raw descriptor lifetime");}
        try(AssetFileDescriptor fd=assetFd) {check("split asset".equals(text(fd.createInputStream())),"asset descriptor lifetime");}
        try {child.getString(label);throw new AssertionError("child survived owner closure");}
        catch(IllegalStateException expected) {}
        base.shirazClose();
        for(String[] invalid:new String[][]{{args[0]},{args[1],args[1]},new String[257],{null},{""}}) {
            try {Resources.shirazOpen(args[0],invalid,160,240,160);throw new AssertionError("invalid split list accepted");}
            catch(IllegalArgumentException expected) {}
        }
        try {Resources.shirazOpen(args[0],new String[]{args[1]+".missing"},160,240,160);throw new AssertionError("missing split accepted");}
        catch(Resources.NotFoundException expected) {}
        System.out.println("SPLIT_RESOURCES_ASSETS_OWNERSHIP_OK");
    }
}
