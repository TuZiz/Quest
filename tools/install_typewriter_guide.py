"""Install the verified native Typewriter guide, preserving existing pages and backing up changed config."""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import re
import shutil
import zipfile
import yaml

parser=argparse.ArgumentParser()
parser.add_argument('--server-root',type=Path,required=True)
parser.add_argument('--basic-jar',type=Path,required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
server=args.server_root.resolve()
quest=server/'plugins/Quest'
tw=server/'plugins/Typewriter'
with zipfile.ZipFile(args.basic_jar) as z:
    metadata=json.loads(z.read('extension.json'))['extension']
    if metadata['engineVersion']!='0.9.0-beta-177':
        raise ValueError('BasicExtension must match Typewriter 0.9.0-beta-177')
backup=quest/'backups'/('native-typewriter-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
changes=[]
lang=quest/'lang/zh_cn.yml'
raw=lang.read_text(encoding='utf-8-sig') if lang.exists() else ''
language=yaml.safe_load(raw) or {}
old_lines=language.get('introduction',{}).get('dialogue',[])
for source in (root/'integrations/typewriter/pages').glob('*.json'):
    page=json.loads(source.read_text(encoding='utf-8'))
    if source.name=='quest_first_guide.json' and old_lines:
        lines=[]
        for line in old_lines:
            # Retired stock instruction describes the old custom chat implementation.
            if '（基岩版玩家可直接阅读此聊天引导' in line:
                continue
            lines.append(line.split('»',1)[-1].strip())
        if not lines:
            raise ValueError('Existing introduction.dialogue has no usable text; original retained')
        spoken=[]
        for i,line in enumerate(lines,1):
            spoken.append({'id':f'quest_guide_{i:02d}','name':f'quest_guide_{i:02d}',
                'type':'spoken','criteria':[],'modifiers':[],
                'triggers':[f'quest_guide_{i+1:02d}' if i<len(lines) else 'quest_guide_complete'],
                'speaker':'quest_guide_speaker','text':line,'duration':1800})
        page['entries']=spoken+[page['entries'][-1]]
    target=tw/'pages'/source.name
    data=(json.dumps(page,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    if target.exists():
        # Editors own existing pages after installation; never regenerate them from Quest.
        current=json.loads(target.read_text(encoding='utf-8-sig'))
        if current.get('id')!=page['id']:
            raise ValueError(f'Conflicting Typewriter page: {target}; original retained')
    else:
        changes.append((target,data))
if raw:
    section=''
    skip=False
    kept=[]
    for line in raw.splitlines(keepends=True):
        if line and not line.startswith((' ','#','\r','\n')):
            section=line.split(':',1)[0]
        if section=='introduction' and re.match(r'^  [^ ]',line):
            skip=line.strip().split(':',1)[0] in ('dialogue','confirm','fallback-confirm')
        elif section!='introduction':
            skip=False
        if not skip and not ('/quest guide finish' in line and section=='introduction'):
            kept.append(line)
    next_raw=''.join(kept)
    yaml.safe_load(next_raw)
    if next_raw!=raw:
        changes.append((lang,next_raw.encode('utf-8')))
guide=quest/'引导.yml'
if guide.exists():
    content=guide.read_text(encoding='utf-8-sig')
    if 'typewriter-entry-id' not in (yaml.safe_load(content) or {}):
        content=content.rstrip()+"\n# 对话内容在Typewriter/pages内编辑，Quest只绑定首条ID。\ntypewriter-entry-id: 'quest_guide_01'\n"
        changes.append((guide,content.encode('utf-8')))
snippets=tw/'snippets.yml'
content=snippets.read_text(encoding='utf-8-sig') if snippets.exists() else ''
if 'dialogue' not in (yaml.safe_load(content) or {}):
    content=content.rstrip()+'\n\n'+(root/'integrations/typewriter/snippets.yml').read_text(encoding='utf-8')
    yaml.safe_load(content)
    changes.append((snippets,content.encode('utf-8')))
extension=tw/'extensions/BasicExtension.jar'
if not extension.exists() or hashlib.sha256(extension.read_bytes()).digest()!=hashlib.sha256(args.basic_jar.read_bytes()).digest():
    changes.append((extension,args.basic_jar.read_bytes()))
if changes:
    backup.mkdir(parents=True)
    for target,data in changes:
        if target.exists():
            saved=backup/target.relative_to(server/'plugins')
            saved.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(target,saved)
        target.parent.mkdir(parents=True,exist_ok=True)
        temporary=target.with_name(target.name+'.quest-native.tmp')
        temporary.write_bytes(data)
        temporary.replace(target)
print(json.dumps({'changed':[str(p) for p,_ in changes],'backup':str(backup) if changes else None,
    'engineVersion':metadata['engineVersion'],'pages_edit_in':str(tw/'pages'),'database_modified':False},ensure_ascii=False))
