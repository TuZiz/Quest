"""Build a narrowly patched Xerr 2.1 JAR. Does not mutate the source or deploy it."""
from pathlib import Path
import argparse, hashlib, subprocess, zipfile, xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('source',type=Path)
parser.add_argument('output',type=Path)
args=parser.parse_args()
if args.source.resolve()==args.output.resolve(): raise SystemExit('Source and output must differ; original JAR preserved')
build=root/'target/verification/xerr-patch'; build.mkdir(parents=True,exist_ok=True)
jdk=Path('C:/Program Files/Zulu/zulu-25/bin')
report=ET.parse(root/'target/surefire-reports/TEST-ym.quest.ResourcesTest.xml')
classpath=next(p.attrib['value'] for p in report.findall('.//property') if p.attrib['name']=='java.class.path')
asm=Path('C:/Users/32633/.m2/repository/org/ow2/asm/asm/9.8/asm-9.8.jar')
subprocess.run([str(jdk/'javac.exe'),'-cp',classpath,'-d',str(build),str(root/'compat/Xerr/LayoutMiniMessage.java')],check=True)
subprocess.run([str(jdk/'javac.exe'),'-cp',str(asm),'-d',str(build),str(root/'tools/smoke/PatchXerr.java')],check=True)
entry='com/hiusers/xerr/api/entity/layout/LayoutEntity.class'
helper='com/hiusers/xerr/compat/LayoutMiniMessage.class'
with zipfile.ZipFile(args.source) as source:
    if helper in source.namelist(): raise SystemExit('Already patched; use the original backup as source')
    original=source.read(entry)
    expected='d4ee2d49edd57ab3435de87a82d69f0bcb69f0e80e2ddfee3374a47978cf2784'
    if hashlib.sha256(original).hexdigest()!=expected: raise SystemExit('Unsupported Xerr LayoutEntity fingerprint; original preserved')
    (build/'original.class').write_bytes(original)
    subprocess.run([str(jdk/'java.exe'),'-cp',str(build)+';'+str(asm),'PatchXerr',str(build/'original.class'),str(build/'patched.class')],check=True)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED) as output:
        for item in source.infolist(): output.writestr(item, (build/'patched.class').read_bytes() if item.filename==entry else source.read(item))
        output.write(build/helper,helper)
print('Xerr layout patch built:',args.output,'sha256='+hashlib.sha256(args.output.read_bytes()).hexdigest())
