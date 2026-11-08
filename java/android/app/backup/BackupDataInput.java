// SPDX-License-Identifier: GPL-3.0-only
package android.app.backup;
import java.io.FileDescriptor;
import java.io.IOException;
public class BackupDataInput {
    public BackupDataInput(FileDescriptor descriptor){if(descriptor==null)throw new NullPointerException("descriptor");throw new UnsupportedOperationException("Android backup transport unavailable");}
    public boolean readNextHeader()throws IOException{throw new IOException("Android backup transport unavailable");}
    public String getKey(){throw new IllegalStateException("No backup entity header");}
    public int getDataSize(){throw new IllegalStateException("No backup entity header");}
    public int readEntityData(byte[] data,int offset,int size)throws IOException{throw new IOException("Android backup transport unavailable");}
    public void skipEntityData()throws IOException{throw new IOException("Android backup transport unavailable");}
}
