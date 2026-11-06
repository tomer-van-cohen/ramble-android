package io.github.tomervancohen.rambleflow

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Test
import org.junit.Assert.*

class LanguageDefaultsTest {
    @Test fun interfaceLanguageProvidesDefaultButExplicitAutoDetectIsPreserved() {
        val app=ApplicationProvider.getApplicationContext<RambleApp>()
        val context=object:ContextWrapper(app) {
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=super.getSharedPreferences("language-default-fixture",mode)
        }
        val prefs=context.getSharedPreferences("ramble",Context.MODE_PRIVATE)
        val old=Locale.getDefault()
        prefs.edit().clear().commit()
        try {
            Locale.setDefault(Locale.forLanguageTag("he"))
            assertEquals("he",Settings(context).transcriptionLanguage)
            assertEquals("מפתח API",appText(R.string.api_key_label))
            Settings(context).transcriptionLanguage=""
            assertEquals("",Settings(context).transcriptionLanguage)
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("",Settings(context).transcriptionLanguage)
            prefs.edit().remove("transcriptionLanguage").commit()
            assertEquals("en",Settings(context).transcriptionLanguage)
            assertEquals("Your Groq API key",appText(R.string.provider_key_title,"Groq"))
        } finally { prefs.edit().clear().commit();Locale.setDefault(old) }
    }
}
