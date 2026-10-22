"""Apply the tested JVM menu migration without starting/stopping the live Minecraft server."""
from pathlib import Path
import hashlib,shutil,subprocess,sys,xml.etree.ElementTree as ET
project=Path(__file__).resolve().parents[1]
data=Path(sys.argv[1]).resolve()
live=Path('C:/Users/32633/Desktop/leaf/plugins/Quest').resolve()
assert data in (live,(project/'target/smoke/plugins/Quest').resolve()),'Explicit known Quest data directory required'
backup=data/'backups/category-cycle-20261022';backup.mkdir(parents=True,exist_ok=True)
if not (backup/'gui').exists():shutil.copytree(data/'gui',backup/'gui')
report=ET.parse(project/'target/surefire-reports/TEST-ym.quest.ResourcesTest.xml')
cp=next(p.attrib['value'] for p in report.findall('.//property') if p.attrib['name']=='java.class.path')
classes=project/'target/verification/menu-migration-classes';classes.mkdir(parents=True,exist_ok=True)
source=classes/'MigrateMenus.java'
source.write_text('''import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public class MigrateMenus {
  public static void main(String[] args) throws Exception {
    var bundled=new LinkedHashMap<String,String>();
    try(var jar=new ZipFile(args[1])) {
      for(String name:List.of("chapters","tasks","settings")) {
        try(var input=jar.getInputStream(jar.getEntry("gui/"+name+".yml"))) {
          bundled.put(name,new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        }
      }
    }
    var root=Path.of(args[0]);
    ym.quest.config.MenuConfigUpgrade.INSTANCE.migrate(root,bundled);
    ym.quest.config.TrackingHudTextUpgrade.INSTANCE.migrate(root);
    ym.quest.config.CategoryButtonUpgrade.INSTANCE.migrate(root,bundled.get("chapters"));
    var first=new HashMap<String,String>();
    try(var files=Files.list(root.resolve("gui"))) { files.forEach(p->{try{first.put(p.getFileName().toString(),Files.readString(p));}catch(Exception e){throw new RuntimeException(e);}}); }
    if(!first.keySet().equals(Set.of("chapters.yml","tasks.yml","settings.yml"))) throw new IllegalStateException("Extra GUI files: "+first.keySet());
    ym.quest.config.MenuConfigUpgrade.INSTANCE.migrate(root,bundled);
    ym.quest.config.TrackingHudTextUpgrade.INSTANCE.migrate(root);
    ym.quest.config.CategoryButtonUpgrade.INSTANCE.migrate(root,bundled.get("chapters"));
    for(var entry:first.entrySet()) if(!Files.readString(root.resolve("gui/"+entry.getKey())).equals(entry.getValue())) throw new IllegalStateException("Repeated migration changed menu");
    System.out.println("THREE_MENUS_MIGRATION_PASSED: "+first.keySet()+"; repeated load byte stable");
  }
}
''',encoding='utf-8')
jdk=Path('C:/Program Files/Zulu/zulu-25/bin')
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-cp',cp,'-d',str(classes),str(source)],check=True)
subprocess.run([str(jdk/'java.exe'),'-cp',str(classes)+';'+cp,'MigrateMenus',str(data),str(project/'target/Quest-1.0.0.jar')],check=True)
jar=data.parent/'Quest-1.0.0.jar'
if not (backup/'Quest-1.0.0.jar').exists():shutil.copy2(jar,backup/'Quest-1.0.0.jar')
shutil.copy2(project/'target/Quest-1.0.0.jar',jar)
hash=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert hash(jar)==hash(project/'target/Quest-1.0.0.jar')==hash(project/'target/smoke/plugins/Quest-1.0.0.jar')
print('Staged built/native tested JAR '+hash(jar)+'; backup '+str(backup))
