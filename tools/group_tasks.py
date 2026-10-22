"""Apply the same durable task-folder migration as Quest startup, without restarting the live server."""
from pathlib import Path
import hashlib,shutil,subprocess,xml.etree.ElementTree as ET

project=Path(__file__).resolve().parents[1]
data=Path('C:/Users/32633/Desktop/leaf/plugins/Quest')
backup=data/'backups/task-folders-20261022'
backup.mkdir(parents=True,exist_ok=True)
if not (backup/'quests').exists():shutil.copytree(data/'quests',backup/'quests')
live_jar=data.parent/'Quest-1.0.0.jar'
if not (backup/live_jar.name).exists():shutil.copy2(live_jar,backup/live_jar.name)
report=ET.parse(project/'target/surefire-reports/TEST-ym.quest.ResourcesTest.xml')
cp=next(p.attrib['value'] for p in report.findall('.//property') if p.attrib['name']=='java.class.path')
classes=project/'target/verification/task-folder-classes'
classes.mkdir(parents=True,exist_ok=True)
source=classes/'GroupTasks.java'
source.write_text('''import java.nio.file.*;
public class GroupTasks {
  public static void main(String[] args) throws Exception {
    var root=Path.of(args[0]);
    ym.quest.config.QuestTaskFiles.INSTANCE.groupFirstChapter(root);
    var before=ym.quest.config.QuestTaskFiles.INSTANCE.files(root.resolve("quests"));
    ym.quest.config.QuestTaskFiles.INSTANCE.groupFirstChapter(root);
    if(!before.equals(ym.quest.config.QuestTaskFiles.INSTANCE.files(root.resolve("quests")))) throw new IllegalStateException("Not idempotent");
    System.out.println("TASK_FOLDERS_MIGRATION_PASSED: "+before);
  }
}
''',encoding='utf-8')
jdk=Path('C:/Program Files/Zulu/zulu-25/bin')
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-cp',cp,'-d',str(classes),str(source)],check=True)
subprocess.run([str(jdk/'java.exe'),'-cp',str(classes)+';'+cp,'GroupTasks',str(data)],check=True)
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
built=project/'target/Quest-1.0.0.jar'
assert digest(built)==digest(project/'target/smoke/plugins/Quest-1.0.0.jar')
shutil.copy2(built,live_jar)
assert digest(live_jar)==digest(built)
print('Staged tested JAR '+digest(live_jar)+'; backup '+str(backup))
