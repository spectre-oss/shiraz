// SPDX-License-Identifier: GPL-3.0-only
// observe connects to an existing Shiraz web runner; it never executes an APK.
// JSON commands on stdin use the portal's normal WebRTC input channel.
package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media/ivfwriter"
)

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run() error {
	endpoint := flag.String("url", "http://127.0.0.1:8080", "Shiraz portal URL (run beside server or use reachable ICE candidates)")
	video := flag.String("video", "capture.ivf", "VP8 capture output; decode with ffmpeg for screenshots")
	duration := flag.Duration("duration", 30*time.Second, "observation duration")
	flag.Parse()
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()
	ctx, cancel := context.WithTimeout(ctx, *duration)
	defer cancel()
	peer, err := webrtc.NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		return err
	}
	defer peer.Close()
	writer, err := ivfwriter.New(*video)
	if err != nil {
		return err
	}
	// Close the peer before closing its packet writer.
	defer writer.Close()
	done := make(chan struct{})
	peer.OnTrack(func(track *webrtc.TrackRemote, _ *webrtc.RTPReceiver) {
		defer close(done)
		fmt.Fprintln(os.Stderr, "video:", track.Codec().MimeType)
		for {
			packet, _, err := track.ReadRTP()
			if err != nil {
				return
			}
			if err := writer.WriteRTP(packet); err != nil {
				fmt.Fprintln(os.Stderr, err)
				cancel()
				return
			}
		}
	})
	channel, err := peer.CreateDataChannel("control", nil)
	if err != nil {
		return err
	}
	ready := make(chan struct{})
	channel.OnOpen(func() { close(ready) })
	channel.OnMessage(func(msg webrtc.DataChannelMessage) { fmt.Println(string(msg.Data)) })
	if _, err := peer.AddTransceiverFromKind(webrtc.RTPCodecTypeVideo, webrtc.RTPTransceiverInit{Direction: webrtc.RTPTransceiverDirectionRecvonly}); err != nil {
		return err
	}
	offer, err := peer.CreateOffer(nil)
	if err != nil {
		return err
	}
	gathered := webrtc.GatheringCompletePromise(peer)
	if err := peer.SetLocalDescription(offer); err != nil {
		return err
	}
	select {
	case <-gathered:
	case <-ctx.Done():
		return ctx.Err()
	}
	body, err := json.Marshal(peer.LocalDescription())
	if err != nil {
		return err
	}
	url := strings.TrimRight(*endpoint, "/")
	req, err := http.NewRequestWithContext(ctx, "POST", url+"/webrtc", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Origin", url)
	response, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		message, _ := io.ReadAll(io.LimitReader(response.Body, 4096))
		return fmt.Errorf("signaling: %s: %s", response.Status, message)
	}
	var answer webrtc.SessionDescription
	if err := json.NewDecoder(io.LimitReader(response.Body, 1<<20)).Decode(&answer); err != nil {
		return err
	}
	if err := peer.SetRemoteDescription(answer); err != nil {
		return err
	}
	select {
	case <-ready:
	case <-ctx.Done():
		return ctx.Err()
	}
	fmt.Fprintln(os.Stderr, "control ready; enter one JSON command per line")
	go func() {
		scanner := bufio.NewScanner(os.Stdin)
		scanner.Buffer(make([]byte, 1024), 1024)
		for scanner.Scan() {
			line := append([]byte(nil), scanner.Bytes()...)
			if !json.Valid(line) {
				fmt.Fprintln(os.Stderr, "invalid JSON command")
				continue
			}
			if err := channel.SendText(string(line)); err != nil {
				fmt.Fprintln(os.Stderr, err)
				return
			}
		}
		if err := scanner.Err(); err != nil {
			fmt.Fprintln(os.Stderr, err)
		}
	}()
	<-ctx.Done()
	peer.Close()
	select {
	case <-done:
	case <-time.After(time.Second):
	}
	return nil
}
