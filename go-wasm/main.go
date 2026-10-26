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
	Package               string `json:"package"`
	Application           string `json:"application"`
	Activity              string `json:"activity"`
	Version               string `json:"version"`
	ApplicationProperties string `json:"application_properties"`
}

var nativeName = regexp.MustCompile(`^lib[A-Za-z0-9_+.-]+\.so$`)
var javaName = regexp.MustCompile(`^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$`)
var versionName = regexp.MustCompile(`^[0-9]+\.[0-9]+\.[0-9]+$`)
var reserved = map[string]bool{"libc.so": true, "libdl.so": true, "libm.so": true, "liblog.so": true, "libandroid.so": true, "libjnigraphics.so": true, "libEGL.so": true, "libGLESv2.so": true, "libGLESv3.so": true, "libstdc++.so": true}

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
	var nativeFiles []*zip.File
	seen := map[string]bool{}
	var nativeBytes uint64
	for _, file := range apkZip.File {
		if strings.HasPrefix(file.Name, "lib/x86_64/") && !file.FileInfo().IsDir() {
			name := strings.TrimPrefix(file.Name, "lib/x86_64/")
			if !nativeName.MatchString(name) || reserved[name] || seen[name] || file.Flags&1 != 0 || file.UncompressedSize64 > 128<<20 {
				return nil, errors.New("unsupported native library")
			}
			seen[name] = true
			nativeBytes += file.UncompressedSize64
			if len(seen) > 64 || nativeBytes > 512<<20 {
				return nil, errors.New("native payload exceeds limit")
			}
			nativeFiles = append(nativeFiles, file)
		}
	}
	sum := sha256.Sum256(apk)
	launch := fmt.Sprintf("activity=%s\naot=false\napplication=%s\nnative=%t\npackage=%s\nsha256=%x\nversion=%s\n", property(meta.Activity), property(meta.Application), len(nativeFiles) > 0, property(meta.Package), sum, property(meta.Version))
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
		if file.UncompressedSize64 > 128<<20 {
			return nil, errors.New("native library exceeds size limit")
		}
		stream, err := file.Open()
		if err != nil {
			return nil, err
		}
		data, err := io.ReadAll(io.LimitReader(stream, 128<<20+1))
		stream.Close()
		if err != nil {
			return nil, err
		}
		if len(data) > 128<<20 {
			return nil, errors.New("native library exceeds size limit")
		}
		if len(data) < 20 || string(data[:6]) != "\x7fELF\x02\x01" || data[16] != 3 || data[17] != 0 || data[18] != 62 || data[19] != 0 {
			return nil, errors.New("native library is not x86_64 ELF shared object")
		}
		name := "usr/android-lib/" + path.Base(file.Name)
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
	var empty bytes.Buffer
	gz := gzip.NewWriter(&empty)
	tw := tar.NewWriter(gz)
	if err := tw.Close(); err != nil {
		return map[string]any{"error": err.Error()}
	}
	if err := gz.Close(); err != nil {
		return map[string]any{"error": err.Error()}
	}
	overlay, err := assemble(empty.Bytes(), bytesFromJS(args[1]), []byte(args[2].String()))
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	base := bytesFromJS(args[0])
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
	apk := bytesFromJS(args[1])
	meta, err := inspectAPK(apk, args[2].String())
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
	base := bytesFromJS(args[0])
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
	return map[string]any{"bytes": array, "sha256": hex.EncodeToString(hash[:]), "package": meta.Package}
}
func jsInspectApk(this js.Value, args []js.Value) any {
	if len(args) != 2 {
		return map[string]any{"error": "expected APK and Shiraz version"}
	}
	meta, err := inspectAPK(bytesFromJS(args[0]), args[1].String())
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	raw, err := json.Marshal(meta)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	return string(raw)
}
func main() {
	js.Global().Set("shirazAssemble", js.FuncOf(jsAssemble))
	js.Global().Set("shirazBuildStandalone", js.FuncOf(jsBuildStandalone))
	js.Global().Set("shirazBuildFromApk", js.FuncOf(jsBuildFromApk))
	js.Global().Set("shirazInspectApk", js.FuncOf(jsInspectApk))
	select {}
}
