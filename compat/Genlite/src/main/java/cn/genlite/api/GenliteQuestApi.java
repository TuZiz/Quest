package cn.genlite.api;
import java.util.*;
/** Immutable, nonblocking snapshots; counters are durable, monotonic per home and metric. */
public interface GenliteQuestApi {
    boolean ready();
    Map<String,Long> counters();
    boolean hasGenerator(UUID home, Set<UUID> members);
}
