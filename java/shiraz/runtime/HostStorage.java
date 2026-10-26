// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;

/** Writable application directories on one host-backed storage volume. */
public final class HostStorage {
    private final File data;
    private final String packageName;
    public HostStorage(File data, String packageName) {
        if (packageName == null || !packageName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")) throw new IllegalArgumentException("Invalid storage package");
        this.packageName = packageName;
        try { this.data = data.getCanonicalFile(); }
        catch (IOException error) { throw new IllegalStateException("Cannot resolve application data directory", error); }
    }
    private File directory(String name, String type, boolean nullable) {
        File base = new File(data, name);
        if (type != null && (type.indexOf('\0') >= 0 || new File(type).isAbsolute())) throw new IllegalArgumentException("Invalid or absolute storage type");
        try {
            File canonicalBase = base.getCanonicalFile();
            if (!canonicalBase.toPath().startsWith(data.toPath())) throw new SecurityException("Storage directory escapes app data");
            File result = (type == null ? base : new File(base, type)).getCanonicalFile();
            if (!result.toPath().startsWith(canonicalBase.toPath())) throw new SecurityException("Storage type escapes its directory");
            Files.createDirectories(result.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            // Restrict existing leaf directories too; this runtime does not create public host folders.
            Files.setPosixFilePermissions(result.toPath(), PosixFilePermissions.fromString("rwx------"));
            return result;
        } catch (IOException error) {
            if (nullable) return null;
            throw new IllegalStateException("Application directory unavailable", error);
        }
    }
    public File data() { return directory("", null, false); }
    public File notificationPolicy() { return new File(directory("notification-policy", null, false), "channels.db"); }
    public File files() { return directory("files", null, false); }
    public File filePath(String name) {
        if(name==null)throw new NullPointerException("File name");
        if(name.isEmpty()||name.equals(".")||name.equals("..")||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf('\0')>=0)throw new IllegalArgumentException("File name contains a path or is invalid");
        File base=files(),candidate=new File(base,name);
        if(Files.isSymbolicLink(candidate.toPath()))throw new SecurityException("Internal file path is a symbolic link");
        try{File result=candidate.getCanonicalFile();if(!result.toPath().startsWith(base.toPath()))throw new SecurityException("File path escapes internal files directory");return result;}
        catch(IOException error){throw new IllegalStateException("Cannot resolve internal file path",error);}
    }
    public File cache() { return directory("cache", null, false); }
    public File codeCache() { return directory("code_cache", null, false); }
    public File noBackup() { return directory("no_backup", null, false); }
    public File named(String name, int mode) {
        if (mode != 0) throw new UnsupportedOperationException("Only MODE_PRIVATE directories are supported");
        if (name == null) throw new NullPointerException("Directory name");
        if (name.indexOf('/') >= 0 || name.indexOf('\0') >= 0) throw new IllegalArgumentException("Directory name contains a separator or NUL");
        return directory("app_" + name, null, false);
    }
    public File databasePath(String name) {
        if (name == null) throw new NullPointerException("Database name");
        if (name.isEmpty() || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid database name");
        File candidate;
        if (new File(name).isAbsolute()) candidate = new File(name);
        else {
            if (name.indexOf('/') >= 0 || name.equals(".") || name.equals("..")) throw new IllegalArgumentException("Database name contains a path");
            candidate = new File(directory("databases", null, false), name);
        }
        try { File canonical = candidate.getCanonicalFile(); if (!canonical.toPath().startsWith(data.toPath())) throw new SecurityException("Database path escapes application data"); return canonical; }
        catch (IOException error) { throw new IllegalStateException("Cannot resolve database path", error); }
    }
    public String[] databases() { String[] files = directory("databases", null, false).list(); if (files == null) throw new IllegalStateException("Cannot list databases"); return files; }
    public File externalFiles(String type) { return directory("external/Android/data/" + packageName + "/files", type, true); }
    public File externalCache() { return directory("external/Android/data/" + packageName + "/cache", null, true); }
    public File obb() { return directory("external/Android/obb/" + packageName, null, true); }
    public File media() { return directory("external/Android/media/" + packageName, null, true); }
    public File preparePrimary() { return directory("external", null, true); }
    public File primaryPath(String type) {
        if (type != null && (type.indexOf('\0') >= 0 || new File(type).isAbsolute())) throw new IllegalArgumentException("Invalid storage path");
        try {
            File root = new File(data, "external").getCanonicalFile();
            if (!root.toPath().startsWith(data.toPath())) throw new SecurityException("Primary volume escapes app data");
            File path = (type == null ? root : new File(root, type)).getCanonicalFile();
            if (!path.toPath().startsWith(root.toPath())) throw new SecurityException("Public storage type escapes primary volume");
            return path;
        } catch (IOException error) { throw new IllegalStateException("Cannot resolve primary volume path", error); }
    }
    public boolean onPrimary(File path) {
        if (path == null) throw new NullPointerException("Storage path");
        try { return path.getCanonicalFile().toPath().startsWith(primaryPath(null).toPath()); }
        catch (IOException error) { throw new IllegalArgumentException("Cannot resolve storage path", error); }
    }
}
