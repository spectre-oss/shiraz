// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
)

type libraryApp struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	File      string `json:"file"`
	Version   string `json:"version"`
	Note      string `json:"note"`
	Available bool   `json:"available"`
}

func (s *server) apps() []libraryApp {
	apps := []libraryApp{{ID: "probe", Name: "Android API Probe", File: "build/android-api-probe/api-probe-unsigned.apk", Note: "200 checks and manual procedures. Some APIs remain unsupported."}}
	var catalog struct {
		APKs []struct{ File, Name, Version string }
	}
	if data, err := os.ReadFile(filepath.Join(s.root, "config/compatibility-apks.json")); err == nil && json.Unmarshal(data, &catalog) == nil {
		for i, a := range catalog.APKs {
			if filepath.Base(a.File) != a.File || filepath.Ext(a.File) != ".apk" {
				continue
			}
			apps = append(apps, libraryApp{ID: fmt.Sprintf("corpus-%d", i), Name: a.Name, Version: a.Version, File: filepath.Join("context/apks", a.File), Note: "Compatibility test APK. Successful launch and functionality are not guaranteed."})
		}
	}
	for i := range apps {
		info, err := os.Stat(filepath.Join(s.root, apps[i].File))
		apps[i].Available = err == nil && info.Mode().IsRegular() && info.Size() > 0 && info.Size() <= maxAPK
	}
	return apps
}
func (s *server) library(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	json.NewEncoder(w).Encode(s.apps())
}
func (s *server) launchLibrary(w http.ResponseWriter, r *http.Request) {
	if r.Header.Get("X-Shiraz-Launch") != "1" {
		http.Error(w, "launcher request required", 403)
		return
	}
	var selected *libraryApp
	for _, a := range s.apps() {
		if a.ID == r.PathValue("id") {
			copy := a
			selected = &copy
			break
		}
	}
	if selected == nil {
		http.NotFound(w, r)
		return
	}
	if !selected.Available {
		http.Error(w, "APK missing: build the API probe or run make compatibility-apks", 404)
		return
	}
	s.mu.Lock()
	if s.running || s.stopping {
		s.mu.Unlock()
		http.Error(w, "an APK is already running or server is stopping", 409)
		return
	}
	s.running = true
	s.mu.Unlock()
	accepted := false
	defer func() {
		if !accepted {
			s.mu.Lock()
			s.running = false
			s.mu.Unlock()
		}
	}()
	dir, err := os.MkdirTemp(filepath.Join(s.root, "build"), "web-library-")
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	defer func() {
		if !accepted {
			os.RemoveAll(dir)
		}
	}()
	source, err := os.Open(filepath.Join(s.root, selected.File))
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	defer source.Close()
	path := filepath.Join(dir, "app.apk")
	out, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	hash := sha256.New()
	n, copyErr := io.Copy(io.MultiWriter(out, hash), io.LimitReader(source, maxAPK+1))
	closeErr := out.Close()
	if copyErr != nil || closeErr != nil || n == 0 || n > maxAPK {
		http.Error(w, "unable to copy bounded APK", 400)
		return
	}
	if err := validAPK(path); err != nil {
		http.Error(w, "invalid APK: "+err.Error(), 400)
		return
	}
	accepted = true
	s.acceptRun(w, dir, path, selected.Name, fmt.Sprintf("%x", hash.Sum(nil)))
}
