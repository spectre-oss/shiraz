// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
/** Download callback for the placeholder WebView. */
public interface DownloadListener {
 void onDownloadStart(String url,String userAgent,String contentDisposition,String mimeType,long contentLength);
}
