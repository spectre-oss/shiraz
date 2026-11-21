// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
/** Logical package ownership is checked by the trusted host before termination. */
public final class HostProcesses {
 private HostProcesses(){}
 public static void terminate(int pid){
  if(pid<=0)throw new IllegalArgumentException("Invalid process ID");
  ServiceBrokerClient broker=RuntimeBroker.client();
  if(broker==null)throw new SecurityException("Process termination requires host supervision");
  try{broker.terminateProcess(pid);}catch(java.io.IOException failure){throw new IllegalStateException("Process termination failed",failure);}
 }
}
