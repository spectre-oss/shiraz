// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"errors"
	"os"
	"path/filepath"
	"time"
)

// The external supervisor renews active every five seconds and removes it on exit.
// Expiration stops input/video even if the supervisor dies without cleanup.
func attachedAlive(dir string, now time.Time) bool {
	info, err := os.Lstat(filepath.Join(dir, "active"))
	if err != nil || !info.Mode().IsRegular() {
		return false
	}
	age := now.Sub(info.ModTime())
	return age >= -time.Second && age < 15*time.Second
}

func (s *server) refreshAttached(now time.Time) {
	active := attachedAlive(s.attach, now)
	s.mu.Lock()
	changed := s.running != active
	s.running = active
	if active {
		s.input = filepath.Join(s.attach, "input.txt")
		s.frame = filepath.Join(s.attach, "frame.ppm")
		s.appName = "Attached session"
		if changed {
			s.commands = 0
		}
	} else {
		s.input, s.frame, s.appName = "", "", ""
	}
	s.mu.Unlock()
	if changed {
		if active {
			s.broadcast(event{"app", "Attached session"})
			s.broadcast(event{"status", "Running attached session"})
		} else {
			s.broadcast(event{"status", "Ready"})
		}
	}
}

func (s *server) attachSession(dir string) error {
	abs, err := filepath.Abs(dir)
	if err != nil {
		return err
	}
	info, err := os.Lstat(abs)
	if err != nil {
		return err
	}
	if !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
		return errors.New("attachment must be a real directory")
	}
	s.attach = abs
	s.refreshAttached(time.Now())
	s.runs.Add(1)
	go func() {
		defer s.runs.Done()
		ticker := time.NewTicker(time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-s.ctx.Done():
				return
			case <-ticker.C:
				s.refreshAttached(time.Now())
			}
		}
	}()
	return nil
}
