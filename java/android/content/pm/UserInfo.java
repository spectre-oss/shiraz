// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

/** User metadata returned by user/profile queries. Profiles are not yet supported. */
public class UserInfo {
 public int id;
 public String name;
 public int flags;
 public UserInfo(int id,String name,int flags){this.id=id;this.name=name;this.flags=flags;}
 public android.os.UserHandle getUserHandle(){return android.os.UserHandle.of(id);}
}
