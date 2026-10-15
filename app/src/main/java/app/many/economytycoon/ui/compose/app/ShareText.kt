package app.many.economytycoon.ui.compose.app

import android.content.Context
import android.content.Intent

/** Hands text to whichever app the person picks: mail, a notes app, a spreadsheet that accepts pasted text. */
object ShareText {
    fun send(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, text)
        val chooser = Intent.createChooser(intent, subject)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
