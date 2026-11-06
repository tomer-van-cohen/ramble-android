package io.github.tomervancohen.rambleflow

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in, read-only probe: never clears app data, opens consent, or prints tokens. */
class DriveAuthorizationDiagnosticTest {
    @Test fun inspectAuthorization() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rambleDriveDiagnostic") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = try {
            val result = Tasks.await(
                Identity.getAuthorizationClient(context).authorize(DriveExport(context, Settings(context)).authRequest()),
                30, TimeUnit.SECONDS,
            )
            "resolution=${result.hasResolution()} tokenPresent=${!result.accessToken.isNullOrBlank()}"
        } catch (error: Exception) {
            val api = generateSequence<Throwable>(error) { it.cause }.filterIsInstance<ApiException>().firstOrNull()
            "status=${api?.statusCode} servicesUpdateRequired=${DriveAuthErrors.needsServicesUpdate(api?.statusCode, api?.status?.statusMessage)}"
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("driveDiagnostic", report) })
    }
}
