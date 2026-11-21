// SPDX-License-Identifier: GPL-3.0-only
import android.app.usage.StorageStatsManager;
import android.os.storage.StorageManager;
import shiraz.runtime.HostStorage;
import java.nio.file.*;
import java.util.UUID;
public final class StorageStatsSmoke {
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("storage-stats-");
        try {
            StorageStatsManager manager=StorageStatsManager.shirazForStorage(new HostStorage(root.toFile(),"test.storage"));
            UUID id=StorageManager.UUID_DEFAULT;
            long total=manager.getTotalBytes(id),free=manager.getFreeBytes(id);
            if(total!=root.toFile().getTotalSpace()||total<=0||free<0||free>total||manager.isQuotaSupported(id))throw new AssertionError("Invalid host statistics");
            try{manager.getFreeBytes(UUID.randomUUID());throw new AssertionError("Unknown volume accepted");}catch(java.io.IOException expected){}
            try{manager.getTotalBytes(null);throw new AssertionError("Null volume accepted");}catch(NullPointerException expected){}
            System.out.println("STORAGE_STATS_OK");
        } finally {Files.delete(root);}
    }
}
