// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Resources;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class ResourceLifetime {
    static long descriptors(String apk) throws Exception {
        try (Stream<Path> files = Files.list(Paths.get("/proc/self/fd"))) {
            return files.filter(path -> {
                try { return Files.readSymbolicLink(path).toString().equals(apk); }
                catch (Exception error) { return false; }
            }).count();
        }
    }
    static long mappings(String apk) throws Exception {
        try (Stream<String> lines = Files.lines(Paths.get("/proc/self/maps"))) { return lines.filter(line -> line.endsWith(apk)).count(); }
    }
    public static void main(String[] args) throws Exception {
        String apk = Paths.get(args[0]).toRealPath().toString();
        int id = (int) Long.parseLong(args[1]);
        long beforeFD = descriptors(apk), beforeMaps = mappings(apk);
        String framework = System.getProperty("shiraz.frameworkResources");
        long frameworkFD = framework == null ? 0 : descriptors(framework), frameworkMaps = framework == null ? 0 : mappings(framework);
        Resources capped=Resources.shirazOpen(apk,96,160);java.util.ArrayList<java.io.InputStream> opened=new java.util.ArrayList<java.io.InputStream>();
        try{
            for(int i=0;i<256;i++)opened.add(capped.getAssets().open("region.txt"));
            try{capped.getAssets().open("region.txt");throw new AssertionError("Asset handle cap ignored");}catch(java.io.IOException expected){}
        }finally{for(java.io.InputStream stream:opened)stream.close();capped.shirazClose();}
        for (int count = 0; count < 128; ++count) {
            Resources resources = Resources.shirazOpen(apk, 96, 160);
            android.util.TypedValue typed=new android.util.TypedValue(); resources.getValue(id,typed,true);
            if (typed.resourceId != id || typed.type != android.util.TypedValue.TYPE_STRING || !"Shiraz activity fixture – café".contentEquals(typed.string)) throw new AssertionError("Typed resource ownership");
            if (!"Shiraz activity fixture – café".equals(resources.getString(id))) throw new AssertionError("Resource contents");
            if (resources.getIdentifier("org.shiraz.fixture:string/app_name", null, null) != id) throw new AssertionError("Resource name");
            for (int repeat = 0; repeat < 3; ++repeat) {
                if (resources.getIdentifier("app_name", "string", "org.shiraz.fixture") != id
                        || resources.getIdentifier("string/app_name", null, "org.shiraz.fixture") != id
                        || resources.getIdentifier("org.shiraz.fixture:string/app_name", "drawable", "absent") != id)
                    throw new AssertionError("Qualified resource ID cache");
                if (resources.getIdentifier("app_name", "drawable", "org.shiraz.fixture") != 0
                        || resources.getIdentifier("app_name", "string", "absent") != 0
                        || resources.getIdentifier("app_name\0suffix", "string", "org.shiraz.fixture") != 0
                        || resources.getIdentifier("", "string", "org.shiraz.fixture") != 0)
                    throw new AssertionError("Resource ID key isolation/misses");
                for (String numeric : new String[]{"+123", "-123", "2147483647", "-2147483648", "١٢٣"})
                    if (resources.getIdentifier(numeric, null, null) != Integer.parseInt(numeric))
                        throw new AssertionError("Numeric identifier semantics");
                if (resources.getIdentifier("2147483648", "string", "org.shiraz.fixture") != 0)
                    throw new AssertionError("Overflow identifier");
            }
            if (count == 0) {
                // Qualified names override defaults, exercising bounded cache turnover.
                for (int key = 0; key < 4200; ++key)
                    if (resources.getIdentifier("org.shiraz.fixture:string/app_name", "string", "fallback" + key) != id)
                        throw new AssertionError("Resource ID cache turnover");
                for (int key = 0; key < 4200; ++key)
                    if (resources.getIdentifier("missing" + key, "string", "org.shiraz.fixture") != 0)
                        throw new AssertionError("Negative resource ID cache turnover");
                char[] large = new char[1100]; java.util.Arrays.fill(large, 'x');
                if (resources.getIdentifier("org.shiraz.fixture:string/app_name", null, new String(large)) != id
                        || resources.getIdentifier("app_name", "string", "org.shiraz.fixture") != id)
                    throw new AssertionError("Uncached long fallback / revisited ID");
            }
            android.content.res.Configuration config = resources.getConfiguration(); config.setLocale(java.util.Locale.FRENCH);
            resources.updateConfiguration(config, null);
            android.content.res.Configuration scaled = new android.content.res.Configuration(config); scaled.densityDpi = 320; scaled.fontScale = 2;
            resources.updateConfiguration(scaled, null);
            android.view.Display display = android.view.Display.shirazForResources(resources);
            android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics(); display.getRealMetrics(displayMetrics);
            if (displayMetrics.densityDpi != 160 || displayMetrics.widthPixels != 96 || displayMetrics.heightPixels != 160 || displayMetrics.scaledDensity != 1)
                throw new AssertionError("Display initialized after app override lost base profile");
            display.getMetrics(displayMetrics); if (displayMetrics.densityDpi != 320 || displayMetrics.scaledDensity != 4) throw new AssertionError("Display app override");
            resources.updateConfiguration(config, null);
            if (resources.getIdentifier("app_name", "string", "org.shiraz.fixture") != id)
                throw new AssertionError("Cached ID after configuration change");
            int localized = resources.getIdentifier("localized", "string", "org.shiraz.fixture");
            if (!"Langue française".equals(resources.getString(localized))) throw new AssertionError("Updated locale selection");
            android.content.res.Resources.Theme theme = resources.newTheme();
            theme.applyStyle(resources.getIdentifier("FixtureBase", "style", "org.shiraz.fixture"), true);
            android.util.TypedValue themed = new android.util.TypedValue();
            if (!theme.resolveAttribute(resources.getIdentifier("themeLabel", "attr", "org.shiraz.fixture"), themed, true)
                    || !"Langue française".contentEquals(themed.string)) throw new AssertionError("Theme reference value");
            int selector = resources.getIdentifier("selector_plain", "color", "org.shiraz.fixture");
            if (resources.getColor(selector) != 0xffaabbcc || !resources.getColorStateList(selector).isStateful())
                throw new AssertionError("Compiled color selector");
            int deferredId = resources.getIdentifier("selector_deferred", "color", "org.shiraz.fixture");
            android.content.res.ColorStateList unresolved = resources.getColorStateList(deferredId, resources.newTheme());
            if (!unresolved.canApplyTheme() || unresolved.getDefaultColor() != 0x1fff00ff)
                throw new AssertionError("Missing theme color must remain deferred with selector alpha");
            android.content.res.ColorStateList resolved = unresolved.obtainForTheme(theme);
            if (resolved.canApplyTheme() || resolved.getDefaultColor() != 0x1f112233 || !unresolved.canApplyTheme())
                throw new AssertionError("Deferred theme resolution / source isolation");
            if (resources.getColorStateList(deferredId).obtainForTheme(theme).getDefaultColor() != resources.getColor(deferredId, theme))
                throw new AssertionError("Deferred and eager theme colors differ");
            try { resources.getColor(resources.getIdentifier("selector_cycle_a", "color", "org.shiraz.fixture")); throw new AssertionError("Cyclic color accepted"); }
            catch (Resources.NotFoundException expected) { }
            android.content.res.AssetFileDescriptor asset = resources.getAssets().openFd("region.txt");
            byte[] expectedAsset = "Shiraz asset region: café 0123456789\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int assetFd = asset.getParcelFileDescriptor().getFd();
            try (android.os.ParcelFileDescriptor copy = android.os.ParcelFileDescriptor.fromFd(assetFd)) {
                if (copy.getFd() == assetFd || copy.getStatSize() != asset.getParcelFileDescriptor().getStatSize())
                    throw new AssertionError("fromFd must duplicate the borrowed descriptor");
            }
            if (!java.util.Arrays.equals(asset.shirazReadRegion(), expectedAsset))
                throw new AssertionError("Closing duplicate closed caller descriptor");
            try { android.os.ParcelFileDescriptor.fromFd(-1); throw new AssertionError("Invalid raw fd accepted"); }
            catch (java.io.IOException expected) { }

            String flags = new String(Files.readAllBytes(Paths.get("/proc/self/fdinfo/" + assetFd)), java.nio.charset.StandardCharsets.US_ASCII);
            boolean cloexec = false;
            for (String line : flags.split("\n")) if (line.startsWith("flags:"))
                cloexec = (Long.parseLong(line.substring(6).trim(), 8) & 02000000) != 0;
            if (!cloexec) throw new AssertionError("Asset descriptor lacks close-on-exec");
            java.io.InputStream survivor=resources.getAssets().open("compressed.dat");
            resources.shirazClose(); resources.shirazClose();
            try{if(survivor.read()!='c')throw new AssertionError("Asset stream lifetime after resource close");survivor.mark(0);int c=survivor.read();survivor.reset();if(survivor.read()!=c)throw new AssertionError("Surviving stream cursor");}finally{survivor.close();}

            if (!"Shiraz activity fixture – café".contentEquals(typed.string)) throw new AssertionError("Typed string outlived resource manager incorrectly");
            if (display.isValid()) throw new AssertionError("Display outlived closed app session");
            try { display.getMetrics(displayMetrics); throw new AssertionError("Closed display metrics accepted"); } catch (IllegalStateException expected) { }
            if (!java.util.Arrays.equals(asset.shirazReadRegion(), expectedAsset)) throw new AssertionError("Asset descriptor lifetime");
            try (java.io.FileInputStream stream = asset.createInputStream()) {
                if (stream.read() != 'S') throw new AssertionError("Asset region start");
                if (!java.util.Arrays.equals(asset.shirazReadRegion(), expectedAsset) || stream.read() != 'h')
                    throw new AssertionError("Position-independent region read changed cursor");
                stream.skip(expectedAsset.length);
                if (stream.read() != -1) throw new AssertionError("Asset EOF");
                stream.close();
                try { stream.read(); throw new AssertionError("Closed EOF stream accepted"); }
                catch (java.io.IOException expected) { }
            }
            asset.close();
            try { resources.getString(id); throw new AssertionError("Closed resources accepted"); }
            catch (IllegalStateException expected) { }
            try { resources.getIdentifier("app_name", "string", "org.shiraz.fixture"); throw new AssertionError("Closed name lookup accepted"); }
            catch (IllegalStateException expected) { }
            try { resources.updateConfiguration(null, null); throw new AssertionError("Closed configuration update accepted"); }
            catch (IllegalStateException expected) { }
            try { theme.resolveAttribute(0, themed, true); throw new AssertionError("Closed theme lookup accepted"); }
            catch (IllegalStateException expected) { }
            try { resources.getColor(selector); throw new AssertionError("Closed color lookup accepted"); }
            catch (IllegalStateException expected) { }
        }
        Resources first = Resources.shirazOpen(apk, 96, 160), second = Resources.shirazOpen(apk, 96, 160);
        first.shirazClose();
        if (!second.getString(id).contains("café")) throw new AssertionError("Independent resource managers");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try { for (int i = 0; i < 1000; ++i) { second.getValue(id,new android.util.TypedValue(),true); second.getString(id); second.getIdentifier("string/app_name", null, "org.shiraz.fixture"); second.getConfiguration(); } }
            catch (IllegalStateException expected) { }
            catch (Throwable error) { failure.set(error); }
        });
        reader.start(); second.shirazClose(); reader.join();
        if (failure.get() != null) throw new AssertionError(failure.get());
        for (int count = 0; count < 8; ++count) {
            try { Resources.shirazOpen(apk + ".absent", 96, 160); throw new AssertionError("Absent APK accepted"); }
            catch (Resources.NotFoundException expected) { }
        }
        if (descriptors(apk) != beforeFD || mappings(apk) != beforeMaps) throw new AssertionError("APK resource descriptors/mappings leaked");
        if (framework != null && (descriptors(framework) != frameworkFD || mappings(framework) != frameworkMaps)) throw new AssertionError("Framework resource descriptors/mappings leaked");
        System.out.println("RESOURCE_LIFETIME_OK");
    }
}
