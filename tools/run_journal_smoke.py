"""Verify native chest inventories and navigation in target/smoke; no live data or materials."""
from pathlib import Path
import os,shutil,subprocess,time,zipfile,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
server=root/'target/smoke';plugins=server/'plugins';jdk=Path('C:/Program Files/Zulu/zulu-25/bin')
report=ET.parse(root/'target/surefire-reports/TEST-ym.quest.ResourcesTest.xml')
cp=next(p.attrib['value'] for p in report.findall('.//property') if p.attrib['name']=='java.class.path')
classes=root/'target/verification/journal-smoke-classes';classes.mkdir(parents=True,exist_ok=True)
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-cp',cp,'-d',str(classes),str(root/'tools/smoke/JournalSmoke.java')],check=True)
archive=server/'verification-disabled-probes';archive.mkdir(exist_ok=True)
for p in plugins.glob('*Smoke.jar'):
    if p.name!='JournalSmoke.jar':shutil.move(str(p),str(archive/p.name))
shutil.copy2(root/'target/Quest-1.0.0.jar',plugins/'Quest-1.0.0.jar')
for name in ('chapters','tasks','settings'):
    shutil.copy2(root/f'src/main/resources/gui/{name}.yml',plugins/f'Quest/gui/{name}.yml')
shutil.copy2(root/'src/main/resources/lang/zh_cn.yml',plugins/'Quest/lang/zh_cn.yml')
with zipfile.ZipFile(plugins/'JournalSmoke.jar','w') as jar:
    for p in classes.rglob('*.class'):jar.write(p,p.relative_to(classes).as_posix())
    jar.writestr('plugin.yml',"name: JournalSmoke\nversion: '1.0'\nmain: ym.quest.smoke.JournalSmoke\napi-version: '26.3'\ndepend:\n  - Quest\n  - CraftEngine\n")
log=root/'target/verification/journal-smoke-console.log'
with log.open('w',encoding='utf-8') as out:
    proc=subprocess.Popen([str(jdk/'java.exe'),'--enable-native-access=ALL-UNNAMED','-Dterminal.jline=false','-Dterminal.ansi=false','-Xms512M','-Xmx1536M','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    try:
        deadline=time.monotonic()+130
        while time.monotonic()<deadline and proc.poll() is None:
            raw=log.read_text(encoding='utf-8',errors='replace')
            if 'QUEST_JOURNAL_FAILED' in raw:raise RuntimeError('Native GUI failed; '+str(log))
            if 'QUEST_JOURNAL_PASSED' in raw:print('QUEST_JOURNAL_PASSED',flush=True);break
            time.sleep(.5)
        else:raise RuntimeError('Native GUI timeout; '+str(log))
    finally:
        if proc.poll() is None:
            proc.stdin.write('stop\n');proc.stdin.flush()
            try:proc.wait(timeout=40)
            except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
    if proc.returncode!=0:raise RuntimeError('Server exit '+str(proc.returncode))
print('JOURNAL_NATIVE_VERIFICATION_PASSED; isolated server stopped normally',flush=True)
