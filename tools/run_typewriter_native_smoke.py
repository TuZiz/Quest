"""Run the real beta-177 Library/BasicExtension probe in target/smoke; never control the live server."""
from pathlib import Path
import os
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile

root=Path(__file__).resolve().parents[1]
server=root/'target/smoke'
plugins=server/'plugins'
live=Path('C:/Users/32633/Desktop/leaf')
jdk=Path('C:/Program Files/Zulu/zulu-25/bin')
report=ET.parse(root/'target/surefire-reports/TEST-ym.quest.ResourcesTest.xml')
classpath=next(p.attrib['value'] for p in report.findall('.//property') if p.attrib['name']=='java.class.path')
classes=root/'target/verification/typewriter-native-smoke-classes'
classes.mkdir(parents=True,exist_ok=True)
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-cp',classpath,'-d',str(classes),str(root/'tools/smoke/TypewriterNativeSmoke.java')],check=True)
for name in ('Typewriter-0.9.0-beta-177.jar','packetevents-spigot-2.14.0.jar','Genlite-1.0.0.jar','DreamSkyblock-1.0-SNAPSHOT.jar'):
    shutil.copy2(live/'plugins'/name,plugins/name)
shutil.copy2(root/'target/Quest-1.0.0.jar',plugins/'Quest-1.0.0.jar')
for resource in ('lang/zh_cn.yml','引导.yml'):
    path=plugins/'Quest'/resource
    path.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(root/'src/main/resources'/resource,path)
pages=plugins/'Typewriter/pages'
pages.mkdir(parents=True,exist_ok=True)
for page in (root/'integrations/typewriter/pages').glob('*.json'):
    shutil.copy2(page,pages/page.name)
subprocess.run(['python',str(root/'tools/install_typewriter_guide.py'),'--server-root',str(server),
    '--basic-jar',str(root/'.build/typewriter-native/BasicExtension.jar')],check=True)
with zipfile.ZipFile(plugins/'TypewriterNativeSmoke.jar','w') as jar:
    for compiled in classes.rglob('*.class'):
        jar.write(compiled,compiled.relative_to(classes).as_posix())
    jar.writestr('plugin.yml',"name: TypewriterNativeSmoke\nversion: '1.0'\nmain: ym.quest.smoke.TypewriterNativeSmoke\napi-version: '26.3'\ndepend:\n  - Quest\n  - Typewriter\n")
quarantine=root/'target/verification/native-probe-disabled'
quarantine.mkdir(exist_ok=True)
moved=[]
for file in plugins.glob('*Smoke.jar'):
    if file.name=='TypewriterNativeSmoke.jar':
        continue
    destination=quarantine/file.name
    if destination.exists():
        raise RuntimeError(f'Existing probe backup conflict: {destination}')
    shutil.move(str(file),str(destination));moved.append((destination,file))
log=root/'target/verification/typewriter-native-console.log'
try:
    with log.open('w',encoding='utf-8') as output:
        process=subprocess.Popen([str(jdk/'java.exe'),'--enable-native-access=ALL-UNNAMED',
            '-Dterminal.jline=false','-Dterminal.ansi=false','-Xms512M','-Xmx1536M','-jar','server.jar','--nogui'],
            cwd=server,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        try:
            deadline=time.monotonic()+120
            while process.poll() is None and time.monotonic()<deadline:
                content=log.read_text(encoding='utf-8',errors='replace')
                if 'TYPEWRITER_NATIVE_FAILED' in content:
                    raise RuntimeError('Native Typewriter probe failed; see '+str(log))
                if 'TYPEWRITER_NATIVE_PASSED' in content:
                    print('TYPEWRITER_NATIVE_PASSED',flush=True)
                    break
                time.sleep(.5)
            else:
                raise RuntimeError('Probe timed out or isolated server exited; see '+str(log))
        finally:
            if process.poll() is None:
                process.stdin.write('stop\n');process.stdin.flush()
                try: process.wait(timeout=45)
                except subprocess.TimeoutExpired: process.terminate();process.wait(timeout=15)
finally:
    for source,destination in moved:
        shutil.move(str(source),str(destination))
