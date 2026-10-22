"""Verify placement API plus introduction/reload on the existing isolated target/smoke server."""
from pathlib import Path
import subprocess
import runpy
import xml.etree.ElementTree as ET
import zipfile
import shutil

root=Path(__file__).resolve().parents[1]
# Always exercise the matching compatibility JAR, never an older staged server binary.
shutil.copy2(root/'.build/dependencies/Genlite/target/Genlite-1.0.0.jar',root/'target/smoke/plugins/Genlite-1.0.0.jar')
report=ET.parse(root/'target/surefire-reports/TEST-ym.quest.ResourcesTest.xml')
classpath=next(p.attrib['value'] for p in report.findall('.//property') if p.attrib['name']=='java.class.path')
for name,dependencies in [('PlacementSmoke',['Quest','Genlite']),('QuestSmoke',['Quest','CraftEngine','Genlite','DreamSkyblock'])]:
    classes=root/'target/verification'/name
    classes.mkdir(parents=True,exist_ok=True)
    subprocess.run(['C:/Program Files/Zulu/zulu-25/bin/javac.exe','-encoding','UTF-8','-cp',classpath,'-d',str(classes),str(root/f'tools/smoke/{name}.java')],check=True)
    with zipfile.ZipFile(root/f'target/smoke/plugins/{name}.jar','w') as jar:
        for compiled in classes.rglob('*.class'): jar.write(compiled,compiled.relative_to(classes).as_posix())
        jar.writestr('plugin.yml',f"name: {name}\nversion: '1.0'\nmain: ym.quest.smoke.{name}\napi-version: '26.3'\ndepend:\n"+''.join(f'  - {dep}\n' for dep in dependencies))
runpy.run_path(str(root/'tools/run_introduction_smoke.py'),run_name='__main__')
log=(root/'target/verification/introduction-smoke-console.log').read_text(encoding='utf-8',errors='replace')
assert 'QUEST_PLACEMENT_PASSED' in log and 'QUEST_PLACEMENT_FAILED' not in log, 'Placement probe failed; see introduction-smoke-console.log'
assert 'QUEST_FUEL_CHEST_PASSED' in log, 'Fuel chest commit-to-Quest probe failed; see introduction-smoke-console.log'
assert 'QUEST_SMOKE_PASSED' in log and 'QUEST_SMOKE_FAILED' not in log, 'Quest API/menu probe failed; see introduction-smoke-console.log'
print('PLACEMENT_SMOKE_PASSED')
