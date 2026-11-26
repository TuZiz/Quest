"""Headless, isolated Purpur 26.3 withdrawal -> PlayerVault -> Quest regression. No desktop/production data."""
from pathlib import Path
import os, shutil, subprocess, time, zipfile, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
LIVE=Path('C:/Users/32633/Desktop/leaf')
JDK=Path('C:/Program Files/Zulu/zulu-25/bin')

def main():
    server=ROOT/'target'/('warehouse-progress-smoke-'+str(time.time_ns()))
    plugins=server/'plugins';plugins.mkdir(parents=True)
    def link(source,destination):
        try:os.link(source,destination)
        except OSError:shutil.copy2(source,destination)
        return destination
    for folder in ['libraries','versions/26.3']:
        shutil.copytree(LIVE/folder,server/folder,copy_function=link)
    (server/'cache').mkdir()
    shutil.copy2(LIVE/'cache/mojang_26.3.jar',server/'cache/mojang_26.3.jar')
    shutil.copy2(LIVE/'purpur-26.3-2655.jar',server/'server.jar')
    (server/'eula.txt').write_text('eula=true\n')
    (server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25618\nonline-mode=false\nlevel-name=fixture\nview-distance=2\nsimulation-distance=2\nenable-rcon=false\nmax-players=0\ngenerate-structures=false\nlevel-type=minecraft:flat\n',encoding='utf-8')
    for name in ['Genlite-1.0.0.jar','DreamSkyblock-1.0-SNAPSHOT.jar','ItemHub-1.0-SNAPSHOT.jar','craft-engine-paper-plugin-26.9.2.jar']:
        shutil.copy2(LIVE/'plugins'/name,plugins/name)
    shutil.copy2(ROOT/'target/Quest-1.0.0.jar',plugins/'Quest-1.0.0.jar')
    pv=ROOT.parent/'PlayerVault/target/PlayerVault-1.0.0.jar'
    shutil.copy2(pv,plugins/pv.name)
    # Only configuration is copied: no SQLite, JSON ledger, player data or plugin state.
    for name in ['Quest','Genlite','DreamSkyblock','ItemHub','PlayerVault']:
        source=LIVE/'plugins'/name;destination=plugins/name;destination.mkdir()
        for file in source.glob('*.yml'):shutil.copy2(file,destination/file.name)
        for folder in ['gui','lang','genlite','quests','chapters','items']:
            if (source/folder).is_dir():shutil.copytree(source/folder,destination/folder)
    ce=plugins/'CraftEngine';ce.mkdir()
    shutil.copytree(LIVE/'plugins/CraftEngine/libs',ce/'libs',copy_function=link)
    for name in ['internal','genlite','quest-journal-ui','货币','强化石','星芽装备']:
        source=LIVE/'plugins/CraftEngine/resources'/name
        if source.exists():shutil.copytree(source,ce/'resources'/name)
    # Reuse an already isolated config with upload/send_pack workflows removed.
    shutil.copy2(ROOT/'target/pixel-smoke/plugins/CraftEngine/config.yml',ce/'config.yml')
    report=ET.parse(ROOT/'target/surefire-reports/TEST-ym.quest.WarehouseExtractionTest.xml')
    cp=next(p.attrib['value'] for p in report.findall('.//property') if p.attrib['name']=='java.class.path')+os.pathsep+str(pv)
    classes=server/'probe-classes';classes.mkdir()
    subprocess.run([str(JDK/'javac.exe'),'-encoding','UTF-8','-cp',cp,'-d',str(classes),str(ROOT/'tools/smoke/WarehouseProgressSmoke.java')],check=True)
    with zipfile.ZipFile(plugins/'WarehouseProgressSmoke.jar','w') as jar:
        for file in classes.rglob('*.class'):jar.write(file,file.relative_to(classes).as_posix())
        jar.writestr('plugin.yml',"name: WarehouseProgressSmoke\nversion: '1'\nmain: ym.quest.smoke.WarehouseProgressSmoke\napi-version: '26.3'\ndepend:\n  - Quest\n  - PlayerVault\n")
    log=server/'console.log'
    print('ISOLATED_SERVER '+str(server),flush=True)
    with log.open('w',encoding='utf-8') as out:
        process=subprocess.Popen([str(JDK/'java.exe'),'--enable-native-access=ALL-UNNAMED','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Dterminal.jline=false','-Dterminal.ansi=false','-Xms256M','-Xmx1024M','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True,encoding='utf-8',creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            deadline=time.monotonic()+180
            while time.monotonic()<deadline and process.poll() is None:
                raw=log.read_text(encoding='utf-8',errors='replace')
                if 'WAREHOUSE_PROGRESS_FAILED' in raw:raise RuntimeError('Regression failed: '+str(log))
                if 'WAREHOUSE_PROGRESS_PASSED' in raw:
                    print(next(line for line in raw.splitlines() if 'WAREHOUSE_PROGRESS_PASSED' in line),flush=True);break
                time.sleep(.5)
            else:raise RuntimeError('Isolated server timed out/exited: '+str(log))
        finally:
            if process.poll() is None:
                process.stdin.write('stop\n');process.stdin.flush()
                try:process.wait(timeout=35)
                except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=15)
    if process.returncode!=0:raise RuntimeError('Unexpected exit: '+str(process.returncode))
    print('PASSED '+str(log),flush=True)

if __name__=='__main__':main()
