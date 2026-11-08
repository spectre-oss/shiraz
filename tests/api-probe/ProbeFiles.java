// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import java.io.*;
import org.json.JSONObject;

final class ProbeFiles {
  static void write(File file, JSONObject value) throws Exception {
    File pending = new File(file.getPath() + ".pending");
    try (FileOutputStream out = new FileOutputStream(pending)) {
      out.write(value.toString(2).getBytes("UTF-8"));
      out.getFD().sync();
    }
    if (!pending.renameTo(file))
      throw new IOException("atomic report rename failed");
  }
  static JSONObject read(File file) throws Exception {
    if (file.length() > 2 * 1024 * 1024)
      throw new IOException("report exceeds 2 MiB");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (InputStream in = new FileInputStream(file)) {
      byte[] buffer = new byte[4096];
      int n;
      while ((n = in.read(buffer)) != -1) {
        if (out.size() + n > 2 * 1024 * 1024)
          throw new IOException("report exceeds limit");
        out.write(buffer, 0, n);
      }
    }
    return new JSONObject(out.toString("UTF-8"));
  }
  static File result(android.content.Context context, String token) {
    if (token == null || !token.matches("[a-f0-9-]{36}"))
      throw new IllegalArgumentException("bad token");
    return new File(context.getFilesDir(), "probe-" + token + ".json");
  }
}
