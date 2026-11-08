// SPDX-License-Identifier: GPL-3.0-only
package android.app.backup;
import java.io.InputStream;
import java.io.IOException;
public class BackupDataInputStream extends InputStream {
    private final BackupDataInput input;
    BackupDataInputStream(BackupDataInput input){this.input=input;}
    public String getKey(){return input.getKey();}
    public int size(){return input.getDataSize();}
    public int read()throws IOException{byte[] value=new byte[1];int count=read(value,0,1);return count<=0?-1:value[0]&255;}
    public int read(byte[] data,int offset,int count)throws IOException{return input.readEntityData(data,offset,count);}
}
