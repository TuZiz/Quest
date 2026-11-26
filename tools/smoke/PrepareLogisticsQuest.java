import ym.quest.config.*;
import ym.quest.domain.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Offline YAML migration and graph validation, deliberately never opens player data. */
public class PrepareLogisticsQuest {
    public static void main(String[] args) throws Exception {
        Path resources=Path.of(args[0]),stage=Path.of(args[1]);
        LogisticsTutorialUpgrade.INSTANCE.migrate(stage,name -> {
            try {return Files.readString(resources.resolve(name));}
            catch(IOException error) {throw new UncheckedIOException(error);}
        });
        var registry=new ConditionRegistry();List<QuestDefinition> quests=new ArrayList<>();
        for(Path path:QuestTaskFiles.INSTANCE.files(stage.resolve("quests"))) {
            var y=new YamlConfiguration();y.load(path.toFile());List<Objective> objectives=new ArrayList<>();
            for(var row:y.getMapList("objectives")) {
                String type=String.valueOf(row.get("type"));
                if(!registry.contains(type)) throw new IllegalArgumentException(path+": unknown objective "+type);
                String resource=row.get("resource")==null?null:String.valueOf(row.get("resource"));
                long target=((Number)row.get("target")).longValue();if(target<1) throw new IllegalArgumentException(path+": invalid target");
                objectives.add(new Objective(String.valueOf(row.get("id")),type,target,resource,String.valueOf(row.get("description")),registry.metric(type,resource),
                        Set.of("island.member","genlite.placed","genlite.level","genlite.collector","genlite.connected").contains(type)));
            }
            quests.add(new QuestDefinition(y.getString("id"),y.getString("name"),y.getStringList("description"),y.getString("icon"),List.of(),objectives,List.of(),
                    QuestCategory.valueOf(y.getString("category","main").toUpperCase(Locale.ROOT)),Map.of()));
        }
        List<ChapterDefinition> chapters=new ArrayList<>();
        for(Path path:ChapterConfigUpgrade.INSTANCE.files(stage.resolve("chapters"))) {
            var y=new YamlConfiguration();y.load(path.toFile());chapters.add(ChapterFiles.INSTANCE.parse(y,path.toString()));
        }
        ChapterGraph.INSTANCE.prepare(quests,chapters);
        System.out.println("Prepared Quest logistics graph: "+quests.size()+" tasks, "+chapters.size()+" chapters; "+stage);
    }
}
