"""Stage local, source-verified dependencies without changing the original repositories."""
from pathlib import Path
import shutil
import hashlib
import json
import sys

root = Path(__file__).resolve().parents[1]
base = Path(sys.argv[1]) if len(sys.argv) > 1 else root.parent
manifest = {}
for name in ('Genlite', 'DreamSkyblock'):
    source = base / name
    target = root / '.build/dependencies' / name
    target.mkdir(parents=True, exist_ok=True)
    for folder in ('src', 'libs', 'craftengine'):
        if (source / folder).exists():
            shutil.copytree(source / folder, target / folder, dirs_exist_ok=True)
    shutil.copy2(source / 'pom.xml', target / 'pom.xml')
    manifest[name] = {}
    for overlay in (root / 'compat' / name).rglob('*'):
        if overlay.is_file():
            relative = overlay.relative_to(root / 'compat' / name)
            original = source / relative
            manifest[name][str(relative)] = hashlib.sha256(original.read_bytes()).hexdigest() if original.exists() else 'new'
            dest = target / relative
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(overlay, dest)
(root / '.build/dependencies/source-manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
