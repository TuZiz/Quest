"""Create an isolated smoke server; no live worlds, databases or plugin configuration are modified."""
from pathlib import Path
import shutil
import yaml

root=Path(__file__).resolve().parents[1]
source=Path('C:/Users/32633/Desktop/leaf')
server=root/'target/smoke'
server.mkdir(parents=True,exist_ok=True)
for folder in ('libraries','versions'):
    shutil.copytree(source/folder,server/folder,dirs_exist_ok=True)
(server/'cache').mkdir(exist_ok=True)
shutil.copy2(source/'cache/mojang_26.3.jar',server/'cache/mojang_26.3.jar')
shutil.copy2(source/'leaf-26.3-4.jar',server/'server.jar')
shutil.copy2(source/'eula.txt',server/'eula.txt')
plugins=server/'plugins';plugins.mkdir(exist_ok=True)
shutil.copy2(root/'target/Quest-1.0.0.jar',plugins/'Quest-1.0.0.jar')
quest=plugins/'Quest';quest.mkdir(exist_ok=True)
shutil.copy2(root/'src/main/resources/config.yml',quest/'config.yml')
for folder in ('quests','chapters','gui','lang'):
    shutil.copytree(root/'src/main/resources'/folder,quest/folder,dirs_exist_ok=True)
for name,jar in [('Genlite','Genlite-1.0.0.jar'),('DreamSkyblock','DreamSkyblock-1.0-SNAPSHOT.jar')]:
    shutil.copy2(root/'.build/dependencies'/name/'target'/jar,plugins/jar)
shutil.copy2(source/'plugins/craft-engine-paper-plugin-26.9.2.jar',plugins/'craft-engine-paper-plugin-26.9.2.jar')
ce=plugins/'CraftEngine';ce.mkdir(exist_ok=True)
for folder in ('libs',):
    shutil.copytree(source/'plugins/CraftEngine'/folder,ce/folder,dirs_exist_ok=True)
shutil.copytree(root.parent/'Genlite/craftengine/genlite',ce/'resources/genlite',dirs_exist_ok=True)
shutil.copytree(source/'plugins/CraftEngine/resources/internal',ce/'resources/internal',dirs_exist_ok=True)
config=yaml.safe_load((source/'plugins/CraftEngine/config.yml').read_text(encoding='utf-8'))
config['metrics']=False
config['update-checker']=False
config['resource-pack']['workflows']={}
config['resource-pack']['self-host']['port']=25589
(ce/'config.yml').write_text(yaml.safe_dump(config,allow_unicode=True,sort_keys=False),encoding='utf-8')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25586\nonline-mode=false\nlevel-name=smoke-world\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nenable-rcon=false\nmax-players=2\n',encoding='utf-8')
print(server)
