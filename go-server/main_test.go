// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
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
	pixels := make([]byte, videoWidth*videoHeight*3)
	pixels[0] = 255
	ppm := append([]byte("P6\n360 640\n255\n"), pixels...)
	if err := os.WriteFile(frame, ppm, 0600); err != nil {
		t.Fatal(err)
	}
	image, err := readPPMPixels(frame)
	if err != nil {
		t.Fatal(err)
	}
	if image[0] != 255 || image[1] != 0 || image[2] != 0 {
		t.Fatal("wrong frame pixel")
	}
}

func TestWebRTCInput(t *testing.T) {
	path := filepath.Join(t.TempDir(), "input.txt")
	if err := os.WriteFile(path, nil, 0600); err != nil {
		t.Fatal(err)
	}
	s := &server{running: true, appName: "messenger.apk", input: path, clients: make(map[*webrtc.DataChannel]struct{})}
	mux := http.NewServeMux()
	mux.HandleFunc("/webrtc", s.webrtcAnswer)
	httpServer := httptest.NewServer(mux)
	defer httpServer.Close()
	peer, err := webrtc.NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		t.Fatal(err)
	}
	defer peer.Close()
	channel, err := peer.CreateDataChannel("control", nil)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := peer.AddTransceiverFromKind(webrtc.RTPCodecTypeVideo, webrtc.RTPTransceiverInit{Direction: webrtc.RTPTransceiverDirectionRecvonly}); err != nil {
		t.Fatal(err)
	}
	ready := make(chan struct{})
	events := make(chan event, 2)
	channel.OnOpen(func() { close(ready) })
	channel.OnMessage(func(message webrtc.DataChannelMessage) {
		var item event
		if json.Unmarshal(message.Data, &item) == nil && (item.Type == "app" || item.Type == "status") {
			events <- item
		}
	})
	offer, err := peer.CreateOffer(nil)
	if err != nil {
		t.Fatal(err)
	}
	gather := webrtc.GatheringCompletePromise(peer)
	if err := peer.SetLocalDescription(offer); err != nil {
		t.Fatal(err)
	}
	select {
	case <-gather:
	case <-time.After(10 * time.Second):
		t.Fatal("client ICE gathering timed out")
	}
	body, err := json.Marshal(peer.LocalDescription())
	if err != nil {
		t.Fatal(err)
	}
	req, err := http.NewRequest(http.MethodPost, httpServer.URL+"/webrtc", bytes.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	req.Header.Set("Origin", httpServer.URL)
	response, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		t.Fatalf("signaling status: %d", response.StatusCode)
	}
	var answer webrtc.SessionDescription
	if err := json.NewDecoder(response.Body).Decode(&answer); err != nil {
		t.Fatal(err)
	}
	if err := peer.SetRemoteDescription(answer); err != nil {
		t.Fatal(err)
	}
	select {
	case <-ready:
	case <-time.After(10 * time.Second):
		t.Fatal("data channel did not open")
	}
	for _, expected := range []event{{"app", "messenger.apk"}, {"status", "Running"}} {
		select {
		case got := <-events:
			if got != expected {
				t.Fatalf("session event: got %+v, want %+v", got, expected)
			}
		case <-time.After(2 * time.Second):
			t.Fatal("session state was not sent on reconnect")
		}
	}
	data, err := json.Marshal(command{Action: "fingerdown", X: .5, Y: .5})
	if err != nil {
		t.Fatal(err)
	}
	if err := channel.Send(data); err != nil {
		t.Fatal(err)
	}
	deadline := time.Now().Add(2 * time.Second)
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
	t.Fatal("WebRTC input did not reach live-input file")
}
