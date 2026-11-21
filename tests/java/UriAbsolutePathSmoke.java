// SPDX-License-Identifier: GPL-3.0-only
import android.net.Uri;
public final class UriAbsolutePathSmoke {
 private static void check(String expected,Uri value){if(!expected.equals(value.toString()))throw new AssertionError(expected+" != "+value);}
 public static void main(String[] args){
  Uri module=new Uri.Builder().scheme("content").authority("com.google.android.gms.chimera").path("api/com.google.android.gms.googlecertificates").build();
  check("content://com.google.android.gms.chimera/api/com.google.android.gms.googlecertificates",module);
  check(module.toString()+"?requestStartTime=7",new Uri.Builder().scheme("content").authority("com.google.android.gms.chimera").path("api").appendPath("com.google.android.gms.googlecertificates").appendQueryParameter("requestStartTime","7").build());
  if(module.getPathSegments().size()!=2||!"com.google.android.gms.googlecertificates".equals(module.getPathSegments().get(1))||!"com.google.android.gms.chimera".equals(module.getAuthority()))throw new AssertionError("Authority/path boundary");
  check("//host/a%2Fb/c",new Uri.Builder().authority("host").encodedPath("a%2Fb/c").build());
  check("custom:/relative",new Uri.Builder().scheme("custom").path("relative").build());
  check("relative/path",new Uri.Builder().path("relative/path").build());
  check("content://host",new Uri.Builder().scheme("content").authority("host").build());
  check("content://host/",new Uri.Builder().scheme("content").authority("host").path("/").build());
  check("mailto:user@example.test",new Uri.Builder().scheme("mailto").encodedOpaquePart("user@example.test").build());
  Uri.Builder reused=new Uri.Builder().path("relative");check("custom:/relative",reused.scheme("custom").build());check("relative",reused.scheme(null).build());
  check(module.toString()+"?k=v#f",module.buildUpon().appendQueryParameter("k","v").fragment("f").build());
  System.out.println("URI_ABSOLUTE_PATH_OK");
 }
}
