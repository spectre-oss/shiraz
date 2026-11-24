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
	"errors"
	"fmt"
	"io"
	"path"
	"regexp"
	"strings"
	"time"
)

const (
	flatpakBaseMagic      = "SHIRAZFP"
	flatpakBaseVersion    = 1
	flatpakAppIDPrefix    = "io.github.spectre_oss.shiraz"
	fallbackShirazIconSVG = `<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256"><rect width="256" height="256" rx="48" fill="#3b5b8c"/><path d="M64 176l64-96 64 96z" fill="#fff"/></svg>`
)

var nonAppIDRun = regexp.MustCompile(`[^A-Za-z0-9_]+`)

type flatpakDirRef struct {
	Tree string `json:"tree"`
	Meta string `json:"meta"`
}

type flatpakPart0Index struct {
	CompressedSize   uint64 `json:"compressed_size"`
	UncompressedSize uint64 `json:"uncompressed_size"`
	SHA256           string `json:"sha256"`
	ObjectsB64       string `json:"objects_b64"`
}

type flatpakFilesRootIndex struct {
	Bin                       flatpakDirRef `json:"bin"`
	UsrBin                    flatpakDirRef `json:"usr_bin"`
	UsrLib                    flatpakDirRef `json:"usr_lib"`
	UsrArt                    flatpakDirRef `json:"usr_art"`
	UsrShareShirazStaticFiles [][]string    `json:"usr_share_shiraz_static_files"`
	UsrShareShirazStaticDirs  [][]string    `json:"usr_share_shiraz_static_dirs"`
	UsrShareOtherDirs         [][]string    `json:"usr_share_other_dirs"`
}

type flatpakBaseIndex struct {
	Version           string                `json:"version"`
	Architecture      string                `json:"architecture"`
	RuntimeBranch     string                `json:"runtime_branch"`
	RuntimeRepo       string                `json:"runtime_repo"`
	Dirmeta0755SHA256 string                `json:"dirmeta_0755_sha256"`
	InstalledSize     uint64                `json:"installed_size"`
	Part0             flatpakPart0Index     `json:"part0"`
	FilesRoot         flatpakFilesRootIndex `json:"files_root"`
}

type parsedFlatpakBase struct {
	index       flatpakBaseIndex
	dirmeta0755 [32]byte
	part0       ostreeDeltaPart
	binDir      ostreeDirEntry
	usrBinDir   ostreeDirEntry
	usrLibDir   ostreeDirEntry
	usrArtDir   ostreeDirEntry
	shirazFiles []ostreeFileEntry
	shirazDirs  []ostreeDirEntry
	otherShare  []ostreeDirEntry
}

type flatpakBuildOptions struct {
	AllowNetwork bool
	Timestamp    uint64
}

type flatpakBuildResult struct {
	Bytes        []byte
	SHA256       string
	Package      string
	AppID        string
	Architecture string
	Filename     string
}

func flatpakAppID(pkg string) string {
	cleaned := strings.Trim(nonAppIDRun.ReplaceAllString(pkg, "_"), "_")
	if cleaned == "" {
		cleaned = "app"
	}
	if cleaned[0] >= '0' && cleaned[0] <= '9' {
		cleaned = "p" + cleaned
	}
	candidate := flatpakAppIDPrefix + "." + cleaned
	if len(candidate) > 255 {
		sum := sha256.Sum256([]byte(pkg))
		digest := hex.EncodeToString(sum[:])[:12]
		maxBody := 255 - len(flatpakAppIDPrefix) - 2 - len(digest)
		candidate = flatpakAppIDPrefix + "." + cleaned[:maxBody] + "_" + digest
	}
	return candidate
}

func flatpakTitle(pkg string) string {
	if strings.HasPrefix(pkg, "net.osmand") {
		return "OsmAnd"
	}
	tail := pkg
	if dot := strings.LastIndexByte(pkg, '.'); dot >= 0 {
		tail = pkg[dot+1:]
	}
	words := strings.Split(strings.ReplaceAll(tail, "_", " "), " ")
	for i, w := range words {
		if len(w) == 0 {
			continue
		}
		if w[0] >= 'a' && w[0] <= 'z' {
			words[i] = string(w[0]-'a'+'A') + w[1:]
		}
	}
	title := strings.TrimSpace(strings.Join(words, " "))
	if title == "" {
		return pkg
	}
	return title
}

func desktopEscape(s string) string {
	r := strings.NewReplacer("\\", "\\\\", "\n", "\\n", "\r", "\\r", "\t", "\\t")
	return r.Replace(s)
}

func xmlEscape(s string) string {
	r := strings.NewReplacer("&", "&amp;", "<", "&lt;", ">", "&gt;", `"`, "&quot;", "'", "&apos;")
	return r.Replace(s)
}

func flatpakMetadataINI(appID, architecture, runtimeBranch string, allowNetwork bool) string {
	shared := "ipc;network;"
	if !allowNetwork {
		shared = "ipc;"
	}
	return "[Application]\n" +
		"name=" + appID + "\n" +
		"runtime=org.freedesktop.Platform/" + architecture + "/" + runtimeBranch + "\n" +
		"command=shiraz-flatpak-run\n" +
		"\n" +
		"[Context]\n" +
		"shared=" + shared + "\n" +
		"sockets=wayland;fallback-x11;pulseaudio;\n" +
		"devices=dri;\n" +
		"filesystems=xdg-run/pipewire-0:ro;\n" +
		"\n" +
		"[Session Bus Policy]\n" +
		"org.freedesktop.Notifications=talk\n" +
		"org.freedesktop.portal.Desktop=talk\n"
}

func flatpakDesktopEntry(appID, title string) string {
	return "[Desktop Entry]\n" +
		"Type=Application\n" +
		"Version=1.5\n" +
		"Name=" + desktopEscape(title) + " (Shiraz)\n" +
		"Exec=shiraz-flatpak-run\n" +
		"Icon=" + appID + "\n" +
		"Categories=Utility;\n" +
		"Terminal=false\n" +
		"X-Purism-FormFactor=Workstation;Mobile;\n"
}

func flatpakMetainfoXML(appID, title, pkg, version string) string {
	safeID := xmlEscape(appID)
	return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
		"<component type=\"desktop-application\">\n" +
		"  <id>" + safeID + "</id>\n" +
		"  <metadata_license>CC0-1.0</metadata_license>\n" +
		"  <project_license>GPL-3.0-only</project_license>\n" +
		"  <name>" + xmlEscape(title) + " (Shiraz)</name>\n" +
		"  <summary>Android app (" + xmlEscape(pkg) + ") packaged with Shiraz</summary>\n" +
		"  <launchable type=\"desktop-id\">" + safeID + ".desktop</launchable>\n" +
		"  <releases>\n" +
		"    <release version=\"" + xmlEscape(version) + "\" />\n" +
		"  </releases>\n" +
		"</component>\n"
}

func parseHex32(s string) ([32]byte, error) {
	var out [32]byte
	if len(s) != 64 {
		return out, errors.New("invalid SHA-256 digest length in Flatpak base")
	}
	raw, err := hex.DecodeString(s)
	if err != nil || len(raw) != 32 {
		return out, errors.New("invalid SHA-256 digest hex in Flatpak base")
	}
	copy(out[:], raw)
	return out, nil
}

func parseDirRef(name string, ref flatpakDirRef) (ostreeDirEntry, error) {
	tree, err := parseHex32(ref.Tree)
	if err != nil {
		return ostreeDirEntry{}, err
	}
	meta, err := parseHex32(ref.Meta)
	if err != nil {
		return ostreeDirEntry{}, err
	}
	return ostreeDirEntry{Name: name, TreeDigest: tree, MetaDigest: meta}, nil
}

func parseFlatpakBase(raw []byte) (*parsedFlatpakBase, error) {
	if len(raw) < 16 || string(raw[:8]) != flatpakBaseMagic {
		return nil, errors.New("base must be a Shiraz Flatpak base (.flatpak-base)")
	}
	ver := binary.LittleEndian.Uint32(raw[8:12])
	if ver != flatpakBaseVersion {
		return nil, fmt.Errorf("unsupported Flatpak base format version %d", ver)
	}
	indexLen := binary.LittleEndian.Uint32(raw[12:16])
	if indexLen == 0 || indexLen > 16<<20 || uint64(16)+uint64(indexLen) > uint64(len(raw)) {
		return nil, errors.New("corrupt Flatpak base header")
	}
	var idx flatpakBaseIndex
	if err := json.Unmarshal(raw[16:16+indexLen], &idx); err != nil {
		return nil, fmt.Errorf("invalid Flatpak base index: %w", err)
	}
	if idx.Architecture != "x86_64" && idx.Architecture != "aarch64" {
		return nil, errors.New("unsupported Flatpak base architecture")
	}
	if idx.RuntimeBranch == "" {
		return nil, errors.New("missing Flatpak runtime branch")
	}
	dirmeta, err := parseHex32(idx.Dirmeta0755SHA256)
	if err != nil {
		return nil, err
	}
	part0Bytes := raw[16+indexLen:]
	if uint64(len(part0Bytes)) != idx.Part0.CompressedSize {
		return nil, errors.New("Flatpak base payload size mismatch")
	}
	expectedPart0Sum, err := parseHex32(idx.Part0.SHA256)
	if err != nil {
		return nil, err
	}
	actualPart0Sum := sha256.Sum256(part0Bytes)
	if actualPart0Sum != expectedPart0Sum {
		return nil, errors.New("Flatpak base payload checksum mismatch")
	}
	part0Objects, err := base64.StdEncoding.DecodeString(idx.Part0.ObjectsB64)
	if err != nil || len(part0Objects) == 0 || len(part0Objects)%33 != 0 {
		return nil, errors.New("invalid Flatpak base object index")
	}
	binDir, err := parseDirRef("bin", idx.FilesRoot.Bin)
	if err != nil {
		return nil, err
	}
	usrBinDir, err := parseDirRef("bin", idx.FilesRoot.UsrBin)
	if err != nil {
		return nil, err
	}
	usrLibDir, err := parseDirRef("lib", idx.FilesRoot.UsrLib)
	if err != nil {
		return nil, err
	}
	usrArtDir, err := parseDirRef("art", idx.FilesRoot.UsrArt)
	if err != nil {
		return nil, err
	}
	shirazFiles := make([]ostreeFileEntry, 0, len(idx.FilesRoot.UsrShareShirazStaticFiles))
	for _, pair := range idx.FilesRoot.UsrShareShirazStaticFiles {
		if len(pair) != 2 || pair[0] == "" {
			return nil, errors.New("invalid static file entry in Flatpak base")
		}
		d, err := parseHex32(pair[1])
		if err != nil {
			return nil, err
		}
		shirazFiles = append(shirazFiles, ostreeFileEntry{Name: pair[0], Digest: d})
	}
	shirazDirs := make([]ostreeDirEntry, 0, len(idx.FilesRoot.UsrShareShirazStaticDirs))
	for _, triple := range idx.FilesRoot.UsrShareShirazStaticDirs {
		if len(triple) != 3 || triple[0] == "" {
			return nil, errors.New("invalid static dir entry in Flatpak base")
		}
		t, err := parseHex32(triple[1])
		if err != nil {
			return nil, err
		}
		m, err := parseHex32(triple[2])
		if err != nil {
			return nil, err
		}
		shirazDirs = append(shirazDirs, ostreeDirEntry{Name: triple[0], TreeDigest: t, MetaDigest: m})
	}
	otherShare := make([]ostreeDirEntry, 0, len(idx.FilesRoot.UsrShareOtherDirs))
	for _, triple := range idx.FilesRoot.UsrShareOtherDirs {
		if len(triple) != 3 || triple[0] == "" {
			return nil, errors.New("invalid share dir entry in Flatpak base")
		}
		t, err := parseHex32(triple[1])
		if err != nil {
			return nil, err
		}
		m, err := parseHex32(triple[2])
		if err != nil {
			return nil, err
		}
		otherShare = append(otherShare, ostreeDirEntry{Name: triple[0], TreeDigest: t, MetaDigest: m})
	}
	return &parsedFlatpakBase{
		index:       idx,
		dirmeta0755: dirmeta,
		part0: ostreeDeltaPart{
			PartBytes:        part0Bytes,
			SHA256:           actualPart0Sum,
			CompressedSize:   uint64(len(part0Bytes)),
			UncompressedSize: idx.Part0.UncompressedSize,
			Objects:          part0Objects,
		},
		binDir:      binDir,
		usrBinDir:   usrBinDir,
		usrLibDir:   usrLibDir,
		usrArtDir:   usrArtDir,
		shirazFiles: shirazFiles,
		shirazDirs:  shirazDirs,
		otherShare:  otherShare,
	}, nil
}

func iconCandidateScore(name string) int {
	lower := strings.ToLower(name)
	if !strings.HasSuffix(lower, ".png") || (!strings.HasPrefix(lower, "res/mipmap") && !strings.HasPrefix(lower, "res/drawable")) {
		return 0
	}
	base := path.Base(lower)
	if !strings.Contains(base, "ic_launcher") && !strings.Contains(base, "app_icon") {
		return 0
	}
	score := 10
	if base == "ic_launcher.png" {
		score += 50
	} else if base == "ic_launcher_round.png" {
		score += 40
	}
	switch {
	case strings.Contains(lower, "xxxhdpi"):
		score += 40
	case strings.Contains(lower, "xxhdpi"):
		score += 35
	case strings.Contains(lower, "xhdpi"):
		score += 30
	case strings.Contains(lower, "hdpi"):
		score += 20
	case strings.Contains(lower, "mdpi"):
		score += 10
	}
	return score
}

func extractAPKIconPNG(apkZip *zip.Reader) ([]byte, bool) {
	var best *zip.File
	bestScore := 0
	for _, f := range apkZip.File {
		if f.FileInfo().IsDir() || f.Flags&1 != 0 || f.UncompressedSize64 == 0 || f.UncompressedSize64 > 4<<20 {
			continue
		}
		score := iconCandidateScore(f.Name)
		if score > bestScore || (score > 0 && score == bestScore && best != nil && f.UncompressedSize64 > best.UncompressedSize64) {
			best = f
			bestScore = score
		}
	}
	if best == nil {
		return nil, false
	}
	stream, err := best.Open()
	if err != nil {
		return nil, false
	}
	defer stream.Close()
	data, err := io.ReadAll(io.LimitReader(stream, 4<<20+1))
	if err != nil || len(data) < 24 || len(data) > 4<<20 {
		return nil, false
	}
	if string(data[:8]) != "\x89PNG\r\n\x1a\n" {
		return nil, false
	}
	return data, true
}

func buildFlatpakFromApk(baseBytes, apkBytes []byte, version string, opts flatpakBuildOptions) (*flatpakBuildResult, error) {
	base, err := parseFlatpakBase(baseBytes)
	if err != nil {
		return nil, err
	}
	architecture := base.index.Architecture
	meta, err := inspectAPK(apkBytes, version, architecture)
	if err != nil {
		return nil, err
	}
	if !javaName.MatchString(meta.Package) || !javaName.MatchString(meta.Application) || !javaName.MatchString(meta.Activity) || !versionName.MatchString(meta.Version) {
		return nil, errors.New("invalid package, component, or version metadata")
	}
	abi, machine, err := targetABI(architecture)
	if err != nil {
		return nil, err
	}
	apkZip, err := zip.NewReader(bytes.NewReader(apkBytes), int64(len(apkBytes)))
	if err != nil {
		return nil, err
	}
	var nativeFiles []*zip.File
	seen := map[string]bool{}
	var nativeBytes uint64
	hasNative := false
	for _, file := range apkZip.File {
		if strings.HasPrefix(file.Name, "lib/") && strings.HasSuffix(file.Name, ".so") {
			hasNative = true
		}
		if strings.HasPrefix(file.Name, "lib/"+abi+"/") && !file.FileInfo().IsDir() {
			name := strings.TrimPrefix(file.Name, "lib/"+abi+"/")
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
	if hasNative && len(nativeFiles) == 0 {
		return nil, fmt.Errorf("APK has no %s native library candidate", abi)
	}

	apkSum := sha256.Sum256(apkBytes)
	launchProps := fmt.Sprintf(
		"activity=%s\naot=false\napplication=%s\nnative=%t\npackage=%s\nsha256=%x\nversion=%s\n",
		property(meta.Activity),
		property(meta.Application),
		len(nativeFiles) > 0,
		property(meta.Package),
		apkSum,
		property(meta.Version),
	)
	appID := flatpakAppID(meta.Package)
	title := flatpakTitle(meta.Package)
	metadataINI := flatpakMetadataINI(appID, architecture, base.index.RuntimeBranch, opts.AllowNetwork)
	desktop := flatpakDesktopEntry(appID, title)
	metainfo := flatpakMetainfoXML(appID, title, meta.Package, version)

	part1 := newDeltaPartBuilder()
	var addedInstalled uint64
	addFile := func(content []byte) [32]byte {
		addedInstalled += uint64(len(content))
		return part1.addFile(ostreeModeIFREG|0o644, content)
	}

	// APK & properties under /files/usr/share/shiraz
	apkObj := addFile(apkBytes)
	launchObj := addFile([]byte(launchProps))
	appPropsObj := addFile([]byte(meta.ApplicationProperties))

	shirazFiles := make([]ostreeFileEntry, 0, len(base.shirazFiles)+3)
	shirazFiles = append(shirazFiles, base.shirazFiles...)
	shirazFiles = append(shirazFiles,
		ostreeFileEntry{Name: "original.apk", Digest: apkObj},
		ostreeFileEntry{Name: "launch.properties", Digest: launchObj},
		ostreeFileEntry{Name: "application.properties", Digest: appPropsObj},
	)
	shirazTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(shirazFiles, base.shirazDirs))

	// /files/usr/share
	usrShareDirs := make([]ostreeDirEntry, 0, len(base.otherShare)+1)
	usrShareDirs = append(usrShareDirs, base.otherShare...)
	usrShareDirs = append(usrShareDirs, ostreeDirEntry{Name: "shiraz", TreeDigest: shirazTree, MetaDigest: base.dirmeta0755})
	usrShareTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(nil, usrShareDirs))

	// /files/usr (plus /files/usr/android-lib if native libraries exist)
	usrDirs := []ostreeDirEntry{
		base.usrArtDir,
		base.usrBinDir,
		base.usrLibDir,
		{Name: "share", TreeDigest: usrShareTree, MetaDigest: base.dirmeta0755},
	}
	if len(nativeFiles) > 0 {
		nativeEntries := make([]ostreeFileEntry, 0, len(nativeFiles))
		for _, file := range nativeFiles {
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
			if len(data) < 20 || string(data[:6]) != "\x7fELF\x02\x01" || data[16] != 3 || data[17] != 0 || data[18] != machine || data[19] != 0 {
				return nil, errors.New("native library architecture does not match the base")
			}
			nativeEntries = append(nativeEntries, ostreeFileEntry{
				Name:   path.Base(file.Name),
				Digest: addFile(data),
			})
		}
		androidLibTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(nativeEntries, nil))
		usrDirs = append(usrDirs, ostreeDirEntry{Name: "android-lib", TreeDigest: androidLibTree, MetaDigest: base.dirmeta0755})
	}
	usrTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(nil, usrDirs))

	// Exported desktop entry, icon, and metainfo (shared by /export/share and /files/share)
	desktopObj := addFile([]byte(desktop))
	appsTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(
		[]ostreeFileEntry{{Name: appID + ".desktop", Digest: desktopObj}},
		nil,
	))
	metainfoObj := addFile([]byte(metainfo))
	metainfoTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(
		[]ostreeFileEntry{{Name: appID + ".metainfo.xml", Digest: metainfoObj}},
		nil,
	))

	var iconSizeDir, iconFileName string
	var iconBytes []byte
	if pngBytes, ok := extractAPKIconPNG(apkZip); ok {
		iconSizeDir = "256x256"
		iconFileName = appID + ".png"
		iconBytes = pngBytes
	} else {
		iconSizeDir = "scalable"
		iconFileName = appID + ".svg"
		iconBytes = []byte(fallbackShirazIconSVG)
	}
	iconObj := addFile(iconBytes)
	iconAppsTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(
		[]ostreeFileEntry{{Name: iconFileName, Digest: iconObj}},
		nil,
	))
	iconSizeTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(
		nil,
		[]ostreeDirEntry{{Name: "apps", TreeDigest: iconAppsTree, MetaDigest: base.dirmeta0755}},
	))
	hicolorTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(
		nil,
		[]ostreeDirEntry{{Name: iconSizeDir, TreeDigest: iconSizeTree, MetaDigest: base.dirmeta0755}},
	))
	iconsTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(
		nil,
		[]ostreeDirEntry{{Name: "hicolor", TreeDigest: hicolorTree, MetaDigest: base.dirmeta0755}},
	))

	exportedShareTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(
		nil,
		[]ostreeDirEntry{
			{Name: "applications", TreeDigest: appsTree, MetaDigest: base.dirmeta0755},
			{Name: "icons", TreeDigest: iconsTree, MetaDigest: base.dirmeta0755},
			{Name: "metainfo", TreeDigest: metainfoTree, MetaDigest: base.dirmeta0755},
		},
	))
	exportTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(
		nil,
		[]ostreeDirEntry{{Name: "share", TreeDigest: exportedShareTree, MetaDigest: base.dirmeta0755}},
	))
	filesTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(
		nil,
		[]ostreeDirEntry{
			base.binDir,
			{Name: "share", TreeDigest: exportedShareTree, MetaDigest: base.dirmeta0755},
			{Name: "usr", TreeDigest: usrTree, MetaDigest: base.dirmeta0755},
		},
	))
	metadataObj := addFile([]byte(metadataINI))
	rootTree := part1.addMeta(ostreeObjectDirTree, ostreeDirtreeBytes(
		[]ostreeFileEntry{{Name: "metadata", Digest: metadataObj}},
		[]ostreeDirEntry{
			{Name: "export", TreeDigest: exportTree, MetaDigest: base.dirmeta0755},
			{Name: "files", TreeDigest: filesTree, MetaDigest: base.dirmeta0755},
		},
	))

	part1Built := part1.finishUncompressed()
	ts := opts.Timestamp
	if ts == 0 {
		ts = uint64(time.Now().Unix())
	}
	ref := "app/" + appID + "/" + architecture + "/stable"
	totalInstalled := base.index.InstalledSize + addedInstalled
	totalDownload := base.part0.CompressedSize + part1Built.CompressedSize
	commitVal := ostreeCommitValue(
		rootTree,
		base.dirmeta0755,
		ref,
		metadataINI,
		totalInstalled,
		totalDownload,
		"Export "+appID,
		ts,
	)
	bundle := buildSuperblock(
		ref,
		metadataINI,
		commitVal,
		[]ostreeDeltaPart{base.part0, part1Built},
		ts,
		base.index.RuntimeRepo,
	)
	bundleSum := sha256.Sum256(bundle)
	return &flatpakBuildResult{
		Bytes:        bundle,
		SHA256:       hex.EncodeToString(bundleSum[:]),
		Package:      meta.Package,
		AppID:        appID,
		Architecture: architecture,
		Filename:     fmt.Sprintf("%s-Shiraz-%s-%s.flatpak", appID, version, architecture),
	}, nil
}
