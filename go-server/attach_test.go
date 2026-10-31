// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestAttachedLifecycleAndInput(t *testing.T) {
	dir := t.TempDir()
	input := filepath.Join(dir, "input.txt")
	active := filepath.Join(dir, "active")
	if err := os.WriteFile(input, nil, 0600); err != nil {
		t.Fatal(err)
	}
	s := &server{attach: dir}
	s.refreshAttached(time.Now())
	if s.running {
		t.Fatal("missing heartbeat considered active")
	}
	// Attached mode must reject uploads even while the external runtime is absent.
	w := httptest.NewRecorder()
	s.upload(w, httptest.NewRequest(http.MethodPost, "/upload", strings.NewReader("invalid")))
	if w.Code != http.StatusConflict {
		t.Fatalf("upload status %d", w.Code)
	}
	if err := os.WriteFile(active, nil, 0600); err != nil {
		t.Fatal(err)
	}
	s.refreshAttached(time.Now())
	if !s.running || s.frame != filepath.Join(dir, "frame.ppm") {
		t.Fatal("fresh session not attached")
	}
	if err := s.sendInput(command{Action: "tab"}); err != nil {
		t.Fatal(err)
	}
	if err := s.sendInput(command{Action: "stop"}); err != nil {
		t.Fatal(err)
	}
	bytes, err := os.ReadFile(input)
	if err != nil {
		t.Fatal(err)
	}
	if string(bytes) != "keydown \"Tab\"\nkeyup \"Tab\"\nclose\n" {
		t.Fatalf("normal input path: %q", bytes)
	}
	old := time.Now().Add(-time.Minute)
	if err := os.Chtimes(active, old, old); err != nil {
		t.Fatal(err)
	}
	// Reject input immediately, even before the polling watcher updates its state.
	if err := s.sendInput(command{Action: "enter"}); err == nil {
		t.Fatal("stale session accepted input")
	}
	s.refreshAttached(time.Now())
	if s.running || s.frame != "" || s.input != "" {
		t.Fatal("stale surface remained available")
	}
	future := time.Now().Add(time.Hour)
	os.Chtimes(active, future, future)
	if attachedAlive(dir, time.Now()) {
		t.Fatal("future heartbeat accepted")
	}
	os.Remove(active)
	if err := os.Symlink(input, active); err != nil {
		t.Fatal(err)
	}
	if attachedAlive(dir, time.Now()) {
		t.Fatal("symlink heartbeat accepted")
	}
}

func TestInputRefusesSymlink(t *testing.T) {
	dir := t.TempDir()
	target := filepath.Join(dir, "target")
	input := filepath.Join(dir, "input")
	os.WriteFile(target, []byte("untouched"), 0600)
	os.Symlink(target, input)
	s := &server{running: true, input: input}
	if err := s.sendInput(command{Action: "enter"}); err == nil {
		t.Fatal("symlink input accepted")
	}
	data, _ := os.ReadFile(target)
	if string(data) != "untouched" {
		t.Fatal("symlink target changed")
	}
}

func TestAttachmentShutdownDoesNotCloseRuntime(t *testing.T) {
	dir := t.TempDir()
	input := filepath.Join(dir, "input.txt")
	os.WriteFile(input, nil, 0600)
	os.WriteFile(filepath.Join(dir, "active"), nil, 0600)
	ctx, cancel := context.WithCancel(context.Background())
	s := &server{ctx: ctx}
	if err := s.attachSession(dir); err != nil {
		t.Fatal(err)
	}
	cancel()
	done := make(chan struct{})
	go func() { s.runs.Wait(); close(done) }()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("watcher did not stop")
	}
	data, _ := os.ReadFile(input)
	if len(data) != 0 {
		t.Fatal("viewer shutdown sent runtime input")
	}
	if _, err := os.Stat(filepath.Join(dir, "active")); err != nil {
		t.Fatal("viewer deleted external heartbeat")
	}
}
