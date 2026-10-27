// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/zip"
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"flag"
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

	"github.com/gorilla/websocket"
)

const maxAPK = 256 << 20

type event struct {
	Type    string `json:"type"`
	Message string `json:"message"`
}
type server struct {
	root    string
	mu      sync.Mutex
	running bool
	clients map[*websocket.Conn]chan event
}

func (s *server) broadcast(e event) {
	s.mu.Lock()
	defer s.mu.Unlock()
	for _, ch := range s.clients {
		select {
		case ch <- e:
		default:
		}
	}
}

func (s *server) ws(w http.ResponseWriter, r *http.Request) {
	// The page and socket must have the same browser origin.
	if r.Header.Get("Origin") != "http://"+r.Host && r.Header.Get("Origin") != "https://"+r.Host {
		http.Error(w, "invalid origin", http.StatusForbidden)
		return
	}
	upgrader := websocket.Upgrader{}
	c, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	ch := make(chan event, 128)
	s.mu.Lock()
	s.clients[c] = ch
	busy := s.running
	s.mu.Unlock()
	if busy {
		ch <- event{"status", "An APK is running"}
	} else {
		ch <- event{"status", "Ready"}
	}
	defer func() { s.mu.Lock(); delete(s.clients, c); s.mu.Unlock(); c.Close() }()
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()
	for {
		c.SetWriteDeadline(time.Now().Add(10 * time.Second))
		select {
		case e := <-ch:
			if c.WriteJSON(e) != nil {
				return
			}
		case <-ticker.C:
			if c.WriteMessage(websocket.PingMessage, nil) != nil {
				return
			}
		}
	}
}

func validAPK(path string) error {
	z, err := zip.OpenReader(path)
	if err != nil {
		return err
	}
	defer z.Close()
	hasManifest, hasDex := false, false
	for _, f := range z.File {
		if f.Name == "AndroidManifest.xml" {
			hasManifest = true
		}
		if f.Name == "classes.dex" {
			hasDex = true
		}
	}
	if !hasManifest || !hasDex {
		return errors.New("APK needs AndroidManifest.xml and classes.dex")
	}
	return nil
}

func (s *server) upload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "POST required", 405)
		return
	}
	s.mu.Lock()
	if s.running {
		s.mu.Unlock()
		http.Error(w, "an APK is already running", http.StatusConflict)
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
	r.Body = http.MaxBytesReader(w, r.Body, maxAPK+(1<<20))
	if err := r.ParseMultipartForm(1 << 20); err != nil {
		http.Error(w, "invalid or oversized upload", 400)
		return
	}
	file, header, err := r.FormFile("apk")
	if err != nil {
		http.Error(w, "missing APK", 400)
		return
	}
	defer file.Close()
	if !strings.EqualFold(filepath.Ext(header.Filename), ".apk") {
		http.Error(w, "select an .apk file", 400)
		return
	}
	dir, err := os.MkdirTemp(filepath.Join(s.root, "build"), "web-apk-")
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	path := filepath.Join(dir, "upload.apk")
	out, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if err != nil {
		os.RemoveAll(dir)
		http.Error(w, err.Error(), 500)
		return
	}
	n, copyErr := io.Copy(out, io.LimitReader(file, maxAPK+1))
	closeErr := out.Close()
	if copyErr != nil || closeErr != nil || n > maxAPK || n == 0 {
		os.RemoveAll(dir)
		http.Error(w, "invalid or oversized APK", 400)
		return
	}
	if err := validAPK(path); err != nil {
		os.RemoveAll(dir)
		http.Error(w, "invalid APK: "+err.Error(), 400)
		return
	}
	accepted = true
	go s.run(dir, path)
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusAccepted)
	json.NewEncoder(w).Encode(event{"status", "APK accepted"})
}

func (s *server) run(dir, path string) {
	defer func() { os.RemoveAll(dir); s.mu.Lock(); s.running = false; s.mu.Unlock() }()
	s.broadcast(event{"status", "Starting Shiraz"})
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Minute)
	defer cancel()
	cmd := exec.CommandContext(ctx, "python3", "-m", "shiraz", "run", path)
	cmd.Dir = s.root
	cmd.Env = append(os.Environ(), "PYTHONPATH="+filepath.Join(s.root, "src"), "PYTHONUNBUFFERED=1")
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		s.broadcast(event{"error", err.Error()})
		return
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		s.broadcast(event{"error", err.Error()})
		return
	}
	if err = cmd.Start(); err != nil {
		s.broadcast(event{"error", err.Error()})
		return
	}
	var wg sync.WaitGroup
	wg.Add(2)
	stream := func(rd io.Reader) {
		defer wg.Done()
		scan := bufio.NewScanner(rd)
		scan.Buffer(make([]byte, 4096), 1<<20)
		for scan.Scan() {
			s.broadcast(event{"log", scan.Text()})
		}
		if scan.Err() != nil {
			s.broadcast(event{"error", scan.Err().Error()})
		}
	}
	go stream(stdout)
	go stream(stderr)
	wg.Wait()
	err = cmd.Wait()
	if ctx.Err() != nil {
		s.broadcast(event{"error", "Run timed out after 30 minutes"})
	} else if err != nil {
		s.broadcast(event{"error", fmt.Sprintf("Shiraz exited: %v", err)})
	} else {
		s.broadcast(event{"status", "Shiraz exited successfully"})
	}
}

func main() {
	listen := flag.String("listen", "127.0.0.1:8080", "HTTP listen address")
	root := flag.String("root", "..", "Shiraz repository root")
	flag.Parse()
	abs, err := filepath.Abs(*root)
	if err != nil {
		log.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(abs, "src/shiraz/cli.py")); err != nil {
		log.Fatalf("invalid Shiraz root: %v", err)
	}
	if err := os.MkdirAll(filepath.Join(abs, "build"), 0700); err != nil {
		log.Fatal(err)
	}
	s := &server{root: abs, clients: make(map[*websocket.Conn]chan event)}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-store")
		io.WriteString(w, page)
	})
	mux.HandleFunc("GET /ws", s.ws)
	mux.HandleFunc("POST /upload", s.upload)
	log.Printf("Shiraz web server listening at http://%s", *listen)
	log.Fatal(http.ListenAndServe(*listen, mux))
}
