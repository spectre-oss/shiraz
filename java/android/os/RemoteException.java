// SPDX-License-Identifier: GPL-3.0-only
package android.os;
public class RemoteException extends android.util.AndroidException {
 public RemoteException(){}public RemoteException(String message){super(message);}
 public RuntimeException rethrowAsRuntimeException(){throw new RuntimeException(this);}
 public RuntimeException rethrowFromSystemServer(){throw new UnsupportedOperationException("Remote system-server exception transport is not implemented",this);}
}
