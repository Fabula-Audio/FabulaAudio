"""Host codec integration: decode WAV/MP3/M4A, prepare and finish ×6.
Requires FFmpeg/FFprobe. Does not replace testing on the affected phone.
"""
import json
import re
import shlex
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OPTIONS = shlex.split(re.search(r'OUTPUT_OPTIONS = "(.*?)"',
    (ROOT / 'app/src/main/java/com/fabula/audio/AudioImport.kt').read_text())[1])
main = (ROOT / 'app/src/main/java/com/fabula/audio/MainActivity.kt').read_text()
TEMPO = re.search(r'return "(atempo=1\.817121[^"]*)"', main)[1]

def ff(*args):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', *map(str, args)],
                   check=True, capture_output=True, timeout=60)

def probe(file):
    return json.loads(subprocess.check_output(['ffprobe', '-v', 'error',
        '-show_streams', '-show_format', '-of', 'json', str(file)]))

with tempfile.TemporaryDirectory(prefix='fabula-import-') as d:
    tmp = Path(d)
    ff('-f', 'lavfi', '-i', 'color=c=green:s=16x16', '-frames:v', 1, tmp/'cover.jpg')
    for channels, rate in ((1, 16000), (2, 44100), (2, 48000)):
        ff('-f', 'lavfi', '-i', f'sine=frequency=440:sample_rate={rate}:duration=7',
           '-ac', channels, '-c:a', 'pcm_s24le', tmp/'source.wav')
        ff('-i', tmp/'source.wav', '-c:a', 'libmp3lame', '-q:a', 4, tmp/'source.mp3')
        ff('-i', tmp/'source.wav', '-c:a', 'aac', '-b:a', '128k', tmp/'source.m4a')
        for extension in ('wav', 'mp3', 'm4a'):
            source = tmp/f'source.{extension}'
            if extension != 'wav':
                covered = tmp/f'covered.{extension}'
                ff('-i', source, '-i', tmp/'cover.jpg', '-map', '0:a', '-map', '1:v',
                   '-c', 'copy', '-disposition:v:0', 'attached_pic', covered)
                source = covered
            # Match SAF's extensionless local copy.
            (tmp/'input_main').write_bytes(source.read_bytes())
            ff('-i', tmp/'input_main', *OPTIONS, '-c:a', 'flac', tmp/'decoded.flac')
            streams = probe(tmp/'decoded.flac')['streams']
            assert len(streams) == 1 and streams[0]['codec_type'] == 'audio'
            assert int(streams[0]['sample_rate']) == rate and streams[0]['channels'] == channels
            ff('-i', tmp/'decoded.flac', *OPTIONS, '-af', 'adeclick,afftdn=nr=6:nf=-30',
               '-c:a', 'flac', tmp/'clean.flac')
            ff('-i', tmp/'clean.flac', '-af', TEMPO, '-c:a', 'flac', tmp/'fast.flac')
            (tmp/'repeat.txt').write_text(f"file '{tmp / 'fast.flac'}'\n" * 6)
            ff('-f', 'concat', '-safe', 0, '-i', tmp/'repeat.txt', '-c:a', 'flac', tmp/'result.flac')
            actual = float(probe(tmp/'result.flac')['format']['duration'])
            assert abs(actual - 7) < 1, (extension, rate, actual)
            print(f'{extension} {rate}Hz {channels}ch: decode/preparation/×6/repeat PASS', flush=True)
    # Extra lossless decode must not change accepted WAV sample values.
    ff('-i', tmp/'source.wav', '-f', 'f32le', tmp/'before.f32')
    ff('-i', tmp/'source.wav', *OPTIONS, '-c:a', 'flac', tmp/'wav-decoded.flac')
    ff('-i', tmp/'wav-decoded.flac', '-f', 'f32le', tmp/'after.f32')
    assert (tmp/'before.f32').read_bytes() == (tmp/'after.f32').read_bytes()
    print('PCM24 WAV normalization: bit-exact PASS')
