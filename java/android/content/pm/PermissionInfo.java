// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
/** Declared permission identity and protection metadata; does not grant access. */
public class PermissionInfo extends PackageItemInfo {
 public static final int PROTECTION_NORMAL=0,PROTECTION_DANGEROUS=1,PROTECTION_SIGNATURE=2,PROTECTION_MASK_BASE=15;
 public int protectionLevel;
 public int getProtection(){return protectionLevel&PROTECTION_MASK_BASE;}
 public int getProtectionFlags(){return protectionLevel&~PROTECTION_MASK_BASE;}
}
