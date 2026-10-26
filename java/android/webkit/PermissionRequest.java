// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
/** Web permission request for the placeholder WebView. */
public class PermissionRequest {
 public static final String RESOURCE_AUDIO_CAPTURE="android.webkit.resource.AUDIO_CAPTURE";
 public static final String RESOURCE_VIDEO_CAPTURE="android.webkit.resource.VIDEO_CAPTURE";
 public static final String RESOURCE_PROTECTED_MEDIA_ID="android.webkit.resource.PROTECTED_MEDIA_ID";
 public android.net.Uri getOrigin(){return android.net.Uri.EMPTY;}
 public String[] getResources(){return new String[0];}
 public void grant(String[] resources){}
 public void deny(){}
}
