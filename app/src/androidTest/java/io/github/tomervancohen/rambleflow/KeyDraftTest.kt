package io.github.tomervancohen.rambleflow

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

/** Isolated preferences: safe on a lab device, never touches its real provider credentials. */
class KeyDraftTest {
    @Test fun pendingKeySurvivesSettingsRecreationWithoutActivatingIt() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val testContext = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("ramble-key-draft-test", mode)
        }
        val prefs = testContext.getSharedPreferences("ramble", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val draft = "synthetic-key-never-sent-to-a-provider"
            Settings(testContext).saveKeyDraft("groq", draft)
            val restored = Settings(testContext)
            assertEquals(draft, restored.readKeyDraft("groq"))
            assertEquals("", restored.readKeyDraft("openai"))
            assertFalse(restored.hasKey("groq"))
            assertFalse(prefs.getString("draftKey.groq", "")!!.contains(draft))
            restored.saveKey("groq", draft)
            val activated = Settings(testContext)
            assertTrue(activated.hasKey("groq"))
            assertEquals(draft, activated.readKey(activated.profile("groq")))
            assertEquals("", activated.readKeyDraft("groq"))
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
