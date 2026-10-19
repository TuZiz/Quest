"""Requires the prepared isolated Leaf server. Does not touch live player data."""
from pathlib import Path
import os
import subprocess
import time
import zipfile
import io
import json
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
        wait_for(lambda: ('XERR_API_PASSED' in logs() and 'XERR_ASYNC_PASSED' in logs()) or 'QUEST_SMOKE_FAILED' in logs(), 120)
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
            # Validate actual renderer font references, not just texture presence.
            # A stale pack can contain the PNGs while missing ui_position_N fonts.
            rendered=json.loads((root/'target/verification/xerr-tracker-final.json').read_text(encoding='utf-8'))
            fonts=set()
            def collect(value):
                if isinstance(value,dict):
                    if 'font' in value: fonts.add(value['font'])
                    for child in value.values(): collect(child)
                elif isinstance(value,list):
                    for child in value: collect(child)
            collect(rendered)
            collect(json.loads((root/'target/verification/xerr-compact-final.json').read_text(encoding='utf-8')))
            for font in fonts:
                namespace,relative=font.split(':',1)
                name='assets/'+namespace+'/font/'+relative+'.json'
                if name not in source.namelist() or name not in merged.namelist():
                    raise AssertionError('Rendered font absent from generated/merged pack: '+font)
                if json.loads(source.read(name))!=json.loads(merged.read(name)):
                    raise AssertionError('Rendered font changed during CE merge: '+font)
            # Check the client's version-specific placement shaders survive merge too.
            shaders=[n for n in source.namelist() if '/shaders/' in n and not n.endswith('/')]
            for name in shaders:
                if name not in merged.namelist():
                    raise AssertionError('Position shader absent during CE merge: '+name)
                equal=json.loads(source.read(name))==json.loads(merged.read(name)) if name.endswith('.json') else source.read(name)==merged.read(name)
                if not equal:
                    raise AssertionError('Position shader absent/changed during CE merge: '+name)
            # Display height does not shrink bitmap source cells. Vanilla font atlases
            # cannot stitch a single source glyph larger than 256x256.
            checked = 0
            for name in source.namelist():
                if '/font/' not in name or not name.endswith('.json') or 'quest' not in name:
                    continue
                for provider in json.loads(source.read(name)).get('providers', []):
                    if provider.get('type') != 'bitmap':
                        continue
                    namespace, relative = provider['file'].split(':', 1)
                    texture = 'assets/' + namespace + '/textures/' + relative
                    bitmap = Image.open(io.BytesIO(source.read(texture)))
                    rows = provider['chars']
                    columns = len(rows[0])
                    if not columns or any(len(row) != columns for row in rows):
                        raise AssertionError('Invalid bitmap grid: ' + name)
                    width, height = bitmap.width / columns, bitmap.height / len(rows)
                    if width > 256 or height > 256:
                        raise AssertionError(f'Bitmap source glyph exceeds font atlas: {texture} {width}x{height}')
                    checked += 1
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
        print('XERR_BITMAP_ATLAS_PASSED: ' + str(checked) + ' providers', flush=True)
        print('XERR_RENDERED_FONTS_AND_SHADERS_PASSED: '+str(len(fonts))+' fonts, '+str(len(shaders))+' shaders',flush=True)
        command('quest reload')
        time.sleep(2)
        command('stop')
        process.wait(timeout=40)
        if process.returncode:
            raise RuntimeError('Server exit ' + str(process.returncode))
    finally:
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=15)
