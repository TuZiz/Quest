"""Run server-side menu/model checks in an isolated Purpur 26.3 process; no desktop control."""
from pathlib import Path
import os,shutil,subprocess,time,zipfile,re,xml.etree.ElementTree as ET,argparse

ROOT=Path(__file__).resolve().parents[1]
LIVE=Path('C:/Users/32633/Desktop/leaf')
SERVER=ROOT/'target/pixel-smoke'
PLUGINS=SERVER/'plugins'
JDK=Path('C:/Program Files/Zulu/zulu-25/bin')

def copytree(src,dst):
    def link_or_copy(a,b):
        if Path(b).exists() and os.path.samefile(a,b):return b
        try:os.link(a,b)
        except OSError:shutil.copy2(a,b)
    shutil.copytree(src,dst,dirs_exist_ok=True,copy_function=link_or_copy)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--stage',type=Path);args=parser.parse_args()
    SERVER.mkdir(parents=True,exist_ok=True);PLUGINS.mkdir(exist_ok=True)
    copytree(LIVE/'libraries',SERVER/'libraries')
    copytree(LIVE/'versions/26.3',SERVER/'versions/26.3')
    (SERVER/'cache').mkdir(exist_ok=True)
    shutil.copy2(LIVE/'cache/mojang_26.3.jar',SERVER/'cache/mojang_26.3.jar')
    shutil.copy2(LIVE/'purpur-26.3-2655.jar',SERVER/'server.jar')
    (SERVER/'eula.txt').write_text('eula=true\n')
    (SERVER/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25616\nonline-mode=false\nlevel-name=pixel-test\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nenable-rcon=false\nmax-players=0\ndifficulty=peaceful\ngenerate-structures=false\n',encoding='utf-8')
    for name in ['Genlite-1.0.0.jar','DreamSkyblock-1.0-SNAPSHOT.jar','ItemHub-1.0-SNAPSHOT.jar','craft-engine-paper-plugin-26.9.2.jar']:
        shutil.copy2(LIVE/'plugins'/name,PLUGINS/name)
    shutil.copy2(ROOT/'target/Quest-1.0.0.jar',PLUGINS/'Quest-1.0.0.jar')
    q=PLUGINS/'Quest';q.mkdir(exist_ok=True)
    for folder in ['quests','chapters','gui','lang']:
        source=args.stage/'gui' if args.stage and folder=='gui' else LIVE/'plugins/Quest'/folder
        shutil.copytree(source,q/folder,dirs_exist_ok=True)
    for name in ['config.yml','引导.yml','提示.yml','奖励.yml','教学.yml','tab.yml']:
        shutil.copy2(LIVE/'plugins/Quest'/name,q/name)
    # Never copy the live SQLite DB, machine state, island state or player preferences.
    ce=PLUGINS/'CraftEngine';ce.mkdir(exist_ok=True)
    copytree(LIVE/'plugins/CraftEngine/libs',ce/'libs')
    for name in ['internal','genlite','quest-journal-ui','货币','强化石']:
        source=args.stage/name if args.stage and name=='quest-journal-ui' else LIVE/'plugins/CraftEngine/resources'/name
        destination=ce/'resources'/name
        if name=='quest-journal-ui':
            assert destination.resolve().is_relative_to(SERVER.resolve())
            if destination.exists():shutil.rmtree(destination)
            shutil.copytree(source,destination)
        else:copytree(source,destination)
    config=(LIVE/'plugins/CraftEngine/config.yml').read_text(encoding='utf-8')
    config=re.sub(r'(?m)^      - type: (?:upload|send_pack)\n(?:        [^\n]*\n)*','',config)
    # Static test data does not need an Internet upload or delivery to players.
    (ce/'config.yml').write_text(config,encoding='utf-8')
    report=ET.parse(ROOT/'target/surefire-reports/TEST-ym.quest.JournalCardLayoutTest.xml')
    cp=next(x.attrib['value'] for x in report.findall('.//property') if x.attrib['name']=='java.class.path')
    classes=ROOT/'target/verification/pixel-journal-classes';classes.mkdir(parents=True,exist_ok=True)
    subprocess.run([str(JDK/'javac.exe'),'-encoding','UTF-8','-cp',cp,'-d',str(classes),str(ROOT/'tools/smoke/PixelJournalSmoke.java')],check=True)
    with zipfile.ZipFile(PLUGINS/'PixelJournalSmoke.jar','w') as z:
        for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
        z.writestr('plugin.yml',"name: PixelJournalSmoke\nversion: '1'\nmain: ym.quest.smoke.PixelJournalSmoke\napi-version: '26.3'\ndepend:\n  - Quest\n  - CraftEngine\n")
    log=ROOT/'target/verification/pixel-journal-console.log'
    pack_path=ce/'generated/resource_pack.zip'
    if pack_path.exists():pack_path.rename(pack_path.with_name('resource_pack.previous-'+str(time.time_ns())+'.zip'))
    with log.open('w',encoding='utf-8') as out:
        proc=subprocess.Popen([str(JDK/'java.exe'),'--enable-native-access=ALL-UNNAMED','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Dterminal.jline=false','-Dterminal.ansi=false','-Xms256M','-Xmx1024M','-jar','server.jar','--nogui'],cwd=SERVER,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True,encoding='utf-8',creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            deadline=time.monotonic()+150
            while time.monotonic()<deadline and proc.poll() is None:
                raw=log.read_text(encoding='utf-8',errors='replace')
                if 'PIXEL_JOURNAL_FAILED' in raw:raise RuntimeError('Native menu probe failed; '+str(log))
                if 'Failed to run reload_pack workflows' in raw:raise RuntimeError('CraftEngine pack workflow failed; '+str(log))
                if 'PIXEL_JOURNAL_PASSED' in raw and pack_path.exists() and zipfile.is_zipfile(pack_path):
                    print('PIXEL_JOURNAL_PASSED + generated resource pack; '+str(log),flush=True);break
                time.sleep(.5)
            else:raise RuntimeError('Isolated server timed out/exited; '+str(log))
        finally:
            if proc.poll() is None:
                proc.stdin.write('stop\n');proc.stdin.flush()
                try:proc.wait(timeout=35)
                except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
    if proc.returncode!=0:raise RuntimeError('Unexpected server exit '+str(proc.returncode))

if __name__=='__main__':main()
