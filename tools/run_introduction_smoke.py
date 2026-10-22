"""Verify the NPC/dialogue adapters in target/smoke only. Never starts or stops the live server."""
from pathlib import Path
import os
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile

root=Path(__file__).resolve().parents[1]
server=root/'target/smoke'
live=Path('C:/Users/32633/Desktop/leaf')
jdk=Path('C:/Program Files/Zulu/zulu-25/bin')
report=ET.parse(root/'target/surefire-reports/TEST-ym.quest.ResourcesTest.xml')
classpath=next(p.attrib['value'] for p in report.findall('.//property') if p.attrib['name']=='java.class.path')
classes=root/'target/verification/introduction-smoke-classes'
classes.mkdir(parents=True,exist_ok=True)
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-cp',classpath,'-d',str(classes),str(root/'tools/smoke/IntroductionSmoke.java')],check=True)
plugins=server/'plugins'
shutil.copy2(root/'target/Quest-1.0.0.jar',plugins/'Quest-1.0.0.jar')
for name in ('Adyeshach-2.2.0-pre.2.jar','Typewriter-0.9.0-beta-177.jar','packetevents-spigot-2.14.0.jar'):
    shutil.copy2(live/'plugins'/name,plugins/name)
shutil.copytree(live/'cache/taboolib',server/'cache/taboolib',dirs_exist_ok=True)
# Typewriter's Paper loader uses server-local Maven libraries; reuse only dependency files.
shutil.copytree(live/'libraries',server/'libraries',dirs_exist_ok=True)
(plugins/'Typewriter').mkdir(exist_ok=True)
(plugins/'Typewriter/config.yml').write_text('enabled: false\n',encoding='utf-8')
for resource in ('lang/zh_cn.yml','引导.yml'):
    path=plugins/'Quest'/resource;path.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(root/'src/main/resources'/resource,path)
with zipfile.ZipFile(plugins/'IntroductionSmoke.jar','w') as jar:
    for compiled in classes.rglob('*.class'): jar.write(compiled,compiled.relative_to(classes).as_posix())
    jar.writestr('plugin.yml',"name: IntroductionSmoke\nversion: '1.0'\nmain: ym.quest.smoke.IntroductionSmoke\napi-version: '26.3'\ndepend:\n  - Quest\n  - Typewriter\n  - Adyeshach\ncommands:\n  introsmoke:\n    description: 'Isolated introduction verification'\n")
log=root/'target/verification/introduction-smoke-console.log'
with log.open('w',encoding='utf-8') as output:
    process=subprocess.Popen([str(jdk/'java.exe'),'--enable-native-access=ALL-UNNAMED','-Dterminal.jline=false','-Dterminal.ansi=false','-Xms512M','-Xmx1536M','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    def wait_for(marker,after=0,timeout=120):
        deadline=time.monotonic()+timeout
        while time.monotonic()<deadline and process.poll() is None:
            content=log.read_text(encoding='utf-8',errors='replace')[after:]
            if 'QUEST_INTRO_FAILED' in content: raise RuntimeError('Introduction API check failed; see '+str(log))
            if marker in content: print(marker,flush=True);return
            time.sleep(.5)
        raise RuntimeError('Timeout waiting for '+marker+'; see '+str(log))
    def command(value): process.stdin.write(value+'\n');process.stdin.flush()
    try:
        wait_for('QUEST_INTRO_INITIAL_PASSED')
        start=len(log.read_text(encoding='utf-8',errors='replace'))
        command('quest reload');time.sleep(3);command('introsmoke')
        wait_for('QUEST_INTRO_RELOADED_PASSED',start,20)
        command('stop');process.wait(timeout=45)
        if process.returncode!=0: raise RuntimeError('Server exited '+str(process.returncode))
        print('INTRODUCTION_SMOKE_PASSED; isolated server stopped normally',flush=True)
    finally:
        if process.poll() is None:
            command('stop')
            try: process.wait(timeout=45)
            except subprocess.TimeoutExpired: process.terminate();process.wait(timeout=15)
