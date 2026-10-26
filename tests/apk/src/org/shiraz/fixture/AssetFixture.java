// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class AssetFixture {
    static byte[] bytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[128]; int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }
    static void check(Context context) throws Exception {
        byte[] expected = "Shiraz asset region: café 0123456789\n".getBytes(StandardCharsets.UTF_8);
        AssetManager assets = context.getAssets();
        if (assets != context.getApplicationContext().getAssets()) throw new AssertionError("Context asset manager identity");
        InputStream input = assets.open("region.txt");
        if (input.available() != expected.length || input.read() != 'S' || !input.markSupported()) throw new AssertionError("Asset stream metadata");
        input.mark(100); if (input.read() != 'h') throw new AssertionError("Asset byte"); input.reset();
        if (input.read() != 'h' || input.skip(10000) != expected.length - 2 || input.read() != -1) throw new AssertionError("Asset stream bounds/mark/reset");
        input.close();
        try { input.read(); throw new AssertionError("Closed asset stream accepted"); } catch (IOException good) { }
        try (InputStream compressed = assets.open("compressed.dat", AssetManager.ACCESS_BUFFER)) {
            if (!new String(bytes(compressed), StandardCharsets.UTF_8).startsWith("compressed:")) throw new AssertionError("Compressed asset contents");
        }
        long expectedCrc;try(InputStream checksum=assets.open("large-stream-crc.txt")){expectedCrc=Long.parseLong(new String(bytes(checksum),StandardCharsets.US_ASCII));}
        try(InputStream large=assets.open("large-stream.dat")){
            int length=2049*8192;if(large.available()!=length)throw new AssertionError("Large streaming length");
            if(large.skip(8192+123)!=8192+123||large.read()!=((123*31+(123>>8))&255))throw new AssertionError("Compressed stream seek");
            large.mark(1);int next=large.read();large.reset();if(large.read()!=next)throw new AssertionError("Compressed stream mark/reset");
        }
        try(InputStream large=assets.open("large-stream.dat",AssetManager.ACCESS_BUFFER)){
            java.util.zip.CRC32 crc=new java.util.zip.CRC32();byte[] chunk=new byte[65536];int count;long total=0;
            while((count=large.read(chunk))!=-1){crc.update(chunk,0,count);total+=count;}
            if(total!=2049L*8192||crc.getValue()!=expectedCrc||large.available()!=0||large.skip(Long.MAX_VALUE)!=0)throw new AssertionError("Large compressed streaming contents/EOF");
        }
        try { assets.openFd("compressed.dat"); throw new AssertionError("Compressed descriptor accepted"); } catch (IOException good) { }
        try { assets.open("missing.asset"); throw new AssertionError("Missing asset accepted"); } catch (FileNotFoundException good) { }
        try { assets.open("../escape"); throw new AssertionError("Traversing asset accepted"); } catch (IllegalArgumentException good) { }
        AssetFileDescriptor region = assets.openFd("region.txt");
        ParcelFileDescriptor descriptor = region.getParcelFileDescriptor();
        if (!region.getFileDescriptor().valid() || descriptor.getFd() < 0 || region.getStartOffset() <= 0 || region.getLength() != expected.length
                || region.getDeclaredLength() != expected.length || descriptor.getStatSize() != new File(context.getPackageCodePath()).length())
            throw new AssertionError("Real package descriptor / offset / length");
        ParcelFileDescriptor duplicate = descriptor.dup();
        if (duplicate.getFd() == descriptor.getFd()) throw new AssertionError("dup reused descriptor number");
        try (AssetFileDescriptor copy = new AssetFileDescriptor(duplicate, region.getStartOffset(), region.getLength()); InputStream stream = copy.createInputStream()) {
            byte[] output = new byte[expected.length + 32];
            int count = stream.read(output);
            if (count != expected.length || !Arrays.equals(expected, Arrays.copyOf(output, count)) || stream.read() != -1 || stream.available() != 0)
                throw new AssertionError("Descriptor stream escaped asset region");
        }
        region.close(); region.close();
        if (descriptor.getFileDescriptor().valid()) throw new AssertionError("Closed asset descriptor still valid");
        try { descriptor.getFd(); throw new AssertionError("Closed descriptor number returned"); } catch (IllegalStateException good) { }
        File file = new File(context.getCacheDir(), "descriptor-fixture.txt");
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(expected); }
        ParcelFileDescriptor old = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        try (FileInputStream external = new FileInputStream(old.getFileDescriptor())) { if (external.read() != 'S') throw new AssertionError("External stream FD interop"); }
        ParcelFileDescriptor replacement = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        old.close();
        if (!replacement.getFileDescriptor().valid()) throw new AssertionError("Double close invalidated replacement descriptor");
        int detached = replacement.detachFd(); replacement.close();
        try (ParcelFileDescriptor adopted = ParcelFileDescriptor.adoptFd(detached); AssetFileDescriptor whole = new AssetFileDescriptor(adopted, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
                FileInputStream stream = whole.createInputStream()) {
            if (whole.getLength() != expected.length || !Arrays.equals(bytes(stream), expected)) throw new AssertionError("Detach/adopt/unknown length");
        }
        if (!file.delete()) throw new AssertionError("Descriptor fixture cleanup");
        System.out.println("SHIRAZ_FIXTURE_ASSETS_OK");
    }
}
