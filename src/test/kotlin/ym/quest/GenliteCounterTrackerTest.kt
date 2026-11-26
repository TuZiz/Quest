package ym.quest

import ym.quest.integration.GenliteCounterTracker
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GenliteCounterTrackerTest {
    @Test fun liveBindingsCanDecreaseToZeroWithoutRollbackWhileEventTotalsCannot() {
        val tracker=GenliteCounterTracker();val key="island|genlite.connected"
        for(n in listOf(2L,1L,0L,3L)) assertEquals(GenliteCounterTracker.Result.CHANGED,tracker.accept(key,n))
        assertEquals(GenliteCounterTracker.Result.UNCHANGED,tracker.accept(key,3))
        val event="island|genlite.energy"
        assertEquals(GenliteCounterTracker.Result.CHANGED,tracker.accept(event,8))
        assertEquals(GenliteCounterTracker.Result.ROLLBACK,tracker.accept(event,7));assertEquals(8L,tracker.previous(event))
        tracker.remove(event);assertEquals(GenliteCounterTracker.Result.CHANGED,tracker.accept(event,7))
        tracker.clear();assertNull(tracker.previous(key))
    }
}
