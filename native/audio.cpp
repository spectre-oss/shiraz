// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/audio.hpp"
#include <sndfile.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <stdexcept>

namespace shiraz::audio {
namespace {
struct MemoryFile { const std::vector<std::uint8_t>& bytes; sf_count_t position = 0; };
sf_count_t length(void* opaque) { return static_cast<sf_count_t>(static_cast<MemoryFile*>(opaque)->bytes.size()); }
sf_count_t tell(void* opaque) { return static_cast<MemoryFile*>(opaque)->position; }
sf_count_t seek(sf_count_t offset, int whence, void* opaque) {
    auto& file = *static_cast<MemoryFile*>(opaque);
    sf_count_t base;
    switch (whence) { case SEEK_SET: base = 0; break; case SEEK_CUR: base = file.position; break; case SEEK_END: base = length(opaque); break; default: return -1; }
    if (offset < -base || offset > length(opaque) - base) return -1;
    file.position = base + offset; return file.position;
}
sf_count_t read(void* target, sf_count_t requested, void* opaque) {
    auto& file = *static_cast<MemoryFile*>(opaque);
    if (requested <= 0) return 0;
    auto count = std::min(requested, length(opaque) - file.position);
    if (count > 0) std::memcpy(target, file.bytes.data() + file.position, static_cast<std::size_t>(count));
    file.position += count; return count;
}
sf_count_t write(const void*, sf_count_t, void*) { return 0; }
void gains(float left, float right) {
    if (!std::isfinite(left) || !std::isfinite(right) || left < 0 || left > 1 || right < 0 || right > 1)
        throw std::invalid_argument("Audio gains must be finite and between zero and one");
}
void playbackRate(float rate) {
    if (!std::isfinite(rate) || rate < .5f || rate > 2) throw std::invalid_argument("Audio rate must be between 0.5 and 2");
}
void loopCount(int loops) { if (loops < -1) throw std::invalid_argument("Audio loop count must be -1 or nonnegative"); }
void validClip(const Clip& clip) {
    if (clip.rate < 8000 || clip.rate > 192000 || clip.channels < 1 || clip.channels > 2 || clip.samples.empty()
            || clip.samples.size() > maxDecodedSamples || clip.samples.size() % clip.channels)
        throw std::invalid_argument("Unsupported audio sample shape or size");
    for (float value : clip.samples) if (!std::isfinite(value) || std::abs(value) > 1) throw std::invalid_argument("Audio samples must be finite and normalized");
}
}
std::shared_ptr<const Clip> decode(const std::vector<std::uint8_t>& encoded) {
    if (encoded.empty() || encoded.size() > maxEncodedBytes) throw std::invalid_argument("Invalid encoded sound size");
    MemoryFile memory{encoded}; SF_INFO info{};
    SF_VIRTUAL_IO io{length, seek, read, write, tell};
    std::unique_ptr<SNDFILE, decltype(&sf_close)> file(sf_open_virtual(&io, SFM_READ, &info, &memory), sf_close);
    if (!file) throw std::invalid_argument("Unsupported or malformed encoded sound");
    if (info.channels < 1 || info.channels > 2 || info.samplerate < 8000 || info.samplerate > 192000 || info.frames <= 0
            || static_cast<std::uint64_t>(info.frames) > maxDecodedSamples / info.channels)
        throw std::invalid_argument("Decoded sound exceeds supported shape or size");
    auto result = std::make_shared<Clip>(); result->rate = info.samplerate; result->channels = info.channels;
    result->samples.resize(static_cast<std::size_t>(info.frames) * info.channels);
    sf_count_t done = 0;
    while (done < info.frames) {
        auto count = sf_readf_float(file.get(), result->samples.data() + done * info.channels, info.frames - done);
        if (count <= 0) throw std::invalid_argument("Truncated encoded sound");
        done += count;
    }
    float extra[2];
    if (sf_readf_float(file.get(), extra, 1) != 0 || sf_error(file.get()) != SF_ERR_NO_ERROR)
        throw std::invalid_argument("Encoded sound frame count mismatch");
    validClip(*result); return result;
}
Mixer::Mixer(int maxStreams, int outputRate) : maxStreams_(maxStreams), outputRate_(outputRate) {
    if (maxStreams < 1 || maxStreams > 64 || outputRate < 8000 || outputRate > 192000)
        throw std::invalid_argument("Unsupported mixer size or rate");
    voices_.reserve(static_cast<std::size_t>(maxStreams));
}
Mixer::Voice* Mixer::find(int id) {
    auto found = std::find_if(voices_.begin(), voices_.end(), [id](const Voice& voice) { return voice.id == id; });
    return found == voices_.end() ? nullptr : &*found;
}
int Mixer::play(std::shared_ptr<const Clip> clip, float left, float right, int priority, int loops, float rate) {
    gains(left, right); playbackRate(rate); loopCount(loops);
    if (!clip) throw std::invalid_argument("Null sound");
    validClip(*clip);
    if (nextId_ > std::numeric_limits<int>::max()) throw std::overflow_error("Audio stream IDs exhausted");
    auto replace = voices_.end();
    if (voices_.size() == static_cast<std::size_t>(maxStreams_)) {
        replace = std::min_element(voices_.begin(), voices_.end(), [](const Voice& a, const Voice& b) {
            return a.priority < b.priority || (a.priority == b.priority && a.id < b.id);
        });
        if (replace->priority > priority) return 0;
    }
    Voice voice{static_cast<int>(nextId_++), priority, loops, std::move(clip), 0, left, right, rate};
    int id = voice.id;
    if (replace == voices_.end()) voices_.push_back(std::move(voice)); else *replace = std::move(voice);
    return id;
}
void Mixer::stop(int id) { voices_.erase(std::remove_if(voices_.begin(), voices_.end(), [id](const Voice& v) { return v.id == id; }), voices_.end()); }
void Mixer::pause(int id) { if (auto* v = find(id)) { v->paused = true; v->autoPaused = false; } }
void Mixer::resume(int id) { if (auto* v = find(id)) { v->paused = false; v->autoPaused = false; } }
void Mixer::autoPause() { for (auto& v : voices_) if (!v.paused) { v.paused = true; v.autoPaused = true; } }
void Mixer::autoResume() { for (auto& v : voices_) if (v.autoPaused) { v.paused = false; v.autoPaused = false; } }
void Mixer::volume(int id, float left, float right) { gains(left, right); if (auto* v = find(id)) { v->left = left; v->right = right; } }
void Mixer::rate(int id, float rate) { playbackRate(rate); if (auto* v = find(id)) v->rate = rate; }
void Mixer::loop(int id, int loops) { loopCount(loops); if (auto* v = find(id)) v->loops = loops; }
void Mixer::priority(int id, int priority) { if (auto* v = find(id)) v->priority = priority; }
void Mixer::clear() { voices_.clear(); }
bool Mixer::runnable() const {
    return std::any_of(voices_.begin(), voices_.end(), [](const Voice& v) { return !v.paused; });
}
void Mixer::render(float* stereo, std::size_t frames) {
    if (!stereo || frames > 16384) throw std::invalid_argument("Invalid mixer output block");
    std::fill(stereo, stereo + frames * 2, 0);
    for (auto& voice : voices_) {
        if (voice.paused) continue;
        const auto& clip = *voice.clip; auto count = clip.frames();
        const double step = static_cast<double>(clip.rate) * voice.rate / outputRate_;
        for (std::size_t out = 0; out < frames && voice.position < count; ++out) {
            auto frame = static_cast<std::size_t>(voice.position);
            auto next = frame + 1 < count ? frame + 1 : voice.loops != 0 ? 0 : frame;
            float fraction = static_cast<float>(voice.position - frame);
            for (int channel = 0; channel < 2; ++channel) {
                auto source = clip.channels == 1 ? 0 : channel;
                float a = clip.samples[frame * clip.channels + source], b = clip.samples[next * clip.channels + source];
                stereo[out * 2 + channel] += (a + (b - a) * fraction) * (channel ? voice.right : voice.left);
            }
            voice.position += step;
            while (voice.position >= count && voice.loops != 0) {
                voice.position -= count; if (voice.loops > 0) --voice.loops;
            }
        }
    }
    voices_.erase(std::remove_if(voices_.begin(), voices_.end(), [](const Voice& v) { return v.position >= v.clip->frames(); }), voices_.end());
    for (std::size_t sample = 0; sample < frames * 2; ++sample) stereo[sample] = std::clamp(stereo[sample], -1.f, 1.f);
}
}
