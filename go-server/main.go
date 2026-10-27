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
	"image"
	"image/color"
	"image/png"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
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
	root     string
	mu       sync.Mutex
	running  bool
	frame    string
	input    string
	commands int
	clients  map[*websocket.Conn]chan event
}

type command struct {
	Action string  `json:"action"`
	X      float64 `json:"x"`
	Y      float64 `json:"y"`
	Text   string  `json:"text"`
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
	go func() {
		defer c.Close()
		c.SetReadLimit(1024)
		for {
			var cmd command
			if c.ReadJSON(&cmd) != nil {
				return
			}
			if err := s.sendInput(cmd); err != nil {
				log.Printf("input rejected: %v", err)
			}
		}
	}()
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

func (s *server) sendInput(cmd command) error {
	var line string
	switch cmd.Action {
	case "fingerdown", "fingermove", "fingerup":
		if cmd.X < 0 || cmd.X > 1 || cmd.Y < 0 || cmd.Y > 1 {
			return errors.New("invalid touch coordinates")
		}
		line = fmt.Sprintf("%s 0 %.6f %.6f 1\n", cmd.Action, cmd.X, cmd.Y)
	case "back":
		line = "keydown \"Escape\"\nkeyup \"Escape\"\n"
	case "text":
		if len(cmd.Text) == 0 || len(cmd.Text) > 30 || strings.ContainsAny(cmd.Text, "\n\r\x00") {
			return errors.New("text must be 1–30 bytes without control characters")
		}
		line = "text " + strconv.Quote(cmd.Text) + "\n"
	case "enter":
		line = "keydown \"Return\"\nkeyup \"Return\"\n"
	case "portrait":
		line = "resize 360 640\n"
	case "landscape":
		line = "resize 640 360\n"
	case "stop":
		line = "close\n"
	default:
		return errors.New("unknown input action")
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.running || s.input == "" {
		return errors.New("no running app")
	}
	if s.commands >= 900 {
		return errors.New("input command limit reached")
	}
	file, err := os.OpenFile(s.input, os.O_APPEND|os.O_WRONLY, 0600)
	if err != nil {
		return err
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil {
		return err
	}
	if info.Size()+int64(len(line)) > 60000 {
		return errors.New("input file limit reached")
	}
	if _, err := io.WriteString(file, line); err != nil {
		return err
	}
	s.commands += strings.Count(line, "\n")
	return nil
}

func readPPM(path string) (image.Image, error) {
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
	if magic != "P6" || width < 1 || height < 1 || width > 8192 || height > 8192 || width*height > 16*1024*1024 || max != 255 {
		return nil, errors.New("invalid frame")
	}
	separator, err := reader.ReadByte()
	if err != nil || separator != '\n' {
		return nil, errors.New("invalid frame header")
	}
	pixels := make([]byte, width*height*3)
	if _, err := io.ReadFull(reader, pixels); err != nil {
		return nil, err
	}
	result := image.NewRGBA(image.Rect(0, 0, width, height))
	for i := 0; i < width*height; i++ {
		result.Set(i%width, i/width, color.RGBA{pixels[i*3], pixels[i*3+1], pixels[i*3+2], 255})
	}
	return result, nil
}

func (s *server) snapshot(w http.ResponseWriter, r *http.Request) {
	s.mu.Lock()
	path := s.frame
	s.mu.Unlock()
	if path == "" {
		http.Error(w, "no frame yet", http.StatusServiceUnavailable)
		return
	}
	frame, err := readPPM(path)
	if err != nil {
		http.Error(w, "no frame yet", http.StatusServiceUnavailable)
		return
	}
	w.Header().Set("Content-Type", "image/png")
	w.Header().Set("Cache-Control", "no-store")
	if err := png.Encode(w, frame); err != nil {
		log.Printf("frame encoding: %v", err)
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
	multipart, err := r.MultipartReader()
	if err != nil {
		http.Error(w, "invalid multipart upload", 400)
		return
	}
	file, err := multipart.NextPart()
	if err != nil || file.FormName() != "apk" {
		http.Error(w, "missing APK", 400)
		return
	}
	defer file.Close()
	if !strings.EqualFold(filepath.Ext(file.FileName()), ".apk") {
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
	if extra, err := multipart.NextPart(); err != io.EOF || extra != nil {
		os.RemoveAll(dir)
		http.Error(w, "upload must contain one APK", 400)
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
	defer func() {
		s.mu.Lock()
		s.running = false
		s.input = ""
		s.frame = ""
		s.mu.Unlock()
		os.RemoveAll(dir)
	}()
	s.broadcast(event{"status", "Starting Shiraz"})
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Minute)
	defer cancel()
	input := filepath.Join(dir, "input.txt")
	if err := os.WriteFile(input, nil, 0600); err != nil {
		s.broadcast(event{"error", err.Error()})
		return
	}
	frame := filepath.Join(dir, "frame.ppm")
	s.mu.Lock()
	s.input = input
	s.frame = frame
	s.commands = 0
	s.mu.Unlock()
	cmd := exec.CommandContext(ctx, "python3", "-m", "shiraz", "run", path)
	cmd.Dir = s.root
	cmd.Env = append(os.Environ(), "PYTHONPATH="+filepath.Join(s.root, "src"), "PYTHONUNBUFFERED=1", "SDL_VIDEODRIVER=dummy", "SDL_RENDER_DRIVER=software", "SHIRAZ_PRESENT_SNAPSHOT="+frame, "SHIRAZ_LIVE_INPUT="+input)
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
	mux.HandleFunc("GET /frame.png", s.snapshot)
	mux.HandleFunc("POST /upload", s.upload)
	log.Printf("Shiraz web server listening at http://%s", *listen)
	log.Fatal(http.ListenAndServe(*listen, mux))
}
