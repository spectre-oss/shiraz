// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/zip"
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"io/fs"
	"strings"
	"testing"
)

func buildTestMacBaseZip(t *testing.T, includeInfoPlist bool) []byte {
	t.Helper()
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	entries := []struct {
		name string
		body string
		mode fs.FileMode
	}{
		{"Shiraz.app/Contents/MacOS/Shiraz", "MACH-O-SIGNED-BYTES", 0o755},
		{"Shiraz.app/Contents/_CodeSignature/CodeResources", "<plist>SIG</plist>", 0o644},
	}
	if includeInfoPlist {
		entries = append(entries, struct {
			name string
			body string
			mode fs.FileMode
		}{"Shiraz.app/Contents/Info.plist", "<plist><dict><key>CFBundleIdentifier</key><string>io.github.spectre_oss.shiraz</string></dict></plist>", 0o644})
	}
	for _, e := range entries {
		h := &zip.FileHeader{Name: e.name, Method: zip.Deflate}
		h.SetMode(e.mode)
		w, err := zw.CreateHeader(h)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := w.Write([]byte(e.body)); err != nil {
			t.Fatal(err)
		}
	}
	if err := zw.Close(); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func buildTestAPKWithOptionalLib(t *testing.T, libPath string, machine byte) []byte {
	t.Helper()
	manifest := buildTestBinaryXML(axmlNodeSpec{
		tag: "manifest",
		attrs: []axmlAttrSpec{
			{name: "package", dataType: 3, strVal: "org.shiraz.macsidecar"},
		},
		children: []axmlNodeSpec{
			{
				tag: "application",
				children: []axmlNodeSpec{
					{
						tag: "activity",
						attrs: []axmlAttrSpec{
							{ns: androidXMLNS, name: "name", dataType: 3, strVal: ".MainActivity"},
						},
						children: []axmlNodeSpec{
							{
								tag: "intent-filter",
								children: []axmlNodeSpec{
									{tag: "action", attrs: []axmlAttrSpec{{ns: androidXMLNS, name: "name", dataType: 3, strVal: "android.intent.action.MAIN"}}},
									{tag: "category", attrs: []axmlAttrSpec{{ns: androidXMLNS, name: "name", dataType: 3, strVal: "android.intent.category.LAUNCHER"}}},
								},
							},
						},
					},
				},
			},
		},
	})
	extra := map[string][]byte{}
	if libPath != "" {
		extra[libPath] = testELF(machine)
	}
	return buildTestAPK(t, manifest, extra)
}

func TestBuildMacAppSidecarPreservesSignedAppBundle(t *testing.T) {
	baseZip := buildTestMacBaseZip(t, true)
	apk := buildTestAPKWithOptionalLib(t, "lib/arm64-v8a/libengine.so", 183)

	res, err := buildMacAppSidecarFromApk(baseZip, apk, "0.363.2", macAppSidecarOptions{
		AllowNetwork: true,
		Sandbox:      true,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if res.Package != "org.shiraz.macsidecar" || res.Architecture != "arm64" || !res.Native {
		t.Fatalf("unexpected metadata: %+v", res)
	}
	if !strings.HasSuffix(res.Filename, "-macos-arm64.zip") {
		t.Fatalf("unexpected filename: %s", res.Filename)
	}

	zr, err := zip.NewReader(bytes.NewReader(res.Bytes), int64(len(res.Bytes)))
	if err != nil {
		t.Fatal(err)
	}
	files := map[string][]byte{}
	for _, f := range zr.File {
		rc, err := f.Open()
		if err != nil {
			t.Fatal(err)
		}
		data, err := io.ReadAll(rc)
		rc.Close()
		if err != nil {
			t.Fatal(err)
		}
		files[f.Name] = data
	}

	prefix := "org-shiraz-macsidecar-Shiraz/"
	if string(files[prefix+"Shiraz.app/Contents/MacOS/Shiraz"]) != "MACH-O-SIGNED-BYTES" {
		t.Fatal("Shiraz.app Mach-O payload was altered")
	}
	if string(files[prefix+"Shiraz.app/Contents/_CodeSignature/CodeResources"]) != "<plist>SIG</plist>" {
		t.Fatal("Shiraz.app _CodeSignature was altered")
	}
	if !bytes.Equal(files[prefix+"AppData/original.apk"], apk) {
		t.Fatal("original.apk sidecar does not match input APK byte-for-byte")
	}
	var manifest macAppSidecarManifest
	if err := json.Unmarshal(files[prefix+"AppData/manifest.json"], &manifest); err != nil {
		t.Fatal(err)
	}
	expectedSum := sha256.Sum256(apk)
	if !manifest.AppBundleUntouched || !manifest.Sandbox || manifest.NativeABI != "arm64-v8a" || manifest.ApkSHA256 != hex.EncodeToString(expectedSum[:]) {
		t.Fatalf("unexpected sidecar manifest: %+v", manifest)
	}
	if !strings.Contains(string(files[prefix+"AppData/launch.properties"]), "sandbox=true\n") {
		t.Fatal("launch.properties missing sandbox=true")
	}
}

func TestBuildMacAppSidecarRejectsX86OnlyAndInvalidBase(t *testing.T) {
	baseZip := buildTestMacBaseZip(t, true)
	x86Apk := buildTestAPKWithOptionalLib(t, "lib/x86_64/libonlyx86.so", 62)
	if _, err := buildMacAppSidecarFromApk(baseZip, x86Apk, "0.363.2", macAppSidecarOptions{Sandbox: true}); err == nil {
		t.Fatal("accepted x86_64-only native APK for Apple Silicon macOS sidecar")
	}
	badBase := buildTestMacBaseZip(t, false)
	armApk := buildTestAPKWithOptionalLib(t, "lib/arm64-v8a/libengine.so", 183)
	if _, err := buildMacAppSidecarFromApk(badBase, armApk, "0.363.2", macAppSidecarOptions{Sandbox: true}); err == nil {
		t.Fatal("accepted macOS base missing Info.plist")
	}
}
