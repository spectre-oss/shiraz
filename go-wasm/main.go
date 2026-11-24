// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"compress/gzip"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"path"
	"regexp"
	"strings"
	"syscall/js"
)

type metadata struct {
	Architecture          string `json:"architecture"`
	Package               string `json:"package"`
	Application           string `json:"application"`
	Activity              string `json:"activity"`
	Version               string `json:"version"`
	ApplicationProperties string `json:"application_properties"`
	Label                 string `json:"label,omitempty"`
	AllowDigitalis        bool   `json:"allow_digitalis,omitempty"`
	IconRes               uint32 `json:"-"`
}

var nativeName = regexp.MustCompile(`^lib[A-Za-z0-9_+.-]+\.so$`)
var javaName = regexp.MustCompile(`^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$`)
var versionName = regexp.MustCompile(`^[0-9]+\.[0-9]+\.[0-9]+$`)
var reserved = map[string]bool{"libc.so": true, "libdl.so": true, "libm.so": true, "liblog.so": true, "libandroid.so": true, "libjnigraphics.so": true, "libEGL.so": true, "libGLESv2.so": true, "libGLESv3.so": true, "libstdc++.so": true}

func targetABI(architecture string) (string, byte, error) {
	switch architecture {
	case "", "x86_64":
		return "x86_64", 62, nil
	case "aarch64":
		return "arm64-v8a", 183, nil
	default:
		return "", 0, errors.New("unsupported target architecture")
	}
}
func baseArchitecture(base []byte) (string, error) {
	if len(base) < 20 || string(base[:6]) != "\x7fELF\x02\x01" || base[19] != 0 {
		return "", errors.New("base must be a 64-bit little-endian ELF AppImage")
	}
	switch base[18] {
	case 62:
		return "x86_64", nil
	case 183:
		return "aarch64", nil
	default:
		return "", errors.New("unsupported base architecture")
	}
}

func property(s string) string {
	return strings.ReplaceAll(strings.ReplaceAll(s, "\\", "\\\\"), "\n", "\\n")
}

func assemble(template, apk, raw []byte) ([]byte, error) {
	var meta metadata
	if err := json.Unmarshal(raw, &meta); err != nil {
		return nil, err
	}
	if !javaName.MatchString(meta.Package) || !javaName.MatchString(meta.Application) || !javaName.MatchString(meta.Activity) || !versionName.MatchString(meta.Version) {
		return nil, errors.New("invalid package, component, or version metadata")
	}
	if len(apk) < 4 || string(apk[:2]) != "PK" {
		return nil, errors.New("input is not an APK/ZIP")
	}
	apkZip, err := zip.NewReader(bytes.NewReader(apk), int64(len(apk)))
	if err != nil {
		return nil, err
	}
	abi, machine, err := targetABI(meta.Architecture)
	if err != nil {
		return nil, err
	}
	var nativeFiles []*zip.File
	seen := map[string]bool{}
	var nativeBytes uint64
	hasNative := false
	selectedABI := abi
	selectedMachine := machine
	useDigitalis := false
	collectABI := func(candidateABI string) ([]*zip.File, error) {
		var found []*zip.File
		localSeen := map[string]bool{}
		var total uint64
		for _, file := range apkZip.File {
			if strings.HasPrefix(file.Name, "lib/"+candidateABI+"/") && !file.FileInfo().IsDir() {
				name := strings.TrimPrefix(file.Name, "lib/"+candidateABI+"/")
				if !nativeName.MatchString(name) || reserved[name] || localSeen[name] || file.Flags&1 != 0 || file.UncompressedSize64 > 256<<20 {
					return nil, errors.New("unsupported native library")
				}
				localSeen[name] = true
				total += file.UncompressedSize64
				if len(localSeen) > 64 || total > 512<<20 {
					return nil, errors.New("native payload exceeds limit")
				}
				found = append(found, file)
			}
		}
		seen = localSeen
		nativeBytes = total
		return found, nil
	}
	for _, file := range apkZip.File {
		if strings.HasPrefix(file.Name, "lib/") && strings.HasSuffix(file.Name, ".so") {
			hasNative = true
			break
		}
	}
	nativeFiles, err = collectABI(abi)
	if err != nil {
		return nil, err
	}
	if hasNative && len(nativeFiles) == 0 && abi == "x86_64" && meta.AllowDigitalis {
		armFiles, err := collectABI("arm64-v8a")
		if err != nil {
			return nil, err
		}
		if len(armFiles) > 0 {
			nativeFiles = armFiles
			selectedABI = "arm64-v8a"
			selectedMachine = 183
			useDigitalis = true
		}
	}
	_ = seen
	_ = nativeBytes
	if hasNative && len(nativeFiles) == 0 {
		return nil, fmt.Errorf("APK has no %s native library candidate", abi)
	}
	sum := sha256.Sum256(apk)
	launch := fmt.Sprintf("activity=%s\naot=false\napplication=%s\ndigitalis=%t\nnative=%t\nnative_abi=%s\npackage=%s\nsha256=%x\nversion=%s\n", property(meta.Activity), property(meta.Application), useDigitalis, len(nativeFiles) > 0, selectedABI, property(meta.Package), sum, property(meta.Version))
	input, err := gzip.NewReader(bytes.NewReader(template))
	if err != nil {
		return nil, err
	}
	defer input.Close()
	reader := tar.NewReader(input)
	var output bytes.Buffer
	zipped := gzip.NewWriter(&output)
	writer := tar.NewWriter(zipped)
	excluded := map[string]bool{"usr/share/shiraz/original.apk": true, "usr/share/shiraz/launch.properties": true, "usr/share/shiraz/application.properties": true, "usr/share/shiraz/bundle.json": true, "usr/share/shiraz/files.sha256.json": true}
	for {
		h, err := reader.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, err
		}
		name := path.Clean(h.Name)
		if name == "." || strings.HasPrefix(name, "../") || strings.HasPrefix(name, "/") {
			return nil, errors.New("unsafe template path")
		}
		if excluded[name] {
			continue
		}
		if err := writer.WriteHeader(h); err != nil {
			return nil, err
		}
		if h.Typeflag == tar.TypeReg || h.Typeflag == tar.TypeRegA {
			if _, err := io.Copy(writer, reader); err != nil {
				return nil, err
			}
		}
	}
	files := map[string][]byte{"usr/share/shiraz/original.apk": apk, "usr/share/shiraz/launch.properties": []byte(launch), "usr/share/shiraz/application.properties": []byte(meta.ApplicationProperties)}
	for _, name := range []string{"usr/share/shiraz/original.apk", "usr/share/shiraz/launch.properties", "usr/share/shiraz/application.properties"} {
		data := files[name]
		if err := writer.WriteHeader(&tar.Header{Name: name, Mode: 0644, Size: int64(len(data)), Typeflag: tar.TypeReg}); err != nil {
			return nil, err
		}
		if _, err := writer.Write(data); err != nil {
			return nil, err
		}
	}
	for _, file := range nativeFiles {
		if file.UncompressedSize64 > 256<<20 {
			return nil, errors.New("native library exceeds size limit")
		}
		stream, err := file.Open()
		if err != nil {
			return nil, err
		}
		data, err := io.ReadAll(io.LimitReader(stream, 256<<20+1))
		stream.Close()
		if err != nil {
			return nil, err
		}
		if len(data) > 256<<20 {
			return nil, errors.New("native library exceeds size limit")
		}
		if len(data) < 20 || string(data[:6]) != "\x7fELF\x02\x01" || data[16] != 3 || data[17] != 0 || data[18] != selectedMachine || data[19] != 0 {
			return nil, errors.New("native library architecture does not match the base")
		}
		libDir := "usr/android-lib/"
		if useDigitalis {
			libDir = "usr/android-lib-arm64/"
		}
		name := libDir + path.Base(file.Name)
		if err := writer.WriteHeader(&tar.Header{Name: name, Mode: 0644, Size: int64(len(data)), Typeflag: tar.TypeReg}); err != nil {
			return nil, err
		}
		if _, err := writer.Write(data); err != nil {
			return nil, err
		}
	}
	desktop := "[Desktop Entry]\nType=Application\nVersion=1.5\nName=" + strings.ReplaceAll(meta.Package, "\n", "") + " (Shiraz)\nExec=AppRun\nIcon=shiraz-icon\nCategories=Utility;\nTerminal=false\n"
	if err := writer.WriteHeader(&tar.Header{Name: "shiraz.desktop", Mode: 0644, Size: int64(len(desktop)), Typeflag: tar.TypeReg}); err != nil {
		return nil, err
	}
	if _, err := writer.Write([]byte(desktop)); err != nil {
		return nil, err
	}
	if err := writer.WriteHeader(&tar.Header{Name: ".DirIcon", Mode: 0777, Typeflag: tar.TypeSymlink, Linkname: "shiraz-icon.svg"}); err != nil {
		return nil, err
	}
	if err := writer.Close(); err != nil {
		return nil, err
	}
	if err := zipped.Close(); err != nil {
		return nil, err
	}
	return output.Bytes(), nil
}

func bytesFromJS(v js.Value) []byte {
	b := make([]byte, v.Get("length").Int())
	js.CopyBytesToGo(b, v)
	return b
}
func jsAssemble(this js.Value, args []js.Value) any {
	if len(args) != 3 {
		return map[string]any{"error": "expected template, APK, and metadata JSON"}
	}
	output, err := assemble(bytesFromJS(args[0]), bytesFromJS(args[1]), []byte(args[2].String()))
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	array := js.Global().Get("Uint8Array").New(len(output))
	js.CopyBytesToJS(array, output)
	hash := sha256.Sum256(output)
	return map[string]any{"bytes": array, "sha256": hex.EncodeToString(hash[:])}
}
func jsBuildStandalone(this js.Value, args []js.Value) any {
	if len(args) != 3 {
		return map[string]any{"error": "expected base AppImage, APK, and metadata JSON"}
	}
	base := bytesFromJS(args[0])
	architecture, err := baseArchitecture(base)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	var meta metadata
	if err := json.Unmarshal([]byte(args[2].String()), &meta); err != nil {
		return map[string]any{"error": err.Error()}
	}
	if meta.Architecture != "" && meta.Architecture != architecture {
		return map[string]any{"error": "metadata/base architecture mismatch"}
	}
	meta.Architecture = architecture
	raw, err := json.Marshal(meta)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	var empty bytes.Buffer
	gz := gzip.NewWriter(&empty)
	tw := tar.NewWriter(gz)
	if err := tw.Close(); err != nil {
		return map[string]any{"error": err.Error()}
	}
	if err := gz.Close(); err != nil {
		return map[string]any{"error": err.Error()}
	}
	overlay, err := assemble(empty.Bytes(), bytesFromJS(args[1]), raw)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	if len(base) < 4 || string(base[:4]) != "\x7fELF" {
		return map[string]any{"error": "base is not an ELF AppImage"}
	}
	var output bytes.Buffer
	output.Grow(len(base) + len(overlay) + 48)
	output.Write(base)
	output.Write(overlay)
	digest := sha256.Sum256(overlay)
	output.Write(digest[:])
	size := uint64(len(overlay))
	for i := 0; i < 8; i++ {
		output.WriteByte(byte(size >> (8 * i)))
	}
	output.WriteString("SHIRAZ01")
	result := output.Bytes()
	array := js.Global().Get("Uint8Array").New(len(result))
	js.CopyBytesToJS(array, result)
	hash := sha256.Sum256(result)
	return map[string]any{"bytes": array, "sha256": hex.EncodeToString(hash[:])}
}
func jsBuildFromApk(this js.Value, args []js.Value) any {
	if len(args) != 3 {
		return map[string]any{"error": "expected base AppImage, APK, and Shiraz version"}
	}
	base := bytesFromJS(args[0])
	architecture, err := baseArchitecture(base)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	apk := bytesFromJS(args[1])
	meta, err := inspectAPK(apk, args[2].String(), architecture)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	raw, err := json.Marshal(meta)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	overlay, err := func() ([]byte, error) {
		var empty bytes.Buffer
		gz := gzip.NewWriter(&empty)
		tw := tar.NewWriter(gz)
		if err := tw.Close(); err != nil {
			return nil, err
		}
		if err := gz.Close(); err != nil {
			return nil, err
		}
		return assemble(empty.Bytes(), apk, raw)
	}()
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	if len(base) < 4 || string(base[:4]) != "\x7fELF" {
		return map[string]any{"error": "base is not an ELF AppImage"}
	}
	var output bytes.Buffer
	output.Grow(len(base) + len(overlay) + 48)
	output.Write(base)
	output.Write(overlay)
	digest := sha256.Sum256(overlay)
	output.Write(digest[:])
	size := uint64(len(overlay))
	for i := 0; i < 8; i++ {
		output.WriteByte(byte(size >> (8 * i)))
	}
	output.WriteString("SHIRAZ01")
	result := output.Bytes()
	array := js.Global().Get("Uint8Array").New(len(result))
	js.CopyBytesToJS(array, result)
	hash := sha256.Sum256(result)
	return map[string]any{"bytes": array, "sha256": hex.EncodeToString(hash[:]), "package": meta.Package, "architecture": meta.Architecture}
}

type browserLaunch struct {
	Architecture          string   `json:"architecture"`
	Package               string   `json:"package"`
	Label                 string   `json:"label,omitempty"`
	Application           string   `json:"application"`
	Activity              string   `json:"activity"`
	Version               string   `json:"version"`
	SHA256                string   `json:"sha256"`
	Native                bool     `json:"native"`
	NativeLibs            []string `json:"native_libs"`
	LaunchProperties      string   `json:"launch_properties"`
	ApplicationProperties string   `json:"application_properties"`
}

func prepareBrowserApk(apk []byte, version string) (*browserLaunch, error) {
	if !versionName.MatchString(version) {
		return nil, errors.New("invalid Shiraz runtime version")
	}
	if len(apk) < 4 || string(apk[:2]) != "PK" {
		return nil, errors.New("input is not an APK/ZIP")
	}
	meta, err := inspectAPK(apk, version, "aarch64")
	if err != nil {
		return nil, err
	}
	apkZip, err := zip.NewReader(bytes.NewReader(apk), int64(len(apk)))
	if err != nil {
		return nil, err
	}
	abi, machine, err := targetABI("aarch64")
	if err != nil {
		return nil, err
	}
	seen := map[string]bool{}
	var nativeLibs []string
	var nativeBytes uint64
	hasNative := false
	for _, file := range apkZip.File {
		if strings.HasPrefix(file.Name, "lib/") && strings.HasSuffix(file.Name, ".so") {
			hasNative = true
		}
		if strings.HasPrefix(file.Name, "lib/"+abi+"/") && !file.FileInfo().IsDir() {
			name := strings.TrimPrefix(file.Name, "lib/"+abi+"/")
			if !nativeName.MatchString(name) || reserved[name] || seen[name] || file.Flags&1 != 0 || file.UncompressedSize64 > 256<<20 {
				return nil, errors.New("unsupported native library")
			}
			stream, err := file.Open()
			if err != nil {
				return nil, err
			}
			var header [20]byte
			n, readErr := io.ReadFull(stream, header[:])
			stream.Close()
			if readErr != nil || n < 20 || string(header[:6]) != "\x7fELF\x02\x01" || header[16] != 3 || header[17] != 0 || header[18] != machine || header[19] != 0 {
				return nil, errors.New("native library architecture does not match aarch64")
			}
			seen[name] = true
			nativeBytes += file.UncompressedSize64
			if len(seen) > 64 || nativeBytes > 512<<20 {
				return nil, errors.New("native payload exceeds limit")
			}
			nativeLibs = append(nativeLibs, name)
		}
	}
	if hasNative && len(nativeLibs) == 0 {
		return nil, errors.New("APK has native libraries without arm64-v8a support; the browser runner requires arm64-v8a or pure Java/Kotlin APKs")
	}
	if nativeLibs == nil {
		nativeLibs = []string{}
	}
	sum := sha256.Sum256(apk)
	digest := hex.EncodeToString(sum[:])
	launch := fmt.Sprintf("activity=%s\naot=false\napplication=%s\ndigitalis=false\nnative=%t\npackage=%s\nsha256=%s\nversion=%s\n", property(meta.Activity), property(meta.Application), len(nativeLibs) > 0, property(meta.Package), digest, property(version))
	return &browserLaunch{
		Architecture:          "aarch64",
		Package:               meta.Package,
		Label:                 meta.Label,
		Application:           meta.Application,
		Activity:              meta.Activity,
		Version:               version,
		SHA256:                digest,
		Native:                len(nativeLibs) > 0,
		NativeLibs:            nativeLibs,
		LaunchProperties:      launch,
		ApplicationProperties: meta.ApplicationProperties,
	}, nil
}

func jsPrepareBrowserApk(this js.Value, args []js.Value) any {
	if len(args) != 2 {
		return map[string]any{"error": "expected APK bytes and Shiraz runtime version"}
	}
	result, err := prepareBrowserApk(bytesFromJS(args[0]), args[1].String())
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	raw, err := json.Marshal(result)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	return string(raw)
}

func jsInspectApk(this js.Value, args []js.Value) any {
	if len(args) != 2 && len(args) != 3 {
		return map[string]any{"error": "expected APK, Shiraz version, and optional architecture"}
	}
	architecture := "x86_64"
	if len(args) == 3 {
		architecture = args[2].String()
	}
	meta, err := inspectAPK(bytesFromJS(args[0]), args[1].String(), architecture)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	raw, err := json.Marshal(meta)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	return string(raw)
}
func jsBuildFlatpakFromApk(this js.Value, args []js.Value) any {
	if len(args) != 3 && len(args) != 4 {
		return map[string]any{"error": "expected Flatpak base, APK, Shiraz version, and optional options"}
	}
	opts := flatpakBuildOptions{AllowNetwork: true}
	if len(args) == 4 && args[3].Type() == js.TypeObject {
		if v := args[3].Get("allowNetwork"); v.Type() == js.TypeBoolean {
			opts.AllowNetwork = v.Bool()
		}
		if v := args[3].Get("allowDigitalis"); v.Type() == js.TypeBoolean {
			opts.AllowDigitalis = v.Bool()
		}
	}
	result, err := buildFlatpakFromApk(bytesFromJS(args[0]), bytesFromJS(args[1]), args[2].String(), opts)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	array := js.Global().Get("Uint8Array").New(len(result.Bytes))
	js.CopyBytesToJS(array, result.Bytes)
	return map[string]any{
		"bytes":        array,
		"sha256":       result.SHA256,
		"package":      result.Package,
		"appId":        result.AppID,
		"architecture": result.Architecture,
		"filename":     result.Filename,
	}
}
func jsBuildMacAppFromApk(this js.Value, args []js.Value) any {
	if len(args) != 3 && len(args) != 4 {
		return map[string]any{"error": "expected macOS base ZIP, APK, Shiraz version, and optional options"}
	}
	opts := macAppSidecarOptions{AllowNetwork: true, Sandbox: true}
	if len(args) == 4 && args[3].Type() == js.TypeObject {
		if v := args[3].Get("allowNetwork"); v.Type() == js.TypeBoolean {
			opts.AllowNetwork = v.Bool()
		}
		if v := args[3].Get("sandbox"); v.Type() == js.TypeBoolean {
			opts.Sandbox = v.Bool()
		}
	}
	result, err := buildMacAppSidecarFromApk(bytesFromJS(args[0]), bytesFromJS(args[1]), args[2].String(), opts)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	array := js.Global().Get("Uint8Array").New(len(result.Bytes))
	js.CopyBytesToJS(array, result.Bytes)
	return map[string]any{
		"bytes":        array,
		"sha256":       result.SHA256,
		"package":      result.Package,
		"architecture": result.Architecture,
		"filename":     result.Filename,
		"native":       result.Native,
	}
}
func main() {
	js.Global().Set("shirazAssemble", js.FuncOf(jsAssemble))
	js.Global().Set("shirazBuildStandalone", js.FuncOf(jsBuildStandalone))
	js.Global().Set("shirazBuildFromApk", js.FuncOf(jsBuildFromApk))
	js.Global().Set("shirazBuildFlatpakFromApk", js.FuncOf(jsBuildFlatpakFromApk))
	js.Global().Set("shirazBuildMacAppFromApk", js.FuncOf(jsBuildMacAppFromApk))
	js.Global().Set("shirazInspectApk", js.FuncOf(jsInspectApk))
	js.Global().Set("shirazPrepareBrowserApk", js.FuncOf(jsPrepareBrowserApk))
	select {}
}
