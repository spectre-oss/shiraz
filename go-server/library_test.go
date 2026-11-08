// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestLibraryAvailabilityAndLaunchGuards(t *testing.T) {
	root := t.TempDir()
	s := &server{root: root}
	if apps := s.apps(); len(apps) != 1 || apps[0].Available {
		t.Fatal(apps)
	}
	path := filepath.Join(root, "build/android-api-probe")
	os.MkdirAll(path, 0700)
	os.WriteFile(filepath.Join(path, "api-probe-unsigned.apk"), []byte("invalid"), 0600)
	if !s.apps()[0].Available {
		t.Fatal("local file not found")
	}
	for _, test := range []struct {
		id, header string
		busy       bool
		want       int
	}{{"probe", "", false, 403}, {"../../outside", "1", false, 404}, {"probe", "1", true, 409}, {"probe", "1", false, 400}} {
		s.running = test.busy
		r := httptest.NewRequest(http.MethodPost, "/apps/test/run", nil)
		r.SetPathValue("id", test.id)
		r.Header.Set("X-Shiraz-Launch", test.header)
		w := httptest.NewRecorder()
		s.launchLibrary(w, r)
		if w.Code != test.want {
			t.Fatalf("%s: got %d want %d", test.id, w.Code, test.want)
		}
	}
	if s.running {
		t.Fatal("failed launch retained run lock")
	}
}
