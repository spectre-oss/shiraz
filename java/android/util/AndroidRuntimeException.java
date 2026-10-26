// SPDX-License-Identifier: GPL-3.0-only
package android.util;
public class AndroidRuntimeException extends RuntimeException {
 public AndroidRuntimeException(){}public AndroidRuntimeException(String message){super(message);}
 public AndroidRuntimeException(String message,Throwable cause){super(message,cause);}public AndroidRuntimeException(Exception cause){super(cause);}
}
