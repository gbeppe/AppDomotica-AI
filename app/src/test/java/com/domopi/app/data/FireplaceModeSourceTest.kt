package com.domopi.app.data

import com.domopi.app.ui.screens.isFireplaceManual
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fireplace mode menu = labels the router translates for .15; manual on/off only in "Manuale". */
class FireplaceModeSourceTest {
    @Test fun menuMatchesTheRouterTranslation() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val hvac = File(root, "com/domopi/app/ui/screens/HvacScreen.kt").readText()
        assertTrue(hvac.contains("val modes = listOf(\"Disattivato\", \"Riscaldamento\", \"Integrazione Caldaia\", " +
            "\"Acqua Sanitaria\", FIREPLACE_MANUAL_MODE)"))
        assertTrue(hvac.contains("enabled = isFireplaceManual(state.mode)"))
    }

    @Test fun powerSwitchOnlyInManualMode() {
        assertTrue(isFireplaceManual("Manuale"))
        assertTrue(isFireplaceManual("manuale"))   // raw .15 value, before the router translation
        for (mode in listOf("Riscaldamento", "Acqua Sanitaria", "Integrazione Caldaia", "Disattivato", "", "inverno"))
            assertFalse(mode, isFireplaceManual(mode))
    }
}
