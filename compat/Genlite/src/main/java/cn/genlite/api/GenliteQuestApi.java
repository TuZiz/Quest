package cn.genlite.api;
import java.util.*;
/** Immutable, nonblocking snapshots; counters are durable, monotonic per home and metric. */
public interface GenliteQuestApi {
    boolean ready();
    Map<String,Long> counters();
    boolean hasGenerator(UUID home, Set<UUID> members);
    /** Current committed machine state, including type-specific level and placement. */
    Map<String,Long> teachingSnapshot();
    record TeachingMachine(UUID home,String type,int level) {}
    TeachingMachine teachingMachine(UUID id);
}
