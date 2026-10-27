package com.domopi.app.data

import com.domopi.app.ui.screens.installedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class GatewayFirmwareTest {
    @Test fun versionCodeBecomesText() {
        assertEquals("26.34.0", EmonCmsParser.firmwareText(263400.0))
        assertEquals("24.12.10", EmonCmsParser.firmwareText(241210.0))
        assertNull(EmonCmsParser.firmwareText(null))
        assertNull(EmonCmsParser.firmwareText(0.0))
    }

    @Test fun fetchAnswerOfTodayAndMissingFeeds() {
        // 27/10/2026: version, install date (last boot 05/10 08:36), not exact
        val fw = EmonCmsParser.gatewayFirmware(EmonCmsParser.parseFetch(200, "[263400,1791182201,0]"))!!
        assertEquals(GatewayFirmware("26.34.0", 1791182201L, false), fw)
        assertEquals(GatewayFirmware("26.34.0", null, false), EmonCmsParser.gatewayFirmware(EmonCmsParser.parseFetch(200, "[263400,null,null]")))
        assertNull(EmonCmsParser.gatewayFirmware(EmonCmsParser.parseFetch(200, "[null,null,null]")))
        assertNull(EmonCmsParser.parseFetch(401, "[263400]"))
        assertNull(EmonCmsParser.parseFetch(200, "{\"success\":false,\"message\":\"Invalid key\"}"))
    }

    @Test fun installedTextSaysWhenTheDateIsOnlyTheLastBoot() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
        try {
            assertEquals("dal 05/10/2026 (ultimo riavvio)", installedText(GatewayFirmware("26.34.0", 1791182201L, false)))
            assertEquals("il 05/10/2026", installedText(GatewayFirmware("26.34.0", 1791182201L, true)))
            assertEquals("—", installedText(GatewayFirmware("26.34.0", null, false)))
            assertEquals("—", installedText(null))
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
