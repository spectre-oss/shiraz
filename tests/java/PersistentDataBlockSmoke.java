// SPDX-License-Identifier: GPL-3.0-only
import android.content.Context;
import android.content.ContextWrapper;
import android.service.persistentdata.PersistentDataBlockManager;
public final class PersistentDataBlockSmoke {
 public static void main(String[] args)throws Exception{
  Context absent=new ContextWrapper(null){
   public Object getSystemService(String name){
    if(!Context.PERSISTENT_DATA_BLOCK_SERVICE.equals(name))throw new AssertionError("Wrong service name");
    return null;
   }
  };
  if(!"persistent_data_block".equals(absent.getSystemServiceName(PersistentDataBlockManager.class)))throw new AssertionError("Class lookup");
  if(absent.getSystemService(PersistentDataBlockManager.class)!=null)throw new AssertionError("Invented backend");
  // Keep the bootloader status ABI distinct from availability of the service.
  if(PersistentDataBlockManager.FLASH_LOCK_UNKNOWN!=-1||PersistentDataBlockManager.FLASH_LOCK_UNLOCKED!=0||PersistentDataBlockManager.FLASH_LOCK_LOCKED!=1)throw new AssertionError("Status constants");
  if(PersistentDataBlockManager.class.getConstructors().length!=0)throw new AssertionError("Public construction without backend");
  PersistentDataBlockManager.class.getMethod("write",byte[].class);
  PersistentDataBlockManager.class.getMethod("read");
  PersistentDataBlockManager.class.getMethod("getMaximumDataBlockSize");
  System.out.println("PERSISTENT_DATA_BLOCK_ABSENT_OK");
 }
}
