package app.many.economytycoon.ui

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.model.DemandResponseForm
import app.many.economytycoon.unions.ResponseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DemandResponseFormTest {
    private val me = CompanyId(1)

    @Test fun acceptingNeedsNoNumber() {
        val cmd = DemandResponseForm(me, 7, ResponseKind.ACCEPT).validate().commandOrNull as Command.RespondDemand
        assertEquals(7L, cmd.demand); assertEquals(ResponseKind.ACCEPT, cmd.response); assertEquals(0L, cmd.counterPpm)
    }

    @Test fun refusingNeedsNoNumberEither() {
        assertNotNull((DemandResponseForm(me, 7, ResponseKind.REFUSE, "junk").validate().commandOrNull as Command.RespondDemand))
    }

    @Test fun aCounterIsReadAsAPercentage() {
        val cmd = DemandResponseForm(me, 7, ResponseKind.COUNTER, "4", 60_000L).validate().commandOrNull as Command.RespondDemand
        assertEquals(40_000L, cmd.counterPpm)
    }

    @Test fun aCounterMustBeANumberAbovezero() {
        assertNotNull(DemandResponseForm(me, 7, ResponseKind.COUNTER, "").validate().errorFor("counter"))
        assertNotNull(DemandResponseForm(me, 7, ResponseKind.COUNTER, "abc").validate().errorFor("counter"))
        assertNotNull(DemandResponseForm(me, 7, ResponseKind.COUNTER, "0").validate().errorFor("counter"))
    }

    @Test fun aCounterMustBeBelowTheDemand() {
        assertNotNull(DemandResponseForm(me, 7, ResponseKind.COUNTER, "6", 60_000L).validate().errorFor("counter"))
        assertNotNull(DemandResponseForm(me, 7, ResponseKind.COUNTER, "9", 60_000L).validate().errorFor("counter"))
        assertNull(DemandResponseForm(me, 7, ResponseKind.COUNTER, "5.9", 60_000L).validate().errorFor("counter"))
    }
}
