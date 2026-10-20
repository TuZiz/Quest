from pathlib import Path
import shutil
import zipfile
import hashlib
import json

root=Path(__file__).resolve().parents[1]
release=root/'target/release';release.mkdir(parents=True,exist_ok=True)
artifacts=[root/'target/Quest-1.0.0.jar',root/'.build/dependencies/Genlite/target/Genlite-1.0.0.jar',root/'.build/dependencies/DreamSkyblock/target/DreamSkyblock-1.0-SNAPSHOT.jar']
with zipfile.ZipFile(artifacts[0]) as jar:
    forbidden=('org/bukkit/','io/papermc/','net/momirealms/','com/hiusers/','cn/genlite/','ym/dreamSkyblock/','org/junit/','ym/quest/smoke/')
    assert not any(name.startswith(forbidden) or name.endswith(('.kt','.java')) for name in jar.namelist()), 'Unexpected bundled classes/resources'
    assert not any(name.endswith(('.png','.ttf')) or name.startswith(('core/assets/','core/layout/')) for name in jar.namelist()), 'Quest no longer ships HUD materials'
hashes={}
for artifact in artifacts:
    destination=release/artifact.name
    shutil.copy2(artifact,destination)
    hashes[destination.name]=hashlib.sha256(destination.read_bytes()).hexdigest()
(release/'SHA256.json').write_text(json.dumps(hashes,indent=2),encoding='utf-8')
with zipfile.ZipFile(release/'Quest-1.0.0-bundle.zip','w',compression=zipfile.ZIP_DEFLATED) as bundle:
    for artifact in artifacts: bundle.write(release/artifact.name,artifact.name)
    bundle.write(release/'SHA256.json','SHA256.json')
    for name in ('README.md','COMPATIBILITY.md','VALIDATION.md','UPGRADE.md'): bundle.write(root/name,name)
print(json.dumps({'artifacts':hashes,'bundle':str(release/'Quest-1.0.0-bundle.zip')},indent=2))
