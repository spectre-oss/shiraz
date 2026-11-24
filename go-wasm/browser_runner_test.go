// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/zip"
	"bytes"
	"io"
	"os"
	"strings"
	"testing"
)

func augmentApkWithZipEntry(t *testing.T, baseApk []byte, name string, payload []byte) []byte {
	t.Helper()
	orig, err := zip.NewReader(bytes.NewReader(baseApk), int64(len(baseApk)))
	if err != nil {
		t.Fatal(err)
	}
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	for _, f := range orig.File {
		rc, err := f.Open()
		if err != nil {
			t.Fatal(err)
		}
		data, err := io.ReadAll(rc)
		rc.Close()
		if err != nil {
			t.Fatal(err)
		}
		w, err := zw.Create(f.Name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := w.Write(data); err != nil {
			t.Fatal(err)
		}
	}
	if name != "" {
		w, err := zw.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := w.Write(payload); err != nil {
			t.Fatal(err)
		}
	}
	if err := zw.Close(); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func TestPrepareBrowserApk(t *testing.T) {
	apkBytes, err := os.ReadFile("../build/apk-fixture/fixture.apk")
	if err != nil {
		t.Skip("fixture.apk not built yet:", err)
	}

	t.Run("pure java apk", func(t *testing.T) {
		launch, err := prepareBrowserApk(apkBytes, "0.363.2")
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if launch.Package != "org.shiraz.fixture" || launch.Architecture != "aarch64" {
			t.Fatalf("unexpected metadata: %+v", launch)
		}
		if launch.Native || len(launch.NativeLibs) != 0 {
			t.Fatalf("expected no native libs, got %+v", launch.NativeLibs)
		}
		if !strings.Contains(launch.LaunchProperties, "native=false\n") || !strings.Contains(launch.LaunchProperties, "version=0.363.2\n") {
			t.Fatalf("unexpected launch.properties:\n%s", launch.LaunchProperties)
		}
	})

	t.Run("arm64 native apk", func(t *testing.T) {
		nativeApk := augmentApkWithZipEntry(t, apkBytes, "lib/arm64-v8a/libprobe.so", testELF(183))
		launch, err := prepareBrowserApk(nativeApk, "0.363.2")
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if !launch.Native || len(launch.NativeLibs) != 1 || launch.NativeLibs[0] != "libprobe.so" {
			t.Fatalf("expected libprobe.so, got native=%v libs=%v", launch.Native, launch.NativeLibs)
		}
		if !strings.Contains(launch.LaunchProperties, "native=true\n") {
			t.Fatalf("expected native=true in launch.properties:\n%s", launch.LaunchProperties)
		}
	})

	t.Run("rejects arm32-only native apk", func(t *testing.T) {
		arm32Apk := augmentApkWithZipEntry(t, apkBytes, "lib/armeabi-v7a/libonly32.so", testELF(40))
		if _, err := prepareBrowserApk(arm32Apk, "0.363.2"); err == nil {
			t.Fatal("accepted arm32-only APK")
		}
	})

	t.Run("rejects wrong elf machine inside arm64-v8a", func(t *testing.T) {
		badElfApk := augmentApkWithZipEntry(t, apkBytes, "lib/arm64-v8a/libwrong.so", testELF(62))
		if _, err := prepareBrowserApk(badElfApk, "0.363.2"); err == nil {
			t.Fatal("accepted x86_64 ELF inside arm64-v8a")
		}
	})
}
