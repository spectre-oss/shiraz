// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.io.Closeable;
import java.io.IOException;
import android.os.ParcelFileDescriptor;
/** Internal private socketpair transport. Callers serialize each direction and own socket lifetime. */
public final class NativeIpc {
    static { System.loadLibrary("shiraz-host"); }
    private NativeIpc() { }
    /** Both returned descriptors are owned by the caller and close-on-exec. */
    public static native int[] pair() throws IOException;
    /** Spawn an absolute executable with only stdio and the supplied socket (FD 3) inherited. */
    public static int spawn(String[] command,int socket) throws IOException {
        if(command==null||command.length==0||command.length>256)throw new IllegalArgumentException("Child command limit");
        byte[][] encoded=new byte[command.length][];int bytes=0;
        for(int i=0;i<command.length;i++){
            if(command[i]==null||command[i].indexOf('\0')>=0)throw new IllegalArgumentException("Invalid child argument");
            encoded[i]=command[i].getBytes(java.nio.charset.StandardCharsets.UTF_8);bytes+=encoded[i].length;
            if(bytes>1024*1024)throw new IllegalArgumentException("Child argument byte limit");
        }
        if(!command[0].startsWith("/"))throw new IllegalArgumentException("Child executable must be absolute");
        return spawnNative(encoded,socket);
    }
    private static native int spawnNative(byte[][] command,int socket) throws IOException;
    /** Reap a child, returning its exit code or 128 + terminating signal. */
    public static native int waitFor(int pid) throws IOException;
    /** Descriptor arguments are borrowed for this call; SCM_RIGHTS duplicates them for the receiver. */
    public static native void send(int socket, byte[] bytes, int[] descriptors) throws IOException;
    public static native Packet receive(int socket) throws IOException;
    /** Wake blocked reads/writes before closing a channel; does not close its descriptor. */
    public static native void shutdown(int socket);
    public static final class Packet implements Closeable {
        public final byte[] bytes;
        public final int senderPid,senderUid;
        private final int[] descriptors;
        private Packet(byte[] bytes, int[] descriptors,int pid,int uid) { this.bytes=bytes; this.descriptors=descriptors;senderPid=pid;senderUid=uid; }
        public int descriptorCount() { return descriptors.length; }
        public synchronized ParcelFileDescriptor takeDescriptor(int index) {
            int raw=descriptors[index];
            if(raw<0)throw new IllegalStateException("Packet descriptor already taken or closed");
            ParcelFileDescriptor result=ParcelFileDescriptor.adoptFd(raw);descriptors[index]=-1;return result;
        }
        @Override public synchronized void close() {
            for(int i=0;i<descriptors.length;i++)if(descriptors[i]>=0){NativeFd.closeRawQuietly(descriptors[i]);descriptors[i]=-1;}
        }
    }
}
