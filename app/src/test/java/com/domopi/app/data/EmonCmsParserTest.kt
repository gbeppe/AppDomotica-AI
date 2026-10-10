package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmonCmsParserTest {
    private fun ok(body: String) = EmonCmsParser.parseFeed(200, body)

    @Test fun nullSamplesStayGapsInsteadOfZero() {
        val result = ok("[[1760000000000,1500.5],[1760000300000,null],[1760000600000,0]]")
        assertEquals(EnergyResult.Success(listOf(
            FeedSample(1760000000000, 1500.5f), FeedSample(1760000300000, null), FeedSample(1760000600000, 0f))), result)
    }

    @Test fun errorsAreClassifiedNotEmptied() {
        assertEquals(EnergyResult.Failure(EnergyFetchError.Http(401)), EmonCmsParser.parseFeed(401, ""))
        assertEquals(EnergyResult.Failure(EnergyFetchError.Http(404)), EmonCmsParser.parseFeed(404, "<html>Not Found</html>"))
        assertEquals(EnergyResult.Failure(EnergyFetchError.Rejected("Username or password empty")),
            ok("""{"success":false,"message":"Username or password empty"}"""))
        assertEquals(EnergyResult.Failure(EnergyFetchError.InvalidResponse), ok("<html>login</html>"))
        assertEquals(EnergyResult.Failure(EnergyFetchError.InvalidResponse), ok("""{"success":false}"""))
        assertEquals(EnergyResult.Failure(EnergyFetchError.InvalidResponse), ok("42"))
    }

    @Test fun emptyArrayIsSuccessWithNoSamples() = assertEquals(EnergyResult.Success(emptyList<FeedSample>()), ok("[]"))

    @Test fun gridImportSkipsGapsAndExportAndSplitsAtMidnight() {
        val today = 1_760_000_000L
        val samples = listOf(
            FeedSample((today - 600) * 1000, 1200f),   // yesterday, 300 s -> 0.1 kWh
            FeedSample((today - 300) * 1000, -500f),   // export: ignored
            FeedSample(today * 1000, 600f),            // today, 300 s -> 0.05 kWh
            FeedSample((today + 300) * 1000, null),    // gap: not integrated
            FeedSample((today + 3600) * 1000, 6000f),  // next sample 1 h later: gap, ignored
            FeedSample((today + 7200) * 1000, 0f),
        )
        val (ieri, oggi) = EmonCmsParser.gridImportKwh(samples, today, 300)
        assertEquals(0.1f, ieri, 1e-6f)
        assertEquals(0.05f, oggi, 1e-6f)
    }

    @Test fun everyErrorHasAnItalianExplanation() {
        listOf(EnergyFetchError.NotConfigured, EnergyFetchError.Unreachable, EnergyFetchError.Http(500),
            EnergyFetchError.Rejected("x"), EnergyFetchError.InvalidResponse, EnergyFetchError.NoData)
            .forEach { assertTrue(describeEnergyError(it).isNotBlank()) }
        assertTrue(describeEnergyError(EnergyFetchError.Unreachable).contains("VPN"))
    }
}
