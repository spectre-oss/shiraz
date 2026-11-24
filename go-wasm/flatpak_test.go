// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/zip"
	"bytes"
	"crypto/sha256"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"os"
	"strings"
	"testing"
)

func syntheticFlatpakBase(t *testing.T, arch string) []byte {
	t.Helper()
	part0 := newDeltaPartBuilder()
	dirmeta := part0.addMeta(ostreeObjectDirMeta, ostreeDirmetaBytes(ostreeModeIFDIR|0o755))
	runFile := part0.addFile(ostreeModeIFREG|0o755, []byte("#!/bin/sh\nexit 0\n"))
	binTree := part0.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes([]ostreeFileEntry{{Name: "shiraz-flatpak-run", Digest: runFile}}, nil))
	appFile := part0.addFile(ostreeModeIFREG|0o755, testELF(62))
	usrBinTree := part0.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes([]ostreeFileEntry{{Name: "shiraz-app", Digest: appFile}}, nil))
	libFile := part0.addFile(ostreeModeIFREG|0o644, []byte("lib"))
	usrLibTree := part0.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes([]ostreeFileEntry{{Name: "libdemo.so", Digest: libFile}}, nil))
	artFile := part0.addFile(ostreeModeIFREG|0o644, []byte("art"))
	usrArtTree := part0.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes([]ostreeFileEntry{{Name: "README", Digest: artFile}}, nil))
	verFile := part0.addFile(ostreeModeIFREG|0o644, []byte("0.360.0\n"))

	p0 := part0.finishUncompressed()
	idx := flatpakBaseIndex{
		Version:           "0.360.0",
		Architecture:      arch,
		RuntimeBranch:     "24.08",
		RuntimeRepo:       "https://flathub.org/repo/flathub.flatpakrepo",
		Dirmeta0755SHA256: hex.EncodeToString(dirmeta[:]),
		InstalledSize:     1024,
		Part0: flatpakPart0Index{
			CompressedSize:   p0.CompressedSize,
			UncompressedSize: p0.UncompressedSize,
			SHA256:           hex.EncodeToString(p0.SHA256[:]),
			ObjectsB64:       base64.StdEncoding.EncodeToString(p0.Objects),
		},
		FilesRoot: flatpakFilesRootIndex{
			Bin:                       flatpakDirRef{Tree: hex.EncodeToString(binTree[:]), Meta: hex.EncodeToString(dirmeta[:])},
			UsrBin:                    flatpakDirRef{Tree: hex.EncodeToString(usrBinTree[:]), Meta: hex.EncodeToString(dirmeta[:])},
			UsrLib:                    flatpakDirRef{Tree: hex.EncodeToString(usrLibTree[:]), Meta: hex.EncodeToString(dirmeta[:])},
			UsrArt:                    flatpakDirRef{Tree: hex.EncodeToString(usrArtTree[:]), Meta: hex.EncodeToString(dirmeta[:])},
			UsrShareShirazStaticFiles: [][]string{{"VERSION", hex.EncodeToString(verFile[:])}},
			UsrShareShirazStaticDirs:  [][]string{},
			UsrShareOtherDirs:         [][]string{},
		},
	}
	idxJSON, err := json.Marshal(idx)
	if err != nil {
		t.Fatal(err)
	}
	var out bytes.Buffer
	out.WriteString(flatpakBaseMagic)
	_ = binary.Write(&out, binary.LittleEndian, uint32(flatpakBaseVersion))
	_ = binary.Write(&out, binary.LittleEndian, uint32(len(idxJSON)))
	out.Write(idxJSON)
	out.Write(p0.PartBytes)
	return out.Bytes()
}

func TestFlatpakAppIDSanitization(t *testing.T) {
	cases := map[string]string{
		"chat.delta":          "io.github.spectre_oss.shiraz.chat_delta",
		"org.shiraz.fixture":  "io.github.spectre_oss.shiraz.org_shiraz_fixture",
		"123.weird-pkg.app":   "io.github.spectre_oss.shiraz.p123_weird_pkg_app",
		"com.example.foo_bar": "io.github.spectre_oss.shiraz.com_example_foo_bar",
	}
	for pkg, want := range cases {
		if got := flatpakAppID(pkg); got != want {
			t.Fatalf("flatpakAppID(%q) = %q, want %q", pkg, got, want)
		}
	}
	longPkg := "com." + strings.Repeat("segment.", 40) + "app"
	gotLong := flatpakAppID(longPkg)
	if len(gotLong) > 255 || !strings.HasPrefix(gotLong, flatpakAppIDPrefix+".") {
		t.Fatalf("long app id invalid: len=%d val=%q", len(gotLong), gotLong)
	}
}

func TestFlatpakBaseValidation(t *testing.T) {
	valid := syntheticFlatpakBase(t, "x86_64")
	if _, err := parseFlatpakBase(valid); err != nil {
		t.Fatalf("valid base rejected: %v", err)
	}
	corrupted := append([]byte(nil), valid...)
	corrupted[len(corrupted)-1] ^= 0xff
	if _, err := parseFlatpakBase(corrupted); err == nil {
		t.Fatal("accepted base with tampered Part 0 payload")
	}
	if _, err := parseFlatpakBase([]byte("\x7fELFnotflatpak")); err == nil {
		t.Fatal("accepted ELF bytes as Flatpak base")
	}
}

func TestBuildFlatpakFromFixtureAPK(t *testing.T) {
	apkBytes, err := os.ReadFile("../build/apk-fixture/fixture.apk")
	if err != nil {
		t.Skip("fixture.apk not built yet:", err)
	}
	for _, tc := range []struct {
		arch    string
		abi     string
		machine byte
	}{
		{"x86_64", "x86_64", 62},
		{"aarch64", "arm64-v8a", 183},
	} {
		t.Run(tc.arch, func(t *testing.T) {
			base := syntheticFlatpakBase(t, tc.arch)
			// Augment fixture APK with a valid PNG icon and architecture-matched native .so.
			origZip, err := zip.NewReader(bytes.NewReader(apkBytes), int64(len(apkBytes)))
			if err != nil {
				t.Fatal(err)
			}
			var apkBuf bytes.Buffer
			zw := zip.NewWriter(&apkBuf)
			for _, f := range origZip.File {
				r, err := f.Open()
				if err != nil {
					t.Fatal(err)
				}
				w, err := zw.Create(f.Name)
				if err != nil {
					t.Fatal(err)
				}
				_, _ = w.Write(mustReadAll(t, r))
				r.Close()
			}
			so, _ := zw.Create("lib/" + tc.abi + "/libprobe.so")
			_, _ = so.Write(testELF(tc.machine))
			icon, _ := zw.Create("res/mipmap-xxhdpi/ic_launcher.png")
			pngHeader := append([]byte("\x89PNG\r\n\x1a\n"), bytes.Repeat([]byte{0x42}, 32)...)
			_, _ = icon.Write(pngHeader)
			if err := zw.Close(); err != nil {
				t.Fatal(err)
			}

			online, err := buildFlatpakFromApk(base, apkBuf.Bytes(), "0.360.0", flatpakBuildOptions{
				AllowNetwork: true,
				Timestamp:    1700000000,
			})
			if err != nil {
				t.Fatal(err)
			}
			if online.AppID != "io.github.spectre_oss.shiraz.org_shiraz_fixture" {
				t.Fatalf("unexpected appId: %q", online.AppID)
			}
			if online.Architecture != tc.arch {
				t.Fatalf("unexpected arch: %q", online.Architecture)
			}
			if !strings.HasSuffix(online.Filename, "-Shiraz-0.360.0-"+tc.arch+".flatpak") {
				t.Fatalf("unexpected filename: %q", online.Filename)
			}
			// Flatpak MIME header "flatpak\x00" + 0xe5890001 LE must appear at the start of superblock.
			if !bytes.Contains(online.Bytes[:64], []byte("flatpak\x00")) || !bytes.Contains(online.Bytes[:64], []byte{0x01, 0x00, 0x89, 0xe5}) {
				t.Fatal("missing Flatpak MIME header in superblock")
			}
			if !bytes.Contains(online.Bytes, []byte("shared=ipc;network;")) {
				t.Fatal("missing network permission in online Flatpak metadata")
			}
			sum := sha256.Sum256(online.Bytes)
			if hex.EncodeToString(sum[:]) != online.SHA256 {
				t.Fatal("reported SHA-256 does not match bundle bytes")
			}

			offline, err := buildFlatpakFromApk(base, apkBuf.Bytes(), "0.360.0", flatpakBuildOptions{
				AllowNetwork: false,
				Timestamp:    1700000000,
			})
			if err != nil {
				t.Fatal(err)
			}
			if bytes.Contains(offline.Bytes, []byte("shared=ipc;network;")) || !bytes.Contains(offline.Bytes, []byte("shared=ipc;\n")) {
				t.Fatal("offline Flatpak metadata still enabled network sharing")
			}
		})
	}
}

func mustReadAll(t *testing.T, r interface{ Read([]byte) (int, error) }) []byte {
	t.Helper()
	var buf bytes.Buffer
	tmp := make([]byte, 4096)
	for {
		n, err := r.Read(tmp)
		if n > 0 {
			buf.Write(tmp[:n])
		}
		if err != nil {
			break
		}
	}
	return buf.Bytes()
}
