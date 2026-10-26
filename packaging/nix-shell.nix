# SPDX-License-Identifier: GPL-3.0-only
# Isolated native build dependencies; does not alter NixOS configuration.
{ pkgs ? import <nixpkgs> {} }:
let
  desktop = with pkgs; [ wayland libxkbcommon libx11 libxext libxcursor libxi libxfixes libxrandr systemd libglvnd ];
  libraries = (with pkgs; [ cairo fontconfig libsndfile dbus SDL2 libbsd libmd libunwind vixl elfutils valgrind icu openssl expat lz4 xz libcap zlib alsa-lib pipewire sqlite freetype harfbuzz libpng libjpeg_turbo libwebp ]) ++ desktop;
in pkgs.mkShell {
  packages = with pkgs; [ gcc gnumake git ripgrep patchelf gettext python3 jdk17 meson ninja pkg-config autoconf automake libtool cmake zip unzip patch which bison flex perl rsync curl cacert wayland-scanner ];
  buildInputs = libraries;
  SHIRAZ_REQUIRE_WAYLAND = "1";
  C_INCLUDE_PATH = "${pkgs.lib.getDev pkgs.vixl}/include";
  SHIRAZ_SYSTEM_LIBRARY_PATH = pkgs.lib.makeLibraryPath (libraries ++ [ pkgs.stdenv.cc.cc.lib pkgs.glibc ]);
  SHIRAZ_SYSTEM_DATA_PATH = pkgs.lib.makeSearchPath "share" [ pkgs.alsa-lib pkgs.pipewire pkgs.xkeyboard_config ];
}
