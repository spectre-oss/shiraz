// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
/** Exit boundary for the dedicated APK process, never for an embedding host JVM. */
public final class DedicatedProcess {
 public interface Task {void run() throws Throwable;}
 private DedicatedProcess(){}
 public static void run(Task body,Task cleanup){
  Throwable failure=null;
  try{body.run();}catch(Throwable error){failure=error;}
  try{cleanup.run();}catch(Throwable error){if(failure==null)failure=error;else if(error!=failure)failure.addSuppressed(error);}
  if(failure==null){System.out.println("SHIRAZ_RUNTIME_CLEANUP_COMPLETE");System.exit(0);return;}
  // The main event loop cannot continue. Report the original error rather than
  // letting an app handler return into a process kept alive by unrelated workers.
  try{System.err.println("SHIRAZ_RUNTIME_FATAL");failure.printStackTrace(System.err);System.err.flush();System.out.flush();}
  finally{Runtime.getRuntime().halt(10);}
 }
}
