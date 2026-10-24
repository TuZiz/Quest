"""Stage local, source-verified dependencies without changing the original repositories."""
from pathlib import Path
import shutil
import hashlib
import json
import sys
import runpy

root = Path(__file__).resolve().parents[1]
base = Path(sys.argv[1]) if len(sys.argv) > 1 else root.parent
# Regenerate narrow overlays against the CURRENT sources; fail on source drift instead of
# silently replacing newer integration behavior with an earlier full-file overlay.
# Reuse native-hook detection and generate any required legacy overlays once.
native_projects = runpy.run_path(str(root / 'tools/make_compat.py'))['native_projects']
manifest = {}
for name in ('Genlite', 'DreamSkyblock'):
    source = base / name
    target = root / '.build/dependencies' / name
    target.mkdir(parents=True, exist_ok=True)
    for folder in ('src', 'libs', 'craftengine'):
        staged = target / folder
        # Only disposable build staging is cleared; never touch source projects or server data.
        assert staged.resolve().is_relative_to((root / '.build/dependencies').resolve())
        if staged.exists():
            shutil.rmtree(staged)
        if (source / folder).exists():
            shutil.copytree(source / folder, staged)
    shutil.copy2(source / 'pom.xml', target / 'pom.xml')
    manifest[name] = {}
    if name in native_projects:
        manifest[name]['mode'] = 'native-quest-api'
        continue
    for overlay in (root / 'compat' / name).rglob('*'):
        if overlay.is_file():
            relative = overlay.relative_to(root / 'compat' / name)
            original = source / relative
            manifest[name][str(relative)] = hashlib.sha256(original.read_bytes()).hexdigest() if original.exists() else 'new'
            dest = target / relative
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(overlay, dest)
(root / '.build/dependencies/source-manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
