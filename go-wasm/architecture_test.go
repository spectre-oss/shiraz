// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"compress/gzip"
	"encoding/json"
	"io"
	"testing"
)

func testELF(machine byte) []byte {
	b := make([]byte, 64)
	copy(b, "\x7fELF\x02\x01")
	b[16], b[18] = 3, machine
	return b
}

func TestBaseArchitecture(t *testing.T) {
	for _, tc := range []struct {
		machine byte
		name    string
	}{{62, "x86_64"}, {183, "aarch64"}} {
		got, err := baseArchitecture(testELF(tc.machine))
		if err != nil || got != tc.name {
			t.Fatalf("got %q, %v", got, err)
		}
	}
	for _, b := range [][]byte{nil, []byte("\x7fELF"), testELF(40)} {
		if _, err := baseArchitecture(b); err == nil {
			t.Fatal("accepted unsupported base")
		}
	}
}

func TestNativeArchitectureSelection(t *testing.T) {
	for _, tc := range []struct {
		name, abi string
		machine   byte
	}{{"x86_64", "x86_64", 62}, {"aarch64", "arm64-v8a", 183}} {
		t.Run(tc.name, func(t *testing.T) {
			var empty bytes.Buffer
			gz := gzip.NewWriter(&empty)
			tw := tar.NewWriter(gz)
			tw.Close()
			gz.Close()
			meta, _ := json.Marshal(metadata{Architecture: tc.name, Package: "org.shiraz.fixture", Application: "android.app.Application", Activity: "org.shiraz.fixture.Main", Version: "1.0.0"})
			for _, wrong := range []bool{false, true} {
				var apk bytes.Buffer
				zw := zip.NewWriter(&apk)
				f, _ := zw.Create("lib/" + tc.abi + "/libprobe.so")
				machine := tc.machine
				if wrong {
					machine = 40
				}
				f.Write(testELF(machine))
				other, _ := zw.Create("lib/armeabi-v7a/libignored.so")
				other.Write(testELF(40))
				zw.Close()
				result, err := assemble(empty.Bytes(), apk.Bytes(), meta)
				if wrong {
					if err == nil {
						t.Fatal("accepted wrong ELF machine")
					}
					continue
				}
				if err != nil {
					t.Fatal(err)
				}
				reader, err := gzip.NewReader(bytes.NewReader(result))
				if err != nil {
					t.Fatal(err)
				}
				tr := tar.NewReader(reader)
				found := false
				for {
					h, err := tr.Next()
					if err == io.EOF {
						break
					}
					if err != nil {
						t.Fatal(err)
					}
					if h.Name == "usr/android-lib/libprobe.so" {
						b, _ := io.ReadAll(tr)
						found = b[18] == tc.machine
					}
					if h.Name == "usr/android-lib/libignored.so" {
						t.Fatal("included foreign ABI")
					}
				}
				reader.Close()
				if !found {
					t.Fatal("target native library missing")
				}
			}
		})
	}
}

func TestDigitalisArm64OnX8664Selection(t *testing.T) {
	var empty bytes.Buffer
	gz := gzip.NewWriter(&empty)
	tw := tar.NewWriter(gz)
	tw.Close()
	gz.Close()

	var apk bytes.Buffer
	zw := zip.NewWriter(&apk)
	f, _ := zw.Create("lib/arm64-v8a/libgame.so")
	f.Write(testELF(183))
	zw.Close()

	// Without AllowDigitalis, x86_64 rejects an arm64-only APK.
	metaOff, _ := json.Marshal(metadata{Architecture: "x86_64", Package: "org.shiraz.dig", Application: "android.app.Application", Activity: "org.shiraz.dig.Main", Version: "1.0.0", AllowDigitalis: false})
	if _, err := assemble(empty.Bytes(), apk.Bytes(), metaOff); err == nil {
		t.Fatal("accepted arm64-only APK on x86_64 without AllowDigitalis")
	}

	// With AllowDigitalis, x86_64 packages arm64-v8a libraries under usr/android-lib-arm64/ and sets digitalis=true.
	metaOn, _ := json.Marshal(metadata{Architecture: "x86_64", Package: "org.shiraz.dig", Application: "android.app.Application", Activity: "org.shiraz.dig.Main", Version: "1.0.0", AllowDigitalis: true})
	result, err := assemble(empty.Bytes(), apk.Bytes(), metaOn)
	if err != nil {
		t.Fatalf("unexpected Digitalis assemble error: %v", err)
	}
	reader, err := gzip.NewReader(bytes.NewReader(result))
	if err != nil {
		t.Fatal(err)
	}
	defer reader.Close()
	tr := tar.NewReader(reader)
	foundArmLib := false
	foundLaunchFlag := false
	for {
		h, err := tr.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		if h.Name == "usr/android-lib-arm64/libgame.so" {
			b, _ := io.ReadAll(tr)
			foundArmLib = len(b) >= 20 && b[18] == 183
		}
		if h.Name == "usr/share/shiraz/launch.properties" {
			b, _ := io.ReadAll(tr)
			foundLaunchFlag = bytes.Contains(b, []byte("digitalis=true\n")) && bytes.Contains(b, []byte("native_abi=arm64-v8a\n"))
		}
	}
	if !foundArmLib || !foundLaunchFlag {
		t.Fatalf("Digitalis bundle incomplete: armLib=%v launchFlag=%v", foundArmLib, foundLaunchFlag)
	}
}
