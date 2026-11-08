// SPDX-License-Identifier: GPL-3.0-only
package android.app.backup;
import java.io.FileDescriptor;
import java.io.IOException;
public class BackupDataOutput {
    public BackupDataOutput(FileDescriptor descriptor){this(descriptor,-1);}
    public BackupDataOutput(FileDescriptor descriptor,long quota){if(descriptor==null)throw new NullPointerException("descriptor");throw new UnsupportedOperationException("Android backup transport unavailable");}
    public int writeEntityHeader(String key,int size)throws IOException{throw new IOException("Android backup transport unavailable");}
    public int writeEntityData(byte[] data,int size)throws IOException{throw new IOException("Android backup transport unavailable");}
    public long getQuota(){throw new UnsupportedOperationException("Android backup transport unavailable");}
}
