// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
/** Manifest declarations only; Shiraz does not execute instrumentation runners. */
public class InstrumentationInfo extends PackageItemInfo {
 public String targetPackage,targetProcesses,sourceDir,publicSourceDir,dataDir;
 public String[] splitNames,splitSourceDirs,splitPublicSourceDirs;
 public boolean handleProfiling,functionalTest;
}
