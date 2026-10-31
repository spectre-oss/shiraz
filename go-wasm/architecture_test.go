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
