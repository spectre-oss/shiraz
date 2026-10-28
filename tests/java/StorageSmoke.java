// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.HostStorage;
import android.os.Environment;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.stream.Stream;

public final class StorageSmoke {
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Paths.get("build"), "storage-check-").toRealPath();
        try {
            try { Environment.getExternalStorageDirectory(); throw new AssertionError("Unconfigured storage accepted"); }
            catch (IllegalStateException expected) { }
            HostStorage storage = new HostStorage(root.resolve("app").toFile(), "org.shiraz.storage");
            Path internal=storage.filePath("fresh-file").toPath();require(internal.equals(storage.files().toPath().resolve("fresh-file"))&&!Files.exists(internal),"Path lookup must not create the file");
            Files.write(internal,new byte[]{4,5});require(Files.size(storage.filePath("fresh-file").toPath())==2,"Existing internal file path");
            for(String invalid:new String[]{"",".","..","../escape","/absolute","bad\u0000name"}){
                try{storage.filePath(invalid);throw new AssertionError("Invalid internal filename accepted");}catch(IllegalArgumentException expected){}
            }
            Files.createSymbolicLink(storage.files().toPath().resolve("outside-link"),root.resolve("outside-file"));
            try{storage.filePath("outside-link");throw new AssertionError("Internal file symlink escape accepted");}catch(SecurityException expected){}
            Environment.shirazInitialize(storage);
            android.os.storage.StorageManager manager=android.os.storage.StorageManager.shirazForStorage(storage);
            android.os.storage.StorageVolume volume=manager.getPrimaryStorageVolume();
            require(manager.getStorageVolumes().size()==1&&manager.getStorageVolumes().get(0).equals(volume),"Primary volume enumeration");
            manager.getStorageVolumes().clear();require(manager.getStorageVolumes().size()==1,"Caller changed volume registry");
            require(volume.isPrimary()&&volume.isEmulated()&&!volume.isRemovable()&&volume.getUuid()==null,"App volume identity");
            require(volume.getDirectory().equals(storage.primaryPath(null))&&Environment.MEDIA_MOUNTED.equals(volume.getState()),"Mounted primary volume path/state");
            require(manager.getStorageVolume(storage.externalFiles(null)).equals(volume)&&manager.getStorageVolume(storage.files())==null,"Volume path lookup");
            require(manager.getStorageVolume(root.resolve("app/external-other").toFile())==null,"Sibling prefix matched primary volume");
            try{manager.getStorageVolume(null);throw new AssertionError("Null volume path accepted");}catch(NullPointerException expected){}
            require(Environment.getDownloadCacheDirectory().equals(storage.cache()), "Download cache is app scoped");
            require(Environment.getExternalStorageDirectory().equals(storage.primaryPath(null)), "Environment primary path");
            require(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()), "Real filesystem mount state");
            java.lang.reflect.Method readOnly = Environment.class.getDeclaredMethod("nativeIsReadOnly", byte[].class);
            readOnly.setAccessible(true);
            Path sys = Paths.get("/sys");
            // Android libcore forbids FileStore queries; keep this independent host-JDK comparison.
            if (Files.isDirectory(sys)&&!"Dalvik".equals(System.getProperty("java.vm.name"))) require(((Boolean)readOnly.invoke(null, (Object)sys.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                == Files.getFileStore(sys).isReadOnly(), "JNI mount flags agree with host filesystem");
            try { Environment.shirazInitialize(storage); throw new AssertionError("Duplicate primary initialization"); }
            catch (IllegalStateException expected) { }
            require(!Environment.getExternalStoragePublicDirectory("not-created").exists(), "Public lookup unexpectedly created a directory");
            Path files = storage.externalFiles("maps/tracks").toPath();
            require(Files.isDirectory(files), "Typed external directory");
            require(Files.getPosixFilePermissions(files).equals(PosixFilePermissions.fromString("rwx------")), "Private external directory");
            require(storage.data().toPath().equals(root.resolve("app")), "Data root");
            require(Files.getPosixFilePermissions(storage.data().toPath()).equals(PosixFilePermissions.fromString("rwx------")), "Private app root");
            Files.write(files.resolve("map.data"), new byte[]{1, 2, 3});
            require(Files.size(new HostStorage(root.resolve("app").toFile(), "org.shiraz.storage").externalFiles("maps/tracks").toPath().resolve("map.data")) == 3, "Storage reopening");
            Path unavailable = Files.createDirectory(root.resolve("unavailable"));
            Files.write(unavailable.resolve("external"), new byte[]{1});
            HostStorage blocked = new HostStorage(unavailable.toFile(), "org.shiraz.storage");
            require(blocked.externalFiles(null) == null && blocked.externalCache() == null && blocked.obb() == null && blocked.media() == null,
                "Unavailable volume must return null");
            Environment.shirazShutdown();
            Environment.shirazInitialize(blocked);
            require(Environment.MEDIA_UNMOUNTED.equals(Environment.getExternalStorageState()), "Unavailable primary state");
            android.os.storage.StorageVolume unavailableVolume=android.os.storage.StorageManager.shirazForStorage(blocked).getPrimaryStorageVolume();
            require(unavailableVolume.getDirectory()==null&&Environment.MEDIA_UNMOUNTED.equals(unavailableVolume.getState()),"Unavailable storage volume reported mounted");
            require(Environment.getExternalStorageDirectory().equals(unavailable.resolve("external").toFile()), "Unavailable root path still available");
            Environment.shirazShutdown(); Environment.shirazInitialize(storage);
            Files.write(unavailable.resolve("files"), new byte[]{1});
            try { blocked.files(); throw new AssertionError("Unavailable internal files directory accepted"); }
            catch (IllegalStateException expected) { }
            Path outside = Files.createDirectory(root.resolve("outside"));
            Files.createSymbolicLink(storage.externalFiles(null).toPath().resolve("escape"), outside);
            try { storage.externalFiles("escape/new"); throw new AssertionError("Symlink traversal accepted"); }
            catch (SecurityException expected) { }
            require(!Files.exists(outside.resolve("new")), "Escaping directory created outside storage");
            Files.createSymbolicLink(storage.primaryPath(null).toPath().resolve("public-escape"), outside);
            require(manager.getStorageVolume(storage.primaryPath(null).toPath().resolve("public-escape/new").toFile())==null,"Symlink escape matched app volume");
            try { Environment.getExternalStoragePublicDirectory("public-escape/new"); throw new AssertionError("Public symlink escape"); }
            catch (SecurityException expected) { }
            require(Environment.MEDIA_UNKNOWN.equals(Environment.getExternalStorageState(outside.toFile())), "Unknown volume state");
            try { Environment.isExternalStorageEmulated(outside.toFile()); throw new AssertionError("Outside path emulation accepted"); }
            catch (IllegalArgumentException expected) { }
            try { storage.externalFiles("../../escape"); throw new AssertionError("Parent traversal accepted"); }
            catch (SecurityException expected) { }
            try { storage.externalFiles("bad\u0000type"); throw new AssertionError("NUL type accepted"); }
            catch (IllegalArgumentException expected) { }
            System.out.println("HOST_STORAGE_FAILURE_PATHS_OK");
        } finally {
            Environment.shirazShutdown();
            try { Environment.getExternalStorageState(); throw new AssertionError("Closed storage accepted"); }
            catch (IllegalStateException expected) { }
            try (Stream<Path> paths = Files.walk(root)) {
                java.util.Iterator<Path> remaining=paths.sorted(Comparator.reverseOrder()).iterator();
                while(remaining.hasNext())Files.delete(remaining.next());
            }
        }
    }
}
