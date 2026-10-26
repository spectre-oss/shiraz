// SPDX-License-Identifier: GPL-3.0-only
package android.os;
public class OperationCanceledException extends RuntimeException {
 public OperationCanceledException(){this(null);}
 public OperationCanceledException(String message){super(message!=null?message:"The operation has been canceled.");}
}
