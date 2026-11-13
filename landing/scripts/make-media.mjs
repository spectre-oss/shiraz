// SPDX-License-Identifier: GPL-3.0-only
// Maintainer tool: encode a finished film for the website and pin its bytes.
//   node landing/scripts/make-media.mjs <source.mp4> [poster-second]
// Writes the web encode and its poster to landing/static/media/ (ignored, never
// committed) and their sizes and SHA-256 to src/lib/media.json (committed).
// Needs ffmpeg and ffprobe. Upload both files to the release named by "tag" in
// media.json; the site build downloads them from there and checks the digests.
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const [source, posterAt = '12.4'] = process.argv.slice(2);
if (!source || !/^\d+(\.\d+)?$/.test(posterAt)) {
  console.error('usage: make-media.mjs <source.mp4> [poster-second]');
  process.exit(2);
}
const landing = fileURLToPath(new URL('../', import.meta.url));
const manifestPath = `${landing}src/lib/media.json`;
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
const media = `${landing}static/media/`;
mkdirSync(media, { recursive: true });
const video = media + manifest.video.file;
const poster = media + manifest.poster.file;
const frame = `${media}.poster-frame.png`;
const ffmpeg = args => execFileSync('ffmpeg', ['-v', 'error', '-y', ...args], { stdio: 'inherit' });

// H.264 High with AAC plays everywhere the site does. The index goes first so
// playback starts before the download ends; source metadata is dropped.
ffmpeg(['-i', source, '-map', '0:v:0', '-map', '0:a:0', '-c:v', 'libx264', '-preset', 'slow', '-crf', '26',
  '-profile:v', 'high', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '128k',
  '-movflags', '+faststart', '-map_metadata', '-1', video]);
ffmpeg(['-ss', posterAt, '-i', video, '-frames:v', '1', frame]);
await sharp(frame).resize({ width: 1600 }).jpeg({ quality: 78, mozjpeg: true }).toFile(poster);
rmSync(frame);

const probe = JSON.parse(execFileSync('ffprobe', ['-v', 'error', '-select_streams', 'v:0',
  '-show_entries', 'stream=width,height:format=duration', '-of', 'json', video], { encoding: 'utf8' }));
const pin = path => ({ bytes: statSync(path).size, sha256: createHash('sha256').update(readFileSync(path)).digest('hex') });
manifest.video = { file: manifest.video.file, ...pin(video), width: probe.streams[0].width,
  height: probe.streams[0].height, seconds: Math.round(Number(probe.format.duration)) };
manifest.poster = { file: manifest.poster.file, ...pin(poster) };
writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
console.log(`MEDIA_ENCODED ${manifest.video.file} ${manifest.video.bytes} bytes, ${manifest.poster.file} ${manifest.poster.bytes} bytes`);
console.log(`Check static/media/${manifest.captions} against the new cut, then upload both files to release ${manifest.tag}.`);
