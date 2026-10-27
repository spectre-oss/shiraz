// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/zip"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
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

func TestInputAndFrame(t *testing.T) {
	dir := t.TempDir()
	input := filepath.Join(dir, "input.txt")
	if err := os.WriteFile(input, nil, 0600); err != nil {
		t.Fatal(err)
	}
	s := &server{running: true, input: input}
	if err := s.sendInput(command{Action: "fingerdown", X: .25, Y: .75}); err != nil {
		t.Fatal(err)
	}
	if err := s.sendInput(command{Action: "fingerup", X: .25, Y: .75}); err != nil {
		t.Fatal(err)
	}
	if err := s.sendInput(command{Action: "fingerdown", X: 2, Y: .75}); err == nil {
		t.Fatal("out-of-range touch accepted")
	}
	data, err := os.ReadFile(input)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(data), "fingerdown 0 0.250000 0.750000 1\n") {
		t.Fatalf("unexpected input: %q", data)
	}
	frame := filepath.Join(dir, "frame.ppm")
	if err := os.WriteFile(frame, []byte("P6\n1 1\n255\n\xff\x00\x00"), 0600); err != nil {
		t.Fatal(err)
	}
	image, err := readPPM(frame)
	if err != nil {
		t.Fatal(err)
	}
	r, g, b, _ := image.At(0, 0).RGBA()
	if r != 65535 || g != 0 || b != 0 {
		t.Fatal("wrong frame pixel")
	}
}

func TestWebSocketInput(t *testing.T) {
	path := filepath.Join(t.TempDir(), "input.txt")
	if err := os.WriteFile(path, nil, 0600); err != nil {
		t.Fatal(err)
	}
	s := &server{running: true, input: path, clients: make(map[*websocket.Conn]chan event)}
	mux := http.NewServeMux()
	mux.HandleFunc("/ws", s.ws)
	httpServer := httptest.NewServer(mux)
	defer httpServer.Close()
	url := "ws" + strings.TrimPrefix(httpServer.URL, "http") + "/ws"
	c, _, err := websocket.DefaultDialer.Dial(url, http.Header{"Origin": []string{httpServer.URL}})
	if err != nil {
		t.Fatal(err)
	}
	defer c.Close()
	if err := c.WriteJSON(command{Action: "fingerdown", X: .5, Y: .5}); err != nil {
		t.Fatal(err)
	}
	deadline := time.Now().Add(time.Second)
	for time.Now().Before(deadline) {
		data, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		if strings.Contains(string(data), "fingerdown 0 0.500000 0.500000 1") {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatal("WebSocket input did not reach live-input file")
}
