"""Check Quest's missing-dependency path in the same isolated server, restoring all staged JARs afterwards."""
from pathlib import Path
import subprocess
import time
import os

root=Path(__file__).resolve().parents[1]
server=root/'target/smoke'
disabled=[]
process=None
log=root/'target/verification/optional-smoke-console.log'
try:
    for jar in (server/'plugins').glob('*.jar'):
        if jar.name != 'Quest-1.0.0.jar':
            dest=jar.with_suffix('.jar.disabled');jar.rename(dest);disabled.append((jar,dest))
    with log.open('w',encoding='utf-8') as output:
        process=subprocess.Popen(['C:/Program Files/Zulu/zulu-25/bin/java.exe','--enable-native-access=ALL-UNNAMED','-Dterminal.jline=false','-Dterminal.ansi=false','-Xms512M','-Xmx1536M','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        deadline=time.monotonic()+90
        while time.monotonic()<deadline and process.poll() is None:
            text=log.read_text(encoding='utf-8',errors='replace')
            if '[Quest] Integration CraftEngine=missing' in text and 'Done (' in text: break
            time.sleep(1)
        text=log.read_text(encoding='utf-8',errors='replace')
        passed=all(f'[Quest] Integration {name}=missing' in text for name in ('CraftEngine','DreamSkyblock','Genlite')) and 'NoClassDefFoundError' not in text
        print('OPTIONAL_SMOKE: '+('passed' if passed else 'failed'),flush=True)
        if process.poll() is None:
            process.stdin.write('quest\nquest admin debug 00000000-0000-0000-0000-000000000001\n');process.stdin.flush();time.sleep(2)
            process.stdin.write('stop\n');process.stdin.flush();process.wait(timeout=40)
        print('Server exited: '+str(process.returncode),flush=True)
        if not passed: raise SystemExit(1)
finally:
    if process is not None and process.poll() is None: process.terminate();process.wait(timeout=15)
    for jar,dest in disabled: dest.rename(jar)
