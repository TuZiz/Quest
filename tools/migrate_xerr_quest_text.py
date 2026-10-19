"""Explicit server-side Quest layout migration. Never packages materials in Quest."""
from pathlib import Path
import argparse, datetime, re, shutil

parser=argparse.ArgumentParser()
parser.add_argument('xerr',type=Path,help='Server plugins/Xerr directory')
args=parser.parse_args()
updates=[]
for name in ('quest_tracker.yml','quest_notifications.yml'):
    path=args.xerr/'core/layout'/name
    old=path.read_text(encoding='utf-8')
    new=re.sub(r"value: '\[(.*?)\]\(color=(#[0-9A-Fa-f]{6})\)'",
               lambda m:"value: '<"+m[2]+">"+m[1]+"'",old)
    if new!=old: updates.append((path,new))
if updates:
    backup=args.xerr/'backups'/('quest-minimessage-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
    backup.mkdir(parents=True,exist_ok=False)
    for path,new in updates:
        shutil.copy2(path,backup/path.name)
        temp=path.with_suffix('.yml.tmp')
        temp.write_text(new,encoding='utf-8')
        temp.replace(path)
    print('Migrated',len(updates),'layouts; originals:',backup)
else: print('No matching legacy Quest text; files unchanged')
