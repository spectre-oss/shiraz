// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/zip"
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"path"
	"strings"
)

type macAppSidecarOptions struct {
	AllowNetwork bool
	Sandbox      bool
}

type macAppSidecarResult struct {
	Bytes        []byte
	SHA256       string
	Package      string
	Architecture string
	Filename     string
	Native       bool
}

type macAppSidecarManifest struct {
	Format             string `json:"format"`
	Architecture       string `json:"architecture"`
	AppBundleUntouched bool   `json:"app_bundle_untouched"`
	Package            string `json:"package"`
	Label              string `json:"label"`
	Activity           string `json:"activity"`
	Application        string `json:"application"`
	Version            string `json:"version"`
	ApkSHA256          string `json:"apk_sha256"`
	Native             bool   `json:"native"`
	NativeABI          string `json:"native_abi,omitempty"`
	AllowNetwork       bool   `json:"allow_network"`
	Sandbox            bool   `json:"sandbox"`
}

func safeMacBundleStem(pkg string) string {
	cleaned := strings.Trim(nonAppIDRun.ReplaceAllString(pkg, "-"), "-")
	if cleaned == "" {
		return "shiraz-app"
	}
	if len(cleaned) > 64 {
		cleaned = cleaned[:64]
	}
	return cleaned
}

// buildMacAppSidecarFromApk packages an unmodified signed macOS Shiraz.app base
// archive alongside an untouched original.apk sidecar and launch metadata.
// It never modifies any Mach-O binary, Info.plist, or _CodeSignature entry
// inside Shiraz.app so Apple Silicon code signatures stay intact.
func buildMacAppSidecarFromApk(baseZipBytes, apkBytes []byte, version string, opts macAppSidecarOptions) (*macAppSidecarResult, error) {
	if len(baseZipBytes) < 4 || string(baseZipBytes[:2]) != "PK" {
		return nil, errors.New("macOS base must be a ZIP archive containing Shiraz.app")
	}
	if len(apkBytes) < 4 || string(apkBytes[:2]) != "PK" {
		return nil, errors.New("input is not an APK/ZIP")
	}
	meta, err := inspectAPK(apkBytes, version, "aarch64")
	if err != nil {
		return nil, err
	}
	if !javaName.MatchString(meta.Package) || !javaName.MatchString(meta.Application) || !javaName.MatchString(meta.Activity) || !versionName.MatchString(meta.Version) {
		return nil, errors.New("invalid package, component, or version metadata")
	}

	apkZip, err := zip.NewReader(bytes.NewReader(apkBytes), int64(len(apkBytes)))
	if err != nil {
		return nil, err
	}
	hasNative := false
	arm64Count := 0
	seen := map[string]bool{}
	var nativeBytes uint64
	for _, file := range apkZip.File {
		if strings.HasPrefix(file.Name, "lib/") && strings.HasSuffix(file.Name, ".so") {
			hasNative = true
		}
		if strings.HasPrefix(file.Name, "lib/arm64-v8a/") && !file.FileInfo().IsDir() {
			name := strings.TrimPrefix(file.Name, "lib/arm64-v8a/")
			if !nativeName.MatchString(name) || reserved[name] || seen[name] || file.Flags&1 != 0 || file.UncompressedSize64 > 256<<20 {
				return nil, errors.New("unsupported native library")
			}
			seen[name] = true
			nativeBytes += file.UncompressedSize64
			if len(seen) > 64 || nativeBytes > 512<<20 {
				return nil, errors.New("native payload exceeds limit")
			}
			stream, err := file.Open()
			if err != nil {
				return nil, err
			}
			header := make([]byte, 20)
			n, err := io.ReadFull(stream, header)
			stream.Close()
			if err != nil || n < 20 || string(header[:6]) != "\x7fELF\x02\x01" || header[16] != 3 || header[17] != 0 || header[18] != 183 || header[19] != 0 {
				return nil, errors.New("arm64-v8a native library is not a valid AArch64 ELF shared object")
			}
			arm64Count++
		}
	}
	if hasNative && arm64Count == 0 {
		return nil, errors.New("APK has native libraries but no arm64-v8a candidate for Apple Silicon")
	}

	baseZip, err := zip.NewReader(bytes.NewReader(baseZipBytes), int64(len(baseZipBytes)))
	if err != nil {
		return nil, fmt.Errorf("invalid macOS base ZIP: %w", err)
	}
	if len(baseZip.File) == 0 || len(baseZip.File) > 4096 {
		return nil, errors.New("macOS base archive entry count out of bounds")
	}

	stem := safeMacBundleStem(meta.Package)
	rootDir := stem + "-Shiraz"
	var out bytes.Buffer
	zw := zip.NewWriter(&out)

	foundInfoPlist := false
	var copiedBytes uint64
	for _, entry := range baseZip.File {
		clean := path.Clean(strings.ReplaceAll(entry.Name, "\\", "/"))
		if clean == "." || clean == ".." || strings.HasPrefix(clean, "../") || strings.HasPrefix(clean, "/") {
			return nil, errors.New("unsafe path in macOS base archive")
		}
		if clean != "Shiraz.app" && !strings.HasPrefix(clean, "Shiraz.app/") {
			return nil, errors.New("macOS base archive must contain only Shiraz.app/")
		}
		if clean == "Shiraz.app/Contents/Info.plist" {
			foundInfoPlist = true
		}
		if entry.UncompressedSize64 > 512<<20 {
			return nil, errors.New("macOS base archive entry exceeds size limit")
		}
		copiedBytes += entry.UncompressedSize64
		if copiedBytes > 1024<<20 {
			return nil, errors.New("macOS base archive exceeds size limit")
		}

		hdr := entry.FileHeader
		hdr.Name = rootDir + "/" + clean
		if entry.FileInfo().IsDir() && !strings.HasSuffix(hdr.Name, "/") {
			hdr.Name += "/"
		}
		dst, err := zw.CreateHeader(&hdr)
		if err != nil {
			return nil, err
		}
		if !entry.FileInfo().IsDir() {
			src, err := entry.Open()
			if err != nil {
				return nil, err
			}
			_, copyErr := io.Copy(dst, io.LimitReader(src, int64(entry.UncompressedSize64)+1))
			src.Close()
			if copyErr != nil {
				return nil, copyErr
			}
		}
	}
	if !foundInfoPlist {
		return nil, errors.New("macOS base archive is missing Shiraz.app/Contents/Info.plist")
	}

	apkSum := sha256.Sum256(apkBytes)
	apkHex := hex.EncodeToString(apkSum[:])
	title := meta.Label
	if title == "" {
		title = flatpakTitle(meta.Package)
	}
	nativeABI := ""
	if arm64Count > 0 {
		nativeABI = "arm64-v8a"
	}

	launchProps := fmt.Sprintf(
		"activity=%s\nallow_network=%t\naot=false\napplication=%s\nnative=%t\nnative_abi=%s\npackage=%s\nsandbox=%t\nsha256=%s\nversion=%s\n",
		property(meta.Activity),
		opts.AllowNetwork,
		property(meta.Application),
		arm64Count > 0,
		nativeABI,
		property(meta.Package),
		opts.Sandbox,
		apkHex,
		property(meta.Version),
	)
	manifestBytes, err := json.MarshalIndent(macAppSidecarManifest{
		Format:             "shiraz-macos-sidecar-v1",
		Architecture:       "arm64",
		AppBundleUntouched: true,
		Package:            meta.Package,
		Label:              title,
		Activity:           meta.Activity,
		Application:        meta.Application,
		Version:            meta.Version,
		ApkSHA256:          apkHex,
		Native:             arm64Count > 0,
		NativeABI:          nativeABI,
		AllowNetwork:       opts.AllowNetwork,
		Sandbox:            opts.Sandbox,
	}, "", "  ")
	if err != nil {
		return nil, err
	}
	manifestBytes = append(manifestBytes, '\n')

	readme := fmt.Sprintf(
		"Shiraz macOS Apple Silicon Sidecar Package\n"+
			"=========================================\n\n"+
			"Package: %s (%s)\n"+
			"APK SHA-256: %s\n\n"+
			"Shiraz.app is included byte-for-byte untouched so its macOS code signature\n"+
			"and notarization ticket remain valid. The unmodified APK and launch metadata\n"+
			"live next to it under AppData/.\n\n"+
			"Launch:\n"+
			"  1. Unzip this archive on an Apple Silicon Mac (macOS 14+).\n"+
			"  2. Open Shiraz.app or import AppData/original.apk in Shiraz Launcher.\n",
		title, meta.Package, apkHex,
	)

	sidecarFiles := []struct {
		name string
		data []byte
	}{
		{rootDir + "/AppData/original.apk", apkBytes},
		{rootDir + "/AppData/launch.properties", []byte(launchProps)},
		{rootDir + "/AppData/application.properties", []byte(meta.ApplicationProperties)},
		{rootDir + "/AppData/manifest.json", manifestBytes},
		{rootDir + "/README-macOS.txt", []byte(readme)},
	}
	for _, f := range sidecarFiles {
		hdr := &zip.FileHeader{Name: f.name, Method: zip.Deflate}
		hdr.SetMode(0o644)
		w, err := zw.CreateHeader(hdr)
		if err != nil {
			return nil, err
		}
		if _, err := w.Write(f.data); err != nil {
			return nil, err
		}
	}
	if err := zw.Close(); err != nil {
		return nil, err
	}

	outBytes := out.Bytes()
	outSum := sha256.Sum256(outBytes)
	return &macAppSidecarResult{
		Bytes:        outBytes,
		SHA256:       hex.EncodeToString(outSum[:]),
		Package:      meta.Package,
		Architecture: "arm64",
		Filename:     stem + "-macos-arm64.zip",
		Native:       arm64Count > 0,
	}, nil
}
