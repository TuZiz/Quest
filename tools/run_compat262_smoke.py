from pathlib import Path
import shutil,subprocess,zipfile,xml.etree.ElementTree as ET,time,os
r=Path.cwd();live=Path('C:/Users/32633/Desktop/leaf');server=Path('D:/codex/DungeonCore/target/altar-smoke262');plugins=server/'plugins'
for source in (r/'target/Quest-1.0.0.jar',Path('D:/codex/Genlite/target/Genlite-1.0.0.jar'),live/'plugins/DreamSkyblock-1.0-SNAPSHOT.jar'):
 shutil.copy2(source,plugins/source.name)
cp=next(p.attrib['value'] for p in ET.parse(r/'target/surefire-reports/TEST-ym.quest.ResourcesTest.xml').findall('.//property') if p.attrib['name']=='java.class.path')
classes=r/'target/verification/compat262-probe';classes.mkdir(parents=True,exist_ok=True)
source=r/'tools/smoke/IntroductionMenuSmoke.java';jdk=Path('C:/Program Files/Zulu/zulu-25/bin')
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-cp',cp,'-d',str(classes),str(source)],check=True)
with zipfile.ZipFile(plugins/'IntroductionMenuSmoke.jar','w') as z:
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
 z.writestr('plugin.yml',"name: IntroductionMenuSmoke\nversion: '1'\nmain: ym.quest.smoke.IntroductionMenuSmoke\napi-version: '26.2'\ndepend:\n  - Quest\n")
log=r/'target/verification/purpur262-console.log'
with log.open('w',encoding='utf-8') as out:
 p=subprocess.Popen([str(jdk/'java.exe'),'@java.args'],cwd=server,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True,encoding='utf-8',creationflags=subprocess.CREATE_NO_WINDOW)
 try:
  deadline=time.monotonic()+100
  while p.poll() is None and time.monotonic()<deadline:
   text=log.read_text(encoding='utf-8',errors='replace')
   if 'INTRODUCTION_MENU_FAILED' in text:raise RuntimeError('NPC probe failed')
   if 'INTRODUCTION_MENU_PASSED' in text and 'ALTAR_PROBE_PASS' in text:
    print('PURPUR262 PASS: Quest NPC/SQLite + Dungeon/MM/CE probes',flush=True);break
   time.sleep(.5)
  else:raise RuntimeError('Startup/probe timeout; inspect '+str(log))
 finally:
  if p.poll() is None:
   p.stdin.write('stop\n');p.stdin.flush()
   try:p.wait(timeout=35)
   except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=15)
