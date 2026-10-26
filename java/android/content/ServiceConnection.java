// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.os.IBinder;

/** Connection callbacks; service hosts are responsible for dispatching them on the main thread. */
public interface ServiceConnection {
 void onServiceConnected(ComponentName name,IBinder service);
 void onServiceDisconnected(ComponentName name);
 default void onNullBinding(ComponentName name){}
 default void onBindingDied(ComponentName name){}
}
