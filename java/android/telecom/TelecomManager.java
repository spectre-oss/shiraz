// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;
import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
/** Account registry only; no system call UI or audio routing exists on the host. */
public class TelecomManager {
 private final List<PhoneAccount> accounts=new ArrayList<PhoneAccount>();
 public void registerPhoneAccount(PhoneAccount account){if(account==null)throw new NullPointerException("account");synchronized(accounts){remove(account.getAccountHandle());accounts.add(account);}}
 public void unregisterPhoneAccount(PhoneAccountHandle handle){synchronized(accounts){remove(handle);}}
 private void remove(PhoneAccountHandle handle){for(int i=accounts.size()-1;i>=0;i--)if(accounts.get(i).getAccountHandle().equals(handle))accounts.remove(i);}
 public PhoneAccount getPhoneAccount(PhoneAccountHandle handle){synchronized(accounts){for(PhoneAccount a:accounts)if(a.getAccountHandle().equals(handle))return a;}return null;}
 public void addNewIncomingCall(PhoneAccountHandle handle,Bundle extras){throw new UnsupportedOperationException("Host call UI is not implemented");}
 public void placeCall(Uri address,Bundle extras){throw new UnsupportedOperationException("Host call UI is not implemented");}
}
