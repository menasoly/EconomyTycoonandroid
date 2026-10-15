package app.many.economytycoon.forecast

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.CompanyId

/** Everything a trial run needs, held apart from the live game so it can be worked on from another thread. */
class OutlookInput(val saved: ByteArray, val content: ContentRepository, val company: CompanyId, val startDay: Long)
