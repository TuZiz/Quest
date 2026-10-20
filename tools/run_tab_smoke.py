"""Verify native TAB configuration/API and reloads in target/smoke only; never touches the live server."""
from pathlib import Path
import os
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile

root=Path(__file__).resolve().parents[1]
server=root/'target/smoke'
jdk=Path('C:/Program Files/Zulu/zulu-25/bin')
report=ET.parse(root/'target/surefire-reports/TEST-ym.quest.ResourcesTest.xml')
classpath=next(p.attrib['value'] for p in report.findall('.//property') if p.attrib['name']=='java.class.path')
classes=root/'target/verification/tab-smoke-classes'
classes.mkdir(parents=True,exist_ok=True)
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-cp',classpath,'-d',str(classes),str(root/'tools/smoke/TabSmoke.java')],check=True)
plugins=server/'plugins'
old_tab=plugins/'TAB-6.1.0.jar'
if old_tab.exists(): old_tab.unlink()
shutil.copy2(root/'.build/dependencies/TAB/TAB-6.2.1.jar',plugins/'TAB-6.2.1.jar')
tab=plugins/'TAB'
tab.mkdir(exist_ok=True)
shutil.copy2(root/'integrations/tab/config.yml',tab/'config.yml')
shutil.copy2(root/'target/Quest-1.0.0.jar',plugins/'Quest-1.0.0.jar')
for resource in ('lang/zh_cn.yml', 'quests/能源启动.yml'):
    destination=plugins/'Quest'/resource
    destination.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(root/'src/main/resources'/resource,destination)
with zipfile.ZipFile(plugins/'TabSmoke.jar','w') as jar:
    for compiled in classes.rglob('*.class'):
        jar.write(compiled,compiled.relative_to(classes).as_posix())
    jar.writestr('plugin.yml',"name: TabSmoke\nversion: '1.0'\nmain: ym.quest.smoke.TabSmoke\napi-version: '26.3'\ndepend:\n  - Quest\n  - TAB\ncommands:\n  tabsmoke:\n    description: 'Isolated TAB verification'\n")
log=root/'target/verification/tab-smoke-console.log'
with log.open('w',encoding='utf-8') as output:
    process=subprocess.Popen([str(jdk/'java.exe'),'--enable-native-access=ALL-UNNAMED','-Dterminal.jline=false','-Dterminal.ansi=false','-Xms512M','-Xmx1536M','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    def wait_for(marker,after=0,timeout=100):
        deadline=time.monotonic()+timeout
        while time.monotonic()<deadline and process.poll() is None:
            content=log.read_text(encoding='utf-8',errors='replace')[after:]
            if 'QUEST_TAB_FAILED' in content:
                raise RuntimeError('Native TAB check failed; see '+str(log))
            if marker in content:
                print(marker,flush=True)
                return
            time.sleep(0.5)
        raise RuntimeError('Timeout waiting for '+marker+'; see '+str(log))
    def command(value):
        process.stdin.write(value+'\n');process.stdin.flush()
    try:
        wait_for('QUEST_TAB_INITIAL_PASSED')
        start=len(log.read_text(encoding='utf-8',errors='replace'))
        command('tab reload')
        time.sleep(3)
        command('tabsmoke')
        wait_for('QUEST_TAB_RELOADED_PASSED',start,15)
        start=len(log.read_text(encoding='utf-8',errors='replace'))
        command('quest reload')
        time.sleep(3)
        command('tabsmoke')
        wait_for('QUEST_TAB_RELOADED_PASSED',start,15)
        command('stop')
        process.wait(timeout=40)
        if process.returncode!=0: raise RuntimeError('Isolated server exited '+str(process.returncode))
        print('TAB_SMOKE_PASSED; isolated server stopped normally',flush=True)
    finally:
        if process.poll() is None:
            command('stop')
            try: process.wait(timeout=40)
            except subprocess.TimeoutExpired: process.terminate();process.wait(timeout=15)
