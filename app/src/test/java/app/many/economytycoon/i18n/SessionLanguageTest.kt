package app.many.economytycoon.i18n

import app.many.economytycoon.session.CurrencyStyle
import app.many.economytycoon.session.GameSettings
import app.many.economytycoon.ui.present.PresentSupport
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionLanguageTest {
    @Test fun sessionTranslatesInTheChosenLanguage() {
        val s = PresentSupport.session("Sprache", 3L)
        assertEquals("Cancel", s.translator.t("action.cancel"))
        s.applySettings(GameSettings(language = GameLocale.DE))
        assertEquals("Abbrechen", s.translator.t("action.cancel"))
        s.applySettings(GameSettings(language = GameLocale.FR))
        assertEquals("Annuler", s.translator.t("action.cancel"))
    }

    @Test fun sessionMoneyFollowsTheLanguage() {
        val s = PresentSupport.session("Geld", 3L)
        s.applySettings(GameSettings(currency = CurrencyStyle.EURO, compactNumbers = false, language = GameLocale.ES))
        assertEquals("1.000,00 €", s.settings.money(app.many.economytycoon.core.Money.units(1_000)))
    }
}
