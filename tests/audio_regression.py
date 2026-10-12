"""Host regression: python3 tests/audio_regression.py (C++ compiler, FFmpeg, NumPy)."""
import os
import re
import subprocess
import tempfile
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
CPP = ROOT / "app/src/main/cpp"
SR = 44100


def run(*args):
    return subprocess.run([str(a) for a in args], check=True, capture_output=True, timeout=120)


def read(path):
    return np.fromfile(path, dtype="<f4").reshape(-1, 2)


def ffmpeg(*args):
    run("ffmpeg", "-v", "error", "-y", *args)


with tempfile.TemporaryDirectory(prefix="fabula-audio-test-") as directory:
    tmp = Path(directory)
    exe = tmp / "tempo"
    run(os.environ.get("CXX", "c++"), "-std=c++17", "-O2", "-Wno-enum-compare",
        "-I", CPP, "-I", CPP / "third_party/libsbsms/include",
        ROOT / "tests/tempo_cli.cpp", CPP / "tempo.cpp",
        *sorted((CPP / "third_party/libsbsms/src").glob("*.cpp")), "-o", exe)

    # Different tones and amplitudes in L/R detect mono conversion, pitch changes,
    # channel swaps and unintended gain. Non-divisible length tests rounding.
    frames = SR * 12 + 17
    t = np.arange(frames) / SR
    source = np.column_stack((.2 * np.sin(2 * np.pi * 440 * t),
                              .1 * np.sin(2 * np.pi * 880 * t))).astype("<f4")
    source.tofile(tmp / "input.f32")
    for tempo in (24, 3):
        run(exe, tmp / "input.f32", tmp / "fast.f32", tempo)
        result = read(tmp / "fast.f32")
        assert abs(len(result) - frames / tempo) <= 1
        assert np.isfinite(result).all() and np.max(np.abs(result)) < 1
        for ch, frequency, amplitude in ((0, 440, .2), (1, 880, .1)):
            middle = result[len(result) // 4:3 * len(result) // 4, ch]
            bins = np.fft.rfftfreq(len(middle), 1 / SR)
            peak = bins[np.argmax(np.abs(np.fft.rfft(middle * np.hanning(len(middle)))))]
            assert abs(peak - frequency) < 5, (tempo, ch, peak)
            assert abs(20 * np.log10(np.sqrt(np.mean(middle ** 2)) / (amplitude / np.sqrt(2)))) < .1
        ffmpeg("-f", "f32le", "-ar", SR, "-ac", 2, "-i", tmp / "fast.f32",
               "-c:a", "flac", tmp / "fast.flac")
        (tmp / "repeat.txt").write_text((f"file '{tmp / 'fast.flac'}'\n") * tempo)
        ffmpeg("-f", "concat", "-safe", 0, "-i", tmp / "repeat.txt",
               "-af", f"apad=whole_len={frames},atrim=end_sample={frames}",
               "-f", "f32le", tmp / "repeated.f32")
        assert len(read(tmp / "repeated.f32")) == frames
        print(f"SBSMS ×{tempo}: pitch, stereo, gain and repeated frame count PASS")

    # Cancellation must unwind a running engine promptly and permit a fresh run.
    cancelled = subprocess.run([str(exe), str(tmp / "input.f32"), str(tmp / "cancel.f32"),
                                "3", "2048"], capture_output=True, timeout=10)
    assert cancelled.returncode == 1 and b"Cancelled by test" in cancelled.stderr
    assert 0 < (tmp / "cancel.f32").stat().st_size < (tmp / "input.f32").stat().st_size / 3
    run(exe, tmp / "input.f32", tmp / "restart.f32", 3)
    assert len(read(tmp / "restart.f32")) == len(read(tmp / "input.f32")) // 3
    print("SBSMS cancellation during processing and subsequent restart PASS")

    # Temporal events must move, not just pitch/stationary tones: raw 432 is
    # three times faster than raw 144 before repeating to restore the duration.
    t = np.arange(SR * 12) / SR
    envelope = sum(np.exp(-((t - center) / .12) ** 2) for center in (2, 5, 9))
    markers = np.column_stack([.2 * envelope * np.sin(2 * np.pi * hz * t)
                               for hz in (440, 880)]).astype("<f4")
    markers.tofile(tmp / "markers.f32")
    run(exe, tmp / "markers.f32", tmp / "markers-fast.f32", 3)
    result = read(tmp / "markers-fast.f32")
    assert len(result) == SR * 4
    for ch in (0, 1):
        for center in (2, 5, 9):
            expected = center / 3
            start, end = int((expected - .1) * SR), int((expected + .1) * SR)
            energy = np.convolve(result[start:end, ch] ** 2, np.ones(441) / 441, "same")
            peak = (start + np.argmax(energy)) / SR
            assert abs(peak - expected) < .03, (ch, expected, peak)
    print("Raw ×432: temporal markers accelerated ×3 relative to raw ×144 PASS")

    # Silence must stay silent, including a short fragment at ×24.
    for frames in (SR * 2, 1001):
        np.zeros((frames, 2), dtype="<f4").tofile(tmp / "silence.f32")
        for tempo in (24, 3):
            run(exe, tmp / "silence.f32", tmp / "silent-fast.f32", tempo)
            result = read(tmp / "silent-fast.f32")
            assert abs(len(result) - frames / tempo) <= 1
            assert np.max(np.abs(result)) < 1e-7
    print("SBSMS silence / short input PASS")

    # Use the exact application graphs; noisy accelerated layers must be quieter,
    # while the underlying x6 voice stays at unity (no amix normalization).
    text = (ROOT / "app/src/main/java/com/fabula/audio/SpeedMix.kt").read_text()
    frames = SR * 4 + 19
    t = np.arange(frames) / SR
    base = np.column_stack((.2 * np.sin(2 * np.pi * 440 * t),
                            .1 * np.sin(2 * np.pi * 880 * t))).astype("<f4")
    rng = np.random.default_rng(17)
    layers = [rng.normal(0, .03, (frames, 2)).astype("<f4") for _ in range(2)]
    base.tofile(tmp / "base.f32")
    for i, layer in enumerate(layers):
        layer.tofile(tmp / f"layer{i}.f32")
    for mode, count in ((144, 1), (432, 2)):
        graph = re.search(r'FILTER' + str(mode) + r' = "(.*?)"', text)[1]
        args = []
        for file in [tmp / "base.f32"] + [tmp / f"layer{i}.f32" for i in range(count)]:
            args += ["-f", "f32le", "-ar", SR, "-ac", 2, "-i", file]
        ffmpeg(*args, "-filter_complex", graph, "-map", "[out]", "-f", "f32le", tmp / "mix.f32")
        actual = read(tmp / "mix.f32")
        expected = base + (10 ** (-36 / 20)) * sum(layers[:count])
        assert len(actual) == frames
        assert np.max(np.abs(actual - expected)) < 1e-6
        assert np.max(np.abs(actual)) < 1
        print(f"Mix ×{mode}: main voice at unity, layers -36 dB, stereo / duration PASS")

    # WAV merge must retain channel separation and exact combined sample count.
    import wave
    for name, n in (("a", 10001), ("b", 17113)):
        with wave.open(str(tmp / f"{name}.wav"), "wb") as wav:
            wav.setparams((2, 2, SR, 0, "NONE", "not compressed"))
            wav.writeframes(np.tile(np.array([1234, -4321], dtype="<i2"), (n, 1)).tobytes())
    (tmp / "wavs.txt").write_text(f"file '{tmp / 'a.wav'}'\nfile '{tmp / 'b.wav'}'\n")
    ffmpeg("-f", "concat", "-safe", 0, "-i", tmp / "wavs.txt", "-c:a", "pcm_s16le",
           "-ar", SR, "-ac", 2, tmp / "merged.wav")
    with wave.open(str(tmp / "merged.wav"), "rb") as wav:
        assert (wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getnframes()) == (2, 2, SR, 27114)
        pcm = np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2").reshape(-1, 2)
        assert np.all(pcm[:, 0] == 1234) and np.all(pcm[:, 1] == -4321)
    print("Stereo PCM WAV merge PASS")
