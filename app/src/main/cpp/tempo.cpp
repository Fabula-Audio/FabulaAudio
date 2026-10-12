#include "tempo.h"
#include "sbsms.h"
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <memory>
#include <stdexcept>
#include <sys/stat.h>

using namespace _sbsms_;
namespace {
using File = std::unique_ptr<FILE, decltype(&fclose)>;
class Input final : public SBSMSInterfaceSliding {
    FILE *file;
public:
    Input(FILE *file, Slide *rate, Slide *pitch, SampleCountType frames, SBSMSQuality *quality)
        : SBSMSInterfaceSliding(rate, pitch, false, frames, 0, quality), file(file) {}
    long samples(audio *buffer, long count) override {
        auto got = fread(buffer, sizeof(audio), count, file);
        if (ferror(file)) throw std::runtime_error("PCM input read failed");
        return static_cast<long>(got);
    }
};
}

int64_t changeTempo(const char *input, const char *output, int tempo, const TempoProgress &progress) {
    if (tempo != 24 && tempo != 3) throw std::runtime_error("Unsupported SBSMS tempo");
    struct stat info {};
    if (stat(input, &info) || info.st_size <= 0 || info.st_size % sizeof(audio))
        throw std::runtime_error("Invalid stereo PCM input");
    File in(fopen(input, "rb"), fclose);
    File out(fopen(output, "wb"), fclose);
    if (!in || !out) throw std::runtime_error("Cannot open PCM files");
    const auto frames = static_cast<SampleCountType>(info.st_size / sizeof(audio));
    SBSMSQuality quality(&SBSMSQualityStandard);
    Slide rate(SlideConstant, float(tempo), float(tempo));
    Slide pitch(SlideConstant, 1.0f, 1.0f);
    Input source(in.get(), &rate, &pitch, frames, &quality);
    SBSMS engine(2, &quality, true);
    const auto target = source.getSamplesToOutput();
    if (target <= 0) throw std::runtime_error("Audio too short for this tempo");
    audio buffer[512];
    SampleCountType written = 0;
    if (progress) progress(0, target);
    while (written < target) {
        long count = engine.read(&source, buffer, static_cast<long>(std::min<SampleCountType>(512, target - written)));
        if (count <= 0 || engine.getError() != SBSMSErrorNone)
            throw std::runtime_error("SBSMS could not complete audio processing");
        for (long i = 0; i < count; ++i)
            if (!std::isfinite(buffer[i][0]) || !std::isfinite(buffer[i][1]))
                throw std::runtime_error("SBSMS produced invalid samples");
        if (fwrite(buffer, sizeof(audio), count, out.get()) != static_cast<size_t>(count))
            throw std::runtime_error("PCM output write failed");
        written += count;
        // Check cancellation after each block, not only at integer percentages.
        if (progress) progress(written, target);
    }
    if (fflush(out.get())) throw std::runtime_error("Cannot flush PCM output");
    return written;
}
