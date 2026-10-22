"""Stage the tested Genlite compatibility build through Bukkit's configured restart update folder."""
from pathlib import Path
from datetime import datetime
import hashlib,json,re,shutil,zipfile,os

project=Path(__file__).resolve().parents[1]
server=Path('C:/Users/32633/Desktop/leaf')
source=project/'.build/dependencies/Genlite/target/Genlite-1.0.0.jar'
tested=project/'target/smoke/plugins/Genlite-1.0.0.jar'
live=server/'plugins/Genlite-1.0.0.jar'
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert digest(source)==digest(tested),'Only the matching isolated-server-tested binary may be staged'
log=(project/'target/verification/introduction-smoke-console.log').read_text(encoding='utf-8',errors='replace')
assert 'QUEST_PLACEMENT_PASSED' in log and 'QUEST_FUEL_CHEST_PASSED' in log and 'QUEST_PLACEMENT_FAILED' not in log,'Placement and fuel integration verification must pass first'
with zipfile.ZipFile(source) as jar:
    assert 'cn/genlite/api/GenliteQuestApi.class' in jar.namelist(),'Missing Quest API'
    assert 'cn/genlite/api/GenliteCountersCommittedEvent.class' in jar.namelist(),'Missing durable event API'
    assert not any(n.startswith(('org/bukkit/','io/papermc/','org/junit/')) for n in jar.namelist()),'Server/test classes bundled'
config=(server/'bukkit.yml').read_text(encoding='utf-8')
assert re.search(r'(?m)^  update-folder: update\s*$',config),'Unexpected Bukkit update folder; originals retained'
backup=server/'plugins/Quest/backups'/('genlite-api-'+datetime.now().strftime('%Y%m%d-%H%M%S'))
backup.mkdir(parents=True,exist_ok=False)
shutil.copy2(live,backup/live.name)
destination=server/'plugins/update'/live.name
destination.parent.mkdir(parents=True,exist_ok=True)
if destination.exists():shutil.copy2(destination,backup/('pending-'+destination.name))
temporary=destination.with_suffix('.jar.pending')
shutil.copy2(source,temporary)
assert digest(temporary)==digest(source)
os.replace(temporary,destination)
report={'live_before':digest(live),'staged':digest(destination),'backup':str(backup),'restart_required':True}
(backup/'deployment.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
