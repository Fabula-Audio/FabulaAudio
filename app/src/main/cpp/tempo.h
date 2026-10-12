#pragma once
#include <cstdint>
#include <functional>

// Interleaved stereo float32 PCM, 44100 Hz. Returns the written frame count.
using TempoProgress = std::function<void(int64_t completed, int64_t total)>;
int64_t changeTempo(const char *input, const char *output, int tempo,
                    const TempoProgress &progress = {});
