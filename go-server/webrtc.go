// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
	"github.com/pion/webrtc/v4/pkg/media/ivfreader"
)

const videoWidth, videoHeight, videoRate = 360, 640, 10

func readPPMPixels(path string) ([]byte, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	reader := bufio.NewReader(file)
	var magic string
	var width, height, max int
	if _, err := fmt.Fscan(reader, &magic, &width, &height, &max); err != nil {
		return nil, err
	}
	if magic != "P6" || width != videoWidth || height != videoHeight || max != 255 {
		return nil, errors.New("unsupported frame format")
	}
	separator, err := reader.ReadByte()
	if err != nil || separator != '\n' {
		return nil, errors.New("invalid frame header")
	}
	pixels := make([]byte, width*height*3)
	if _, err := io.ReadFull(reader, pixels); err != nil {
		return nil, err
	}
	return pixels, nil
}

func ffmpegCommand(root string, ctx context.Context) (*exec.Cmd, error) {
	path := os.Getenv("SHIRAZ_FFMPEG")
	if path == "" {
		bundled := filepath.Join(root, ".local/system-codecs/usr/bin/ffmpeg")
		if _, err := os.Stat(bundled); err == nil {
			path = bundled
		} else {
			path = "ffmpeg"
		}
	}
	if _, err := exec.LookPath(path); err != nil {
		return nil, fmt.Errorf("FFmpeg is required for WebRTC video: %w", err)
	}
	cmd := exec.CommandContext(ctx, path,
		"-hide_banner", "-loglevel", "error", "-f", "rawvideo", "-pixel_format", "rgb24", "-video_size", "360x640", "-framerate", "10", "-i", "pipe:0",
		"-an", "-c:v", "libvpx", "-deadline", "realtime", "-cpu-used", "8", "-b:v", "900k", "-g", "30", "-f", "ivf", "pipe:1")
	lib := filepath.Join(root, ".local/system-codecs/usr/lib")
	if strings.HasPrefix(path, filepath.Join(root, ".local/system-codecs/usr/bin")+string(filepath.Separator)) {
		cmd.Env = append(os.Environ(), "LD_LIBRARY_PATH="+lib+":"+os.Getenv("LD_LIBRARY_PATH"))
	}
	return cmd, nil
}

func (s *server) streamVideo(ctx context.Context, track *webrtc.TrackLocalStaticSample) {
	cmd, err := ffmpegCommand(s.root, ctx)
	if err != nil {
		s.broadcast(event{"error", err.Error()})
		return
	}
	stdin, err := cmd.StdinPipe()
	if err != nil {
		s.broadcast(event{"error", err.Error()})
		return
	}
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		s.broadcast(event{"error", err.Error()})
		return
	}
	cmd.Stderr = io.Discard
	if err := cmd.Start(); err != nil {
		s.broadcast(event{"error", err.Error()})
		return
	}
	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		defer stdin.Close()
		black := make([]byte, videoWidth*videoHeight*3)
		ticker := time.NewTicker(time.Second / videoRate)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
			}
			s.mu.Lock()
			path := s.frame
			s.mu.Unlock()
			pixels := black
			if path != "" {
				if capture, err := readPPMPixels(path); err == nil {
					pixels = capture
				}
			}
			if _, err := stdin.Write(pixels); err != nil {
				return
			}
		}
	}()
	reader, header, err := ivfreader.NewWith(stdout)
	if err == nil && header.FourCC != "VP80" {
		err = errors.New("FFmpeg did not produce VP8")
	}
	if err == nil {
		for ctx.Err() == nil {
			frame, _, readErr := reader.ParseNextFrame()
			if readErr != nil {
				err = readErr
				break
			}
			if writeErr := track.WriteSample(media.Sample{Data: frame, Duration: time.Second / videoRate}); writeErr != nil {
				err = writeErr
				break
			}
		}
	}
	cmd.Process.Kill()
	wg.Wait()
	cmd.Wait()
	if err != nil && ctx.Err() == nil && !errors.Is(err, io.EOF) {
		log.Printf("WebRTC video stream: %v", err)
	}
}

func (s *server) webrtcAnswer(w http.ResponseWriter, r *http.Request) {
	origin := r.Header.Get("Origin")
	if origin != "http://"+r.Host && origin != "https://"+r.Host {
		http.Error(w, "invalid origin", http.StatusForbidden)
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	var offer webrtc.SessionDescription
	if err := json.NewDecoder(r.Body).Decode(&offer); err != nil || offer.Type != webrtc.SDPTypeOffer {
		http.Error(w, "invalid WebRTC offer", 400)
		return
	}
	peer, err := webrtc.NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	closePeer := true
	defer func() {
		if closePeer {
			peer.Close()
		}
	}()
	track, err := webrtc.NewTrackLocalStaticSample(webrtc.RTPCodecCapability{MimeType: webrtc.MimeTypeVP8}, "phone", "shiraz")
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	sender, err := peer.AddTrack(track)
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	go func() {
		buffer := make([]byte, 1500)
		for {
			if _, _, err := sender.Read(buffer); err != nil {
				return
			}
		}
	}()
	ctx, cancel := context.WithCancel(context.Background())
	var once sync.Once
	peer.OnConnectionStateChange(func(state webrtc.PeerConnectionState) {
		switch state {
		case webrtc.PeerConnectionStateConnected:
			once.Do(func() { go s.streamVideo(ctx, track) })
		case webrtc.PeerConnectionStateFailed, webrtc.PeerConnectionStateClosed:
			cancel()
			peer.Close()
		}
	})
	peer.OnDataChannel(func(ch *webrtc.DataChannel) {
		if ch.Label() != "control" {
			ch.Close()
			return
		}
		ch.OnOpen(func() {
			s.mu.Lock()
			s.clients[ch] = struct{}{}
			running := s.running
			s.mu.Unlock()
			message := "Ready"
			if running {
				message = "An APK is running"
			}
			data, _ := json.Marshal(event{"status", message})
			ch.SendText(string(data))
		})
		ch.OnClose(func() { s.mu.Lock(); delete(s.clients, ch); s.mu.Unlock() })
		ch.OnMessage(func(msg webrtc.DataChannelMessage) {
			if len(msg.Data) > 1024 {
				return
			}
			var command command
			if err := json.Unmarshal(msg.Data, &command); err != nil {
				return
			}
			if err := s.sendInput(command); err != nil && !strings.Contains(err.Error(), "no running app") {
				log.Printf("input rejected: %v", err)
			}
		})
	})
	if err := peer.SetRemoteDescription(offer); err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	answer, err := peer.CreateAnswer(nil)
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	gather := webrtc.GatheringCompletePromise(peer)
	if err := peer.SetLocalDescription(answer); err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	select {
	case <-gather:
	case <-time.After(15 * time.Second):
		http.Error(w, "ICE gathering timed out", 504)
		return
	case <-r.Context().Done():
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	if err := json.NewEncoder(w).Encode(peer.LocalDescription()); err != nil {
		return
	}
	closePeer = false
	time.AfterFunc(45*time.Second, func() {
		if peer.ConnectionState() != webrtc.PeerConnectionStateConnected {
			peer.Close()
		}
	})
}
