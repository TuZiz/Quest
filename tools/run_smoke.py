from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile
import os

root=Path(__file__).resolve().parents[1]
server=root/'target/smoke'
jdk=Path('C:/Program Files/Zulu/zulu-25/bin')
report=ET.parse(root/'target/surefire-reports/TEST-ym.quest.ResourcesTest.xml')
classpath=next(p.attrib['value'] for p in report.findall('.//property') if p.attrib['name']=='java.class.path')
classes=root/'target/verification/smoke-classes';classes.mkdir(parents=True,exist_ok=True)
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-cp',classpath,'-d',str(classes),str(root/'tools/smoke/QuestSmoke.java')],check=True)
with zipfile.ZipFile(server/'plugins/QuestSmoke.jar','w') as jar:
    jar.write(classes/'ym/quest/smoke/QuestSmoke.class','ym/quest/smoke/QuestSmoke.class')
    jar.writestr('plugin.yml','name: QuestSmoke\nversion: 1.0\nmain: ym.quest.smoke.QuestSmoke\napi-version: \'26.3\'\ndepend:\n  - Quest\n  - CraftEngine\n  - Genlite\n  - DreamSkyblock\n')
log=root/'target/verification/smoke-console.log'
with log.open('w',encoding='utf-8') as output:
    process=subprocess.Popen([str(jdk/'java.exe'),'--enable-native-access=ALL-UNNAMED','-Dterminal.jline=false','-Dterminal.ansi=false','-Xms512M','-Xmx1536M','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    try:
        deadline=time.monotonic()+120
        while time.monotonic()<deadline and process.poll() is None:
            text=log.read_text(encoding='utf-8',errors='replace')
            if 'QUEST_SMOKE_PASSED' in text or 'QUEST_SMOKE_FAILED' in text: break
            time.sleep(1)
        text=log.read_text(encoding='utf-8',errors='replace')
        passed='QUEST_SMOKE_PASSED' in text and 'QUEST_SMOKE_FAILED' not in text
        print('SMOKE: '+('passed' if passed else 'failed; inspect '+str(log)),flush=True)
        if process.poll() is None:
            config=server/'plugins/Quest/quests/sky_start.yml'
            original=config.read_text(encoding='utf-8')
            try:
                config.write_text('schema-version: 1\nid: [\n',encoding='utf-8')
                process.stdin.write('quest admin reload\n');process.stdin.flush();time.sleep(2)
                process.stdin.write('quest admin debug 00000000-0000-0000-0000-000000000001\n');process.stdin.flush();time.sleep(1)
                diagnostic=log.read_text(encoding='utf-8',errors='replace')
                rollback='Quest operation failed' in diagnostic and all(name+':' in diagnostic for name in ('sky_start','first_fuel','first_resources'))
                passed=passed and rollback
                print('INVALID_RELOAD_PRESERVES_QUESTS: '+str(rollback),flush=True)
            finally:
                config.write_text(original,encoding='utf-8')
            process.stdin.write('quest admin reload\n');process.stdin.flush();time.sleep(3)
            process.stdin.write('stop\n');process.stdin.flush()
            process.wait(timeout=40)
        print('Server exited: '+str(process.returncode),flush=True)
        if not passed: raise SystemExit(1)
    finally:
        if process.poll() is None: process.terminate();process.wait(timeout=15)
