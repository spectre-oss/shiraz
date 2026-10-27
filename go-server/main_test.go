// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/zip"
	"os"
	"path/filepath"
	"testing"
)

func TestValidAPK(t *testing.T) {
	path := filepath.Join(t.TempDir(), "test.apk")
	file, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	archive := zip.NewWriter(file)
	for _, name := range []string{"AndroidManifest.xml", "classes.dex"} {
		entry, err := archive.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := entry.Write([]byte("fixture")); err != nil {
			t.Fatal(err)
		}
	}
	if err := archive.Close(); err != nil {
		t.Fatal(err)
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}
	if err := validAPK(path); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte("invalid"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := validAPK(path); err == nil {
		t.Fatal("invalid ZIP accepted")
	}
}
