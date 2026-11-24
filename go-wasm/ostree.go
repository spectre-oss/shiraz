// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/base64"
	"encoding/binary"
	"sort"
	"strconv"
	"strings"
)

const (
	flatpakMagicU32            uint32 = 0xe5890001
	ostreeObjectFile           byte   = 1
	ostreeObjectDirTree        byte   = 2
	ostreeObjectDirMeta        byte   = 3
	ostreeOpOpenSpliceAndClose byte   = 'S'
	ostreeDeltapartVersion     uint32 = 0
	ostreeModeIFREG            uint32 = 0o100000
	ostreeModeIFLNK            uint32 = 0o120000
	ostreeModeIFDIR            uint32 = 0o040000
)

type ostreeFileEntry struct {
	Name   string
	Digest [32]byte
}

type ostreeDirEntry struct {
	Name       string
	TreeDigest [32]byte
	MetaDigest [32]byte
}

type ostreeDeltaPart struct {
	PartBytes        []byte
	SHA256           [32]byte
	CompressedSize   uint64
	UncompressedSize uint64
	Objects          []byte
}

func ostreeWriteVaruint64(buf *bytes.Buffer, n uint64) {
	for {
		b := byte(n & 0x7f)
		n >>= 7
		if n != 0 {
			buf.WriteByte(b | 0x80)
		} else {
			buf.WriteByte(b)
			return
		}
	}
}

func ostreeChecksumB64(digest [32]byte) string {
	encoded := base64.StdEncoding.EncodeToString(digest[:])
	encoded = strings.TrimRight(encoded, "=")
	return strings.ReplaceAll(encoded, "/", "_")
}

func ostreeDeltaPartKey(toDigest [32]byte, partIndex int) string {
	b64 := ostreeChecksumB64(toDigest)
	return "deltas/" + b64[:2] + "/" + b64[2:] + "/" + strconv.Itoa(partIndex)
}

func ostreeDirmetaBytes(mode uint32) []byte {
	emptyXattrs := gvArray("(ayay)", 1, false, nil)
	return gvTuple(gvU32BE(0), gvU32BE(0), gvU32BE(mode), emptyXattrs).data
}

func ostreeFileChecksum(mode uint32, content []byte, symlinkTarget string) [32]byte {
	emptyXattrs := gvArray("(ayay)", 1, false, nil)
	header := gvTuple(
		gvU32BE(0),
		gvU32BE(0),
		gvU32BE(mode),
		gvU32BE(0),
		gvString(symlinkTarget),
		emptyXattrs,
	).data
	var prefix [8]byte
	binary.BigEndian.PutUint32(prefix[:4], uint32(len(header)))
	h := sha256.New()
	h.Write(prefix[:])
	h.Write(header)
	if mode&0o170000 == ostreeModeIFREG {
		h.Write(content)
	}
	var out [32]byte
	copy(out[:], h.Sum(nil))
	return out
}

func ostreeDirtreeBytes(files []ostreeFileEntry, dirs []ostreeDirEntry) []byte {
	sortedFiles := append([]ostreeFileEntry(nil), files...)
	sort.Slice(sortedFiles, func(i, j int) bool { return sortedFiles[i].Name < sortedFiles[j].Name })
	sortedDirs := append([]ostreeDirEntry(nil), dirs...)
	sort.Slice(sortedDirs, func(i, j int) bool { return sortedDirs[i].Name < sortedDirs[j].Name })

	gvFiles := make([]gvValue, 0, len(sortedFiles))
	for _, f := range sortedFiles {
		gvFiles = append(gvFiles, gvTuple(gvString(f.Name), gvBytes(f.Digest[:])))
	}
	gvDirs := make([]gvValue, 0, len(sortedDirs))
	for _, d := range sortedDirs {
		gvDirs = append(gvDirs, gvTuple(gvString(d.Name), gvBytes(d.TreeDigest[:]), gvBytes(d.MetaDigest[:])))
	}
	return gvTuple(
		gvArray("(say)", 1, false, gvFiles),
		gvArray("(sayay)", 1, false, gvDirs),
	).data
}

func ostreeCommitValue(
	rootTree, rootMeta [32]byte,
	ref, metadataINI string,
	installedSize, downloadSize uint64,
	subject string,
	timestamp uint64,
) gvValue {
	entries := []gvValue{
		gvDictEntry(gvString("ostree.collection-binding"), gvVariant(gvString(""))),
		gvDictEntry(gvString("ostree.ref-binding"), gvVariant(gvArray("s", 1, false, []gvValue{gvString(ref)}))),
		gvDictEntry(gvString("xa.ref"), gvVariant(gvString(ref))),
		gvDictEntry(gvString("xa.metadata"), gvVariant(gvString(metadataINI))),
		gvDictEntry(gvString("xa.installed-size"), gvVariant(gvU64BE(installedSize))),
		gvDictEntry(gvString("xa.download-size"), gvVariant(gvU64BE(downloadSize))),
	}
	return gvTuple(
		gvArray("{sv}", 8, false, entries),
		gvBytes(nil),
		gvArray("(say)", 1, false, nil),
		gvString(subject),
		gvString(""),
		gvU64BE(timestamp),
		gvBytes(rootTree[:]),
		gvBytes(rootMeta[:]),
	)
}

type ostreeObjectKey struct {
	objType byte
	digest  [32]byte
}

type ostreeModeKey struct {
	uid, gid, mode uint32
}

type deltaPartBuilder struct {
	modes            []ostreeModeKey
	modeIndex        map[ostreeModeKey]uint64
	payload          bytes.Buffer
	ops              bytes.Buffer
	objects          bytes.Buffer
	seen             map[ostreeObjectKey]bool
	uncompressedSize uint64
}

func newDeltaPartBuilder() *deltaPartBuilder {
	return &deltaPartBuilder{
		modeIndex: map[ostreeModeKey]uint64{},
		seen:      map[ostreeObjectKey]bool{},
	}
}

func (b *deltaPartBuilder) internMode(uid, gid, mode uint32) uint64 {
	key := ostreeModeKey{uid, gid, mode}
	if idx, ok := b.modeIndex[key]; ok {
		return idx
	}
	idx := uint64(len(b.modes))
	b.modeIndex[key] = idx
	b.modes = append(b.modes, key)
	return idx
}

func (b *deltaPartBuilder) addMeta(objType byte, data []byte) [32]byte {
	digest := sha256.Sum256(data)
	key := ostreeObjectKey{objType: objType, digest: digest}
	if b.seen[key] {
		return digest
	}
	b.seen[key] = true
	offset := uint64(b.payload.Len())
	b.payload.Write(data)
	b.ops.WriteByte(ostreeOpOpenSpliceAndClose)
	ostreeWriteVaruint64(&b.ops, uint64(len(data)))
	ostreeWriteVaruint64(&b.ops, offset)
	b.objects.WriteByte(objType)
	b.objects.Write(digest[:])
	b.uncompressedSize += uint64(len(data))
	return digest
}

func (b *deltaPartBuilder) addFile(mode uint32, content []byte) [32]byte {
	digest := ostreeFileChecksum(mode, content, "")
	key := ostreeObjectKey{objType: ostreeObjectFile, digest: digest}
	if b.seen[key] {
		return digest
	}
	b.seen[key] = true
	modeIdx := b.internMode(0, 0, mode)
	offset := uint64(b.payload.Len())
	b.payload.Write(content)
	b.ops.WriteByte(ostreeOpOpenSpliceAndClose)
	ostreeWriteVaruint64(&b.ops, modeIdx)
	ostreeWriteVaruint64(&b.ops, 0) // empty xattrs index 0
	ostreeWriteVaruint64(&b.ops, uint64(len(content)))
	ostreeWriteVaruint64(&b.ops, offset)
	b.objects.WriteByte(ostreeObjectFile)
	b.objects.Write(digest[:])
	b.uncompressedSize += uint64(len(content))
	return digest
}

func (b *deltaPartBuilder) finishUncompressed() ostreeDeltaPart {
	gvModes := make([]gvValue, 0, len(b.modes))
	for _, m := range b.modes {
		gvModes = append(gvModes, gvTuple(gvU32BE(m.uid), gvU32BE(m.gid), gvU32BE(m.mode)))
	}
	gvXattrs := gvArray("a(ayay)", 1, false, []gvValue{gvArray("(ayay)", 1, false, nil)})
	payloadBody := gvTuple(
		gvArray("(uuu)", 4, true, gvModes),
		gvXattrs,
		gvBytes(b.payload.Bytes()),
		gvBytes(b.ops.Bytes()),
	).data
	partBytes := make([]byte, 1+len(payloadBody))
	partBytes[0] = 0 // uncompressed delta part
	copy(partBytes[1:], payloadBody)
	sum := sha256.Sum256(partBytes)
	return ostreeDeltaPart{
		PartBytes:        partBytes,
		SHA256:           sum,
		CompressedSize:   uint64(len(partBytes)),
		UncompressedSize: b.uncompressedSize,
		Objects:          append([]byte(nil), b.objects.Bytes()...),
	}
}

func buildSuperblock(
	ref, metadataINI string,
	commitVal gvValue,
	parts []ostreeDeltaPart,
	timestamp uint64,
	runtimeRepo string,
) []byte {
	toDigest := sha256.Sum256(commitVal.data)
	dictEntries := []gvValue{
		// Flatpak MIME header must appear first in metadata a{sv}.
		gvDictEntry(gvString("flatpak"), gvVariant(gvU32LE(flatpakMagicU32))),
		gvDictEntry(gvString("ref"), gvVariant(gvString(ref))),
		gvDictEntry(gvString("metadata"), gvVariant(gvString(metadataINI))),
	}
	if runtimeRepo != "" {
		dictEntries = append(dictEntries, gvDictEntry(gvString("runtime-repo"), gvVariant(gvString(runtimeRepo))))
	}
	dictEntries = append(dictEntries,
		gvDictEntry(gvString("collection-id"), gvVariant(gvString(""))),
		gvDictEntry(gvString("ostree.endianness"), gvVariant(gvByte('l'))),
	)

	partMetas := make([]gvValue, 0, len(parts))
	for i, part := range parts {
		partKey := ostreeDeltaPartKey(toDigest, i)
		partGV := gvValue{sig: "(yay)", align: 1, fixed: false, data: part.PartBytes}
		dictEntries = append(dictEntries, gvDictEntry(gvString(partKey), gvVariant(partGV)))
		partMetas = append(partMetas, gvTuple(
			gvU32LE(ostreeDeltapartVersion),
			gvBytes(part.SHA256[:]),
			gvU64LE(part.CompressedSize),
			gvU64LE(part.UncompressedSize),
			gvBytes(part.Objects),
		))
	}

	return gvTuple(
		gvArray("{sv}", 8, false, dictEntries),
		gvU64BE(timestamp),
		gvBytes(nil),
		gvBytes(toDigest[:]),
		commitVal,
		gvBytes(nil),
		gvArray("(uayttay)", 8, false, partMetas),
		gvArray("(yaytt)", 8, false, nil),
	).data
}
