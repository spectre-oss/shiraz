// SPDX-License-Identifier: GPL-3.0-only
// This launcher reads a browser-appended APK overlay and runs Shiraz from a
// temporary AppDir whose large runtime files remain linked to the mounted image.
package main

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"crypto/sha256"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

func payload(image string) ([]byte, error) {
	file, err := os.Open(image)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	stat, err := file.Stat()
	if err != nil {
		return nil, err
	}
	if stat.Size() < 48 {
		return nil, errors.New("missing Shiraz payload")
	}
	footer := make([]byte, 48)
	if _, err = file.ReadAt(footer, stat.Size()-48); err != nil {
		return nil, err
	}
	if string(footer[40:]) != "SHIRAZ01" {
		return nil, errors.New("missing Shiraz payload footer")
	}
	size := binary.LittleEndian.Uint64(footer[32:40])
	if size == 0 || size > 1<<30 || int64(size) > stat.Size()-48 {
		return nil, errors.New("invalid Shiraz payload size")
	}
	data := make([]byte, int(size))
	if _, err = file.ReadAt(data, stat.Size()-48-int64(size)); err != nil {
		return nil, err
	}
	digest := sha256.Sum256(data)
	if !bytes.Equal(digest[:], footer[:32]) {
		return nil, errors.New("Shiraz payload checksum mismatch")
	}
	return data, nil
}

func mirror(source, destination string) error {
	return filepath.WalkDir(source, func(src string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		relative, err := filepath.Rel(source, src)
		if err != nil {
			return err
		}
		dst := filepath.Join(destination, relative)
		if entry.IsDir() {
			return os.MkdirAll(dst, 0700)
		}
		if entry.Type()&os.ModeSymlink != 0 {
			target, err := os.Readlink(src)
			if err != nil {
				return err
			}
			return os.Symlink(target, dst)
		}
		input, err := os.Open(src)
		if err != nil {
			return err
		}
		defer input.Close()
		output, err := os.OpenFile(dst, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0700)
		if err != nil {
			return err
		}
		if _, err = io.Copy(output, input); err != nil {
			output.Close()
			return err
		}
		return output.Close()
	})
}

func permitted(name string) bool {
	switch name {
	case "usr/share/shiraz/original.apk", "usr/share/shiraz/launch.properties", "usr/share/shiraz/application.properties", "shiraz.desktop", ".DirIcon":
		return true
	}
	return strings.HasPrefix(name, "usr/android-lib/") && strings.HasSuffix(name, ".so") && !strings.Contains(strings.TrimPrefix(name, "usr/android-lib/"), "/")
}

func overlay(root string, data []byte) error {
	gz, err := gzip.NewReader(bytes.NewReader(data))
	if err != nil {
		return err
	}
	defer gz.Close()
	reader := tar.NewReader(gz)
	seen := map[string]bool{}
	for {
		header, err := reader.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return err
		}
		name := filepath.Clean(header.Name)
		if !permitted(name) || seen[name] {
			return fmt.Errorf("invalid overlay path: %s", name)
		}
		seen[name] = true
		dst := filepath.Join(root, name)
		if err = os.MkdirAll(filepath.Dir(dst), 0700); err != nil {
			return err
		}
		if err = os.Remove(dst); err != nil && !os.IsNotExist(err) {
			return err
		}
		switch header.Typeflag {
		case tar.TypeReg, tar.TypeRegA:
			if header.Size < 0 || header.Size > 512<<20 {
				return errors.New("overlay file exceeds limit")
			}
			out, err := os.OpenFile(dst, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
			if err != nil {
				return err
			}
			_, err = io.CopyN(out, reader, header.Size)
			closeErr := out.Close()
			if err != nil {
				return err
			}
			if closeErr != nil {
				return closeErr
			}
		case tar.TypeSymlink:
			if name != ".DirIcon" || header.Linkname != "shiraz-icon.svg" {
				return errors.New("invalid overlay symlink")
			}
			if err = os.Symlink(header.Linkname, dst); err != nil {
				return err
			}
		default:
			return errors.New("unsupported overlay entry")
		}
	}
	for _, name := range []string{"usr/share/shiraz/original.apk", "usr/share/shiraz/launch.properties", "usr/share/shiraz/application.properties"} {
		if !seen[name] {
			return errors.New("missing overlay file: " + name)
		}
	}
	return nil
}

func run() error {
	image := os.Getenv("APPIMAGE")
	if image == "" {
		return errors.New("APPIMAGE path not supplied by runtime")
	}
	data, err := payload(image)
	if err != nil {
		return err
	}
	executable, err := os.Executable()
	if err != nil {
		return err
	}
	mounted := filepath.Dir(filepath.Dir(filepath.Dir(executable)))
	cache := os.Getenv("XDG_CACHE_HOME")
	if cache == "" {
		home, err := os.UserHomeDir()
		if err != nil {
			return err
		}
		cache = filepath.Join(home, ".cache")
	}
	if err = os.MkdirAll(cache, 0700); err != nil {
		return err
	}
	dir, err := os.MkdirTemp(cache, "shiraz-browser-run-")
	if err != nil {
		return err
	}
	defer os.RemoveAll(dir)
	if err = mirror(mounted, dir); err != nil {
		return err
	}
	if err = overlay(dir, data); err != nil {
		return err
	}
	loader := filepath.Join(dir, "usr/lib/ld-linux-x86-64.so.2")
	lib := filepath.Join(dir, "usr/lib")
	binary := filepath.Join(dir, "usr/bin/shiraz-app")
	args := append([]string{"--library-path", lib, binary}, os.Args[1:]...)
	command := exec.Command(loader, args...)
	command.Stdin = os.Stdin
	command.Stdout = os.Stdout
	command.Stderr = os.Stderr
	command.Env = os.Environ()
	if err = command.Run(); err != nil {
		return err
	}
	return nil
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "Shiraz browser launcher:", err)
		if failure, ok := err.(*exec.ExitError); ok {
			os.Exit(failure.ExitCode())
		}
		os.Exit(1)
	}
}
