package io.github.tomervancohen.rambleflow

import org.junit.Assert.*
import org.junit.Test

class DriveAuthErrorsTest {
    @Test fun oldServicesGateIsNotAnOAuthRegistrationError() {
        assertTrue(DriveAuthErrors.needsServicesUpdate(10, "Caller not whitelisted to call this API."))
        assertTrue(DriveAuthErrors.needsServicesUpdate(10, "caller not whitelisted to call this api"))
    }

    @Test fun otherDeveloperFailuresDoNotRecommendAnUpdate() {
        assertFalse(DriveAuthErrors.needsServicesUpdate(10, "OAuth client is not registered"))
        assertFalse(DriveAuthErrors.needsServicesUpdate(10, null))
        assertFalse(DriveAuthErrors.needsServicesUpdate(7, "Caller not whitelisted to call this API."))
    }
}
