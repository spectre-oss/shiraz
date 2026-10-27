#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Bundle the verified private Shiraz runtime into an amd64 Debian package.

The runtime root is a prepared Linux build with the native libraries needed by
Shiraz. No APK, app data, build log, or compiler object is copied into the deb.
"""

import argparse
import os
from pathlib import Path
import shutil
import subprocess


def copy(source: Path, destination: Path):
    if not source.exists():
        raise SystemExit(f"missing required runtime file: {source}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    if source.is_dir():
        shutil.copytree(source, destination, symlinks=True)
    else:
        shutil.copy2(source, destination, follow_symlinks=False)


def write(path: Path, content: str, mode=0o644):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    path.chmod(mode)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--runtime-root", type=Path, required=True)
    parser.add_argument("--private-python", type=Path, required=True)
    parser.add_argument("--revision", type=int, default=1)
    parser.add_argument("--system-ffmpeg", action="store_true",
                        help="depend on Debian's ffmpeg package instead of a private codec bundle")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    source = args.source_root.resolve()
    runtime = args.runtime_root.resolve()
    python = args.private_python.resolve()
    version = (source / "VERSION").read_text(encoding="ascii").strip()
    if args.revision < 1:
        parser.error("revision must be positive")
    stage = args.output.parent.resolve() / "shiraz-deb-stage"
    shutil.rmtree(stage, ignore_errors=True)
    app = stage / "opt/shiraz"
    copy(source / "VERSION", app / "VERSION")
    copy(source / "src/shiraz", app / "src/shiraz")
    for name in ("bin", "lib64", "framework"):
        copy(runtime / "context/art_standalone/out/host/linux-x86" / name,
             app / "context/art_standalone/out/host/linux-x86" / name)
    for name in ("shiraz", "espeak-ng-data", "bionic_translation"):
        copy(runtime / ".local/share" / name, app / ".local/share" / name)
    copy(runtime / ".local/lib", app / ".local/lib")
    if not args.system_ffmpeg:
        copy(runtime / ".local/system-codecs/usr/lib", app / ".local/system-codecs/usr/lib")
        copy(runtime / ".local/system-codecs/usr/bin/ffmpeg", app / ".local/system-codecs/usr/bin/ffmpeg")
    copy(python / "bin/python3", app / "python/bin/python3")
    copy(python / "lib", app / "python/lib")
    copy(python / "cacerts", app / "python/cacerts")
    build = app / "build"
    for item in (runtime / "build").iterdir():
        if item.is_file() or item.is_symlink():
            if item.name.startswith("lib") and ".so" in item.name:
                copy(item, build / item.name)
    copy(source / "build/shiraz-framework.jar", build / "shiraz-framework.jar")
    copy(source / "build/framework-res/framework-res.apk", build / "framework-res/framework-res.apk")
    copy(source / "build/shiraz-web", build / "shiraz-web")
    write(app / "bin/python3", '#!/bin/sh\nexec /opt/shiraz/python/lib/ld-linux-x86-64.so.2 --library-path /opt/shiraz/python/lib /opt/shiraz/python/bin/python3 "$@"\n', 0o755)
    if not args.system_ffmpeg:
        write(app / "bin/ffmpeg", '#!/bin/sh\nexec /opt/shiraz/python/lib/ld-linux-x86-64.so.2 --library-path /opt/shiraz/.local/system-codecs/usr/lib:/opt/shiraz/.local/lib:/opt/shiraz/python/lib /opt/shiraz/.local/system-codecs/usr/bin/ffmpeg "$@"\n', 0o755)
    write(app / "config/fonts.conf", '<?xml version="1.0"?>\n<fontconfig>\n  <dir>/opt/shiraz/.local/share/shiraz/fonts</dir>\n  <cachedir>/opt/shiraz/build/fontconfig-cache</cachedir>\n</fontconfig>\n')
    ffmpeg_path = "/usr/bin/ffmpeg" if args.system_ffmpeg else "/opt/shiraz/bin/ffmpeg"
    write(stage / "usr/bin/shiraz-web", f'#!/bin/sh\nexport PATH=/opt/shiraz/bin:/usr/bin:/bin\nexport SHIRAZ_RUNTIME_LOADER=/opt/shiraz/python/lib/ld-linux-x86-64.so.2\nexport SHIRAZ_JAVA_TRUST_STORE=/opt/shiraz/python/cacerts\nexport SHIRAZ_FFMPEG={ffmpeg_path}\nexport FONTCONFIG_FILE=/opt/shiraz/config/fonts.conf\nexec /opt/shiraz/python/lib/ld-linux-x86-64.so.2 --library-path /opt/shiraz/python/lib /opt/shiraz/build/shiraz-web -root /opt/shiraz "$@"\n', 0o755)
    write(stage / "lib/systemd/system/shiraz-web.service", """[Unit]
Description=Shiraz APK web portal
After=network.target

[Service]
Type=simple
User=shiraz
Group=shiraz
WorkingDirectory=/opt/shiraz
ExecStart=/usr/bin/shiraz-web -listen 127.0.0.1:8080
Restart=on-failure
RestartSec=3
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
""")
    depends = "adduser, libc6, systemd" + (", ffmpeg" if args.system_ffmpeg else "")
    write(stage / "DEBIAN/control", f"""Package: shiraz-web
Version: {version}-{args.revision}
Section: misc
Priority: optional
Architecture: amd64
Maintainer: SpectreOSS <https://github.com/spectre-oss/shiraz>
Depends: {depends}
Description: Experimental Shiraz Android APK web portal
 Bundled Shiraz ART, Python, media runtime, and native libraries.
 The service listens on localhost:8080. Android app support is experimental.
""")
    write(stage / "DEBIAN/postinst", """#!/bin/sh
set -e
if ! getent passwd shiraz >/dev/null; then
  adduser --system --group --home /opt/shiraz --no-create-home shiraz
fi
chown shiraz:shiraz /opt/shiraz /opt/shiraz/build
install -d -o shiraz -g shiraz -m 0700 /opt/shiraz/data /opt/shiraz/build/runs
if [ -d /run/systemd/system ]; then
  systemctl daemon-reload
  systemctl enable --now shiraz-web.service
fi
""", 0o755)
    write(stage / "DEBIAN/prerm", """#!/bin/sh
set -e
if [ -d /run/systemd/system ]; then
  systemctl stop shiraz-web.service || true
fi
""", 0o755)
    write(stage / "DEBIAN/postrm", """#!/bin/sh
set -e
if [ -d /run/systemd/system ]; then systemctl daemon-reload; fi
""", 0o755)
    write(stage / "usr/share/doc/shiraz-web/README.Debian", """Shiraz bundles a private ART, Python, and media runtime under /opt/shiraz.
The systemd service listens only on 127.0.0.1:8080. Do not expose the upload
endpoint without authentication and TLS. The application data directory is
/opt/shiraz/data and is intentionally retained when the package is removed.
This is an experimental build for x86-64 Debian 12 and 13.
""")
    notices = stage / "usr/share/doc/shiraz-web/licenses"
    copy(source / "LICENSE", notices / "Shiraz-GPL-3.0.txt")
    copy(source / "LICENSES", notices / "Shiraz-notices")
    copy(source / "packaging/licenses/LGPL-2.1-only.txt", notices / "LGPL-2.1-only.txt")
    copy(source / "packaging/licenses/GPL-2.0-only.txt", notices / "GPL-2.0-only.txt")
    if not args.system_ffmpeg:
        copy(runtime / ".local/system-codecs/usr/share/licenses", notices / "media-libraries")
    python_licenses = sorted((python / "lib").glob("python3.*/LICENSE.txt"))
    if len(python_licenses) != 1:
        raise SystemExit("private Python must contain exactly one LICENSE.txt")
    copy(python_licenses[0], notices / "Python-LICENSE.txt")
    art_root = source / "context/art_standalone"
    for directory, subdirs, files in os.walk(art_root):
        subdirs[:] = [name for name in subdirs if name not in ("out", ".git")]
        for name in files:
            item = Path(directory) / name
            relative = item.relative_to(art_root)
            if len(relative.parts) <= 5 and name.upper().startswith(("LICENSE", "NOTICE", "COPYING")):
                copy(item, notices / "ART" / relative)
    copy(source / "context/bionic_translation/linker/NOTICE", notices / "Bionic-linker-NOTICE")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["dpkg-deb", "--build", "--root-owner-group", "-Zzstd", "-z8", str(stage), str(args.output)], check=True)
    print(args.output)


if __name__ == "__main__":
    main()
