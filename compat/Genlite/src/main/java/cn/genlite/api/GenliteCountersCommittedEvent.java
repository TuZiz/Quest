package cn.genlite.api;
import org.bukkit.event.*;
import java.util.Map;
/** Fired on the Genlite IO executor AFTER atomic persistence. Never access Bukkit state in handlers. */
public final class GenliteCountersCommittedEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final Map<String,Long> counters;
    public GenliteCountersCommittedEvent(Map<String,Long> counters) {super(true);this.counters=Map.copyOf(counters);}
    public Map<String,Long> counters() {return counters;}
    @Override public HandlerList getHandlers() {return HANDLERS;}
    public static HandlerList getHandlerList() {return HANDLERS;}
}
