"""Install a supplied Fusion Pixel 12px SC font in server-only Xerr assets.

Run with Xerr directory and the extracted official 12px monospaced zh_hans TTF.
Backs up changed files, preserves unknown configuration, and is repeatable.
Does not reload plugins, generate/send packs, or touch player data.
"""
from pathlib import Path
import argparse
import datetime
import os
import re
import shutil
import yaml

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('xerr', type=Path)
parser.add_argument('font', type=Path)
args = parser.parse_args()
font_config = args.xerr / 'core/assets/font/text.yml'
layout = args.xerr / 'core/layout/quest_tracker.yml'
regex = args.xerr / 'core/regex/common.yml'
target = args.xerr / 'core/assets/font/ttf/quest_pixel_12.ttf'
source = args.font.read_bytes()
if source[:4] != b'\x00\x01\x00\x00':
    raise SystemExit('Expected a TrueType font; no files changed')

# Plan and parse everything before writing any live file.
text = font_config.read_text(encoding='utf-8')
config = yaml.safe_load(text)
if config['ui']['ttf'] not in ('fusion_pixel', 'quest_pixel_12'):
    raise SystemExit('Custom ui font present; refusing to overwrite it')
text, count = re.subn(r'(?m)^(  ttf:) .+$', r"\1 'quest_pixel_12'", text)
if count != 1:
    raise SystemExit('Expected exactly one font configuration')
text = re.sub(r'(?m)^  size: \d+$', '  size: 12', text)
text = re.sub(r'(?m)^  oversample: [\d.]+$', '  oversample: 1.0', text)
text = re.sub(r'^# Quest 字体版本.*\n', '# Quest 字体版本 2；12px 简体像素字体，使用原生整数像素避免笔画变形。\n', text)
layout_text = layout.read_text(encoding='utf-8')
layout_text = layout_text.replace('    x: -75\n', '    x: -76\n')
# The taller title stays inside the header band (panel top 44, divider 68).
layout_text = layout_text.replace('    y: -60\n', '    y: -50\n')
regex_text = regex.read_text(encoding='utf-8')
if 'quest_fullwidth_latin' not in yaml.safe_load(regex_text):
    regex_text += "\n# 此字体的 Latin-1 全角符号；其他字体仍使用原宽度。\nquest_fullwidth_latin:\n  regex: '[©®·¼½¾]'\n  width: 1\n  font-widths:\n    ui: 2\n"
planned = {font_config: text.encode(), layout: layout_text.encode(), regex: regex_text.encode(), target: source}
for path, content in planned.items():
    if path.suffix == '.yml':
        yaml.safe_load(content)
changed = {p: data for p, data in planned.items() if not p.exists() or p.read_bytes() != data}
if not changed:
    print('No changes; font configuration already installed')
    raise SystemExit(0)
backup = args.xerr / 'backups' / ('quest-font-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
for path in changed:
    if path.exists():
        copy = backup / path.relative_to(args.xerr)
        copy.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, copy)
for path, content in changed.items():
    temporary = path.with_name(path.name + '.quest-tmp')
    temporary.write_bytes(content)
    os.replace(temporary, path)
print('Updated', len(changed), 'server-only font/layout files; backup:', backup)
print('Apply: xerr reload -> xerr pack async (wait) -> ce reload pack (upload/send)')
