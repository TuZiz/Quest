"""Requires the prepared isolated Leaf server. Does not touch live player data."""
from pathlib import Path
import os
import subprocess
import time
import zipfile
import io
from PIL import Image

root = Path(__file__).resolve().parents[1]
server = root / 'target/smoke'
log = root / 'target/verification/xerr-smoke-console.log'

def wait_for(predicate, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline and process.poll() is None:
        if predicate():
            return
        time.sleep(0.5)
    raise RuntimeError('Smoke timeout; inspect ' + str(log))

with log.open('w', encoding='utf-8') as output:
    process = subprocess.Popen([
        'C:/Program Files/Zulu/zulu-25/bin/java.exe', '--enable-native-access=ALL-UNNAMED',
        '-Dsun.net.client.defaultReadTimeout=15000', '-Dsun.net.client.defaultConnectTimeout=15000',
        '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Xms512M', '-Xmx1536M',
        '-jar', 'server.jar', '--nogui'], cwd=server, stdin=subprocess.PIPE,
        stdout=output, stderr=subprocess.STDOUT, text=True,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    def command(value):
        process.stdin.write(value + '\n')
        process.stdin.flush()
    def logs():
        return log.read_text(encoding='utf-8', errors='replace')
    try:
        wait_for(lambda: 'XERR_API_PASSED' in logs() or 'QUEST_SMOKE_FAILED' in logs(), 120)
        if 'QUEST_SMOKE_FAILED' in logs():
            raise RuntimeError('Xerr API smoke failed')
        stamp = time.time()
        command('xerr pack async')
        xerr = server / 'plugins/Xerr/generated/resource_pack.zip'
        wait_for(lambda: xerr.exists() and xerr.stat().st_mtime >= stamp and zipfile.is_zipfile(xerr), 90)
        stamp = time.time()
        command('ce reload pack')
        ce = server / 'plugins/CraftEngine/generated/resource_pack.zip'
        wait_for(lambda: ce.exists() and ce.stat().st_mtime >= stamp and zipfile.is_zipfile(ce), 90)
        with zipfile.ZipFile(xerr) as source, zipfile.ZipFile(ce) as merged:
            textures = [n for n in source.namelist() if n.endswith('.png') and 'quest' in n]
            if not textures:
                raise AssertionError('Quest textures absent in Xerr pack')
            for name in textures:
                # CraftEngine optimizes PNG encoding; compare decoded pixels, including alpha.
                before = Image.open(io.BytesIO(source.read(name))).convert('RGBA')
                after = Image.open(io.BytesIO(merged.read(name))).convert('RGBA')
                if before.size != after.size or before.tobytes() != after.tobytes():
                    raise AssertionError('CE native merge mismatch: ' + name)
        print('XERR_PACK_AND_CE_NATIVE_MERGE_PASSED: ' + str(len(textures)) + ' Quest textures', flush=True)
        command('quest admin reload')
        time.sleep(2)
        command('stop')
        process.wait(timeout=40)
        if process.returncode:
            raise RuntimeError('Server exit ' + str(process.returncode))
    finally:
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=15)
