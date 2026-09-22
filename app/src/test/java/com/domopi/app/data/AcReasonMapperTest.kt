package com.domopi.app.data

import org.junit.Assert.*
import org.junit.Test

class AcReasonMapperTest {

    @Test
    fun testCase1_OffWithBloccoTimerAnticicloOff_12MinRemaining() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = "MIN_OFF",
                    timerResiduoMinuti = 12,
                    timerDurataMinuti = 20
                )
            )
        )
        val info = AcReasonMapper.getAcReasonInfo("BLOCCO_TIMER_ANTICICLO_OFF", aiData)

        assertEquals("Riavvio temporaneamente bloccato", info.userTitle)
        assertEquals(12, info.remainingTimerMinutes)
    }

    @Test
    fun testCase2_OffWithComfortNotturnoRaggiunto_NoFakeTimerBlockWhenZero() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = "MIN_OFF",
                    timerResiduoMinuti = 0
                )
            )
        )
        val info = AcReasonMapper.getAcReasonInfo("COMFORT_NOTTURNO_RAGGIUNTO", aiData)

        assertEquals("Comfort notturno raggiunto", info.userTitle)
        assertNull(info.remainingTimerMinutes)
        assertFalse(info.userExplanation.contains("0 min"))
    }

    @Test
    fun testCase3_CoolingOnWithTolleranzaDeficitInCorso_17MinRemaining() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                diagnosticaAc = DiagnosticaAc(
                    deficitResiduoMinuti = 17,
                    deficitTolleranzaMinuti = 30
                )
            )
        )
        val info = AcReasonMapper.getAcReasonInfo("TOLLERANZA_DEFICIT_IN_CORSO", aiData)

        assertEquals("Deficit energetico temporaneamente tollerato", info.userTitle)
        assertEquals("Deficit energetico in corso ma ancora entro il tempo di tolleranza.", info.userExplanation)
        assertEquals(17, info.remainingTimerMinutes)
    }

    @Test
    fun testCase4_OffWithBloccoNottePausa45m_23MinRemaining() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                diagnosticaAc = DiagnosticaAc(
                    pausaNotturnaResiduoMinuti = 23
                )
            )
        )
        val info = AcReasonMapper.getAcReasonInfo("BLOCCO_NOTTE_PAUSA_45M", aiData)

        assertEquals("Pausa notturna in corso", info.userTitle)
        assertEquals(23, info.remainingTimerMinutes)
    }

    @Test
    fun testCase5_EsecuzioneSpegnimentoWithCausaTransizione_UsesTransitionCause() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                diagnosticaAc = DiagnosticaAc(
                    causaTransizione = "SPEGNIMENTO_COMFORT_TEMP_BASSA"
                )
            )
        )
        val info = AcReasonMapper.getAcReasonInfo("ESECUZIONE_SPEGNIMENTO", aiData)

        assertEquals("Condizionatore spento", info.userTitle)
        assertEquals("Comfort raggiunto", info.userExplanation)
    }

    @Test
    fun testCase6_EmptyOrNullCausaTransizione_DoesNotRetainStaleCause() {
        val aiDataEmpty = AiManagedData(
            logicaControllo = LogicaControllo(
                diagnosticaAc = DiagnosticaAc(
                    causaTransizione = null
                )
            )
        )
        val info = AcReasonMapper.getAcReasonInfo("ESECUZIONE_SPEGNIMENTO", aiDataEmpty)

        assertEquals("Condizionatore spento", info.userTitle)
        assertEquals("Comando di spegnimento eseguito", info.userExplanation)
    }

    @Test
    fun testCase7_UnknownMotivoLogica_SafeFallbackNoCrash() {
        val info = AcReasonMapper.getAcReasonInfo("NUOVO_CODICE_FUTURO_NON_MAPPATO")

        assertNotNull(info)
        assertEquals("Nuovo codice futuro non mappato", info.userTitle)
        assertTrue(info.userExplanation.contains("NUOVO_CODICE_FUTURO_NON_MAPPATO"))
        assertEquals(AcReasonCategory.UNKNOWN, info.category)
    }

    @Test
    fun testCase8_UnreceivedDiagnosticTopics_StableUiAndNoInventedFakeZeros() {
        val aiDataUnreceived = AiManagedData(
            logicaControllo = LogicaControllo(
                diagnosticaAc = DiagnosticaAc() // all fields null
            )
        )
        val info = AcReasonMapper.getAcReasonInfo("COMFORT_NOTTURNO_RAGGIUNTO", aiDataUnreceived)

        assertNotNull(info)
        assertNull(info.remainingTimerMinutes)
        assertFalse(info.userExplanation.contains("0"))
    }

    @Test
    fun testWp21_ComfortNotturnoRaggiuntoUsesHumidexBedroomNotLiving() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 32.5f,
                humidexBedroom = 27.8f
            ),
            logicaControllo = LogicaControllo(
                sogliaAttivazioneApplicata = 30.0f
            )
        )
        val info = AcReasonMapper.getAcReasonInfo("COMFORT_NOTTURNO_RAGGIUNTO", aiData)

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex camera" }
        assertNotNull(humidexMetric)
        assertTrue(humidexMetric!!.value.contains("27,8") || humidexMetric.value.contains("27.8"))
        assertFalse(info.metrics.any { it.label == "Humidex zona giorno" && it.value.contains("32") })
    }

    @Test
    fun testWp21_NullDeficitTimerNotTransformedToZero() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                diagnosticaAc = DiagnosticaAc(
                    deficitResiduoMinuti = null
                )
            )
        )
        val info = AcReasonMapper.getAcReasonInfo("TOLLERANZA_DEFICIT_IN_CORSO", aiData)

        assertNull(info.remainingTimerMinutes)
        assertFalse(info.userExplanation.contains("0 min"))
    }

    @Test
    fun testWp21_NoNightDryThresholdOffsetCalculatedInAndroid() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                nightDryHumidexThreshold = 30.0f
            )
        )
        val info = AcReasonMapper.getAcReasonInfo("COMFORT_NOTTURNO_RAGGIUNTO", aiData)

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Soglia NIGHT_DRY" }
        assertNotNull(thresholdMetric)
        assertTrue(thresholdMetric!!.value.contains("30,0") || thresholdMetric.value.contains("30.0"))
        // Verify NO +0.5 (30.5) is calculated locally in Android
        assertFalse(thresholdMetric.value.contains("30,5") || thresholdMetric.value.contains("30.5"))
    }

    @Test
    fun testWp32_ComfortNotturnoDoesNotExposeComparativeDecisionThreshold() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 30.2f,
                humidexBedroom = 28.0f
            ),
            logicaControllo = LogicaControllo(
                nightDryHumidexThreshold = 30.0f
            ),
            metricheElettriche = MetricheElettriche(
                powerwallSocPercent = 68.0f
            )
        )
        val info = AcReasonMapper.getAcReasonInfo("COMFORT_NOTTURNO_RAGGIUNTO", aiData)

        assertEquals("Comfort notturno raggiunto", info.userTitle)
        assertEquals("Humidex camera dentro la zona di comfort notturna.", info.userExplanation)

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex camera" }
        assertNotNull(humidexMetric)
        assertTrue(humidexMetric!!.value.contains("28,0") || humidexMetric.value.contains("28.0"))

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Soglia NIGHT_DRY" }
        assertNotNull(thresholdMetric)
        assertTrue(thresholdMetric!!.value.contains("30,0") || thresholdMetric.value.contains("30.0"))

        assertTrue(info.soglieCostanti.isEmpty())
        assertFalse(info.soglieCostanti.contains("<"))

        assertFalse(info.soglieCostanti.contains("30.5") || info.soglieCostanti.contains("30,5"))
        assertFalse(info.metrics.any { it.value.contains("30.5") || it.value.contains("30,5") })
    }

    @Test
    fun testWp33A_ComfortTempBassaAttesaTimer_ProvidesCorrectCausalEvidence() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                temperaturaC = 24.3f
            ),
            logicaControllo = LogicaControllo(
                sogliaTemperaturaOffComfortC = 24.5f,
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = "MIN_RUN",
                    timerResiduoMinuti = 19,
                    timerDurataMinuti = 20
                )
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("COMFORT_TEMP_BASSA_ATTESA_TIMER", aiData)

        assertEquals("Comfort per temperatura - attesa timer", info.userTitle)
        assertTrue(info.userExplanation.contains("Temperatura interna sotto la soglia di comfort"))
        assertTrue(info.userExplanation.contains("tempo minimo di funzionamento"))

        assertEquals(19, info.remainingTimerMinutes)

        val tempMetric = info.metrics.firstOrNull { it.label == "Temperatura Int." }
        assertNotNull(tempMetric)
        assertTrue(tempMetric!!.value.contains("24,3") || tempMetric.value.contains("24.3"))

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Soglia Comfort" }
        assertNotNull(thresholdMetric)
        assertTrue(thresholdMetric!!.value.contains("24,5") || thresholdMetric.value.contains("24.5"))

        assertFalse(info.metrics.any { it.label == "Humidex Int." })
        assertFalse(info.metrics.any { it.label == "Humidex Camera" })
        assertFalse(info.metrics.any { it.label == "Soglia Humidex" })
        assertFalse(info.metrics.any { it.label == "Soglia Configurata" })
        assertFalse(info.metrics.any { it.label == "Surplus Solare" })
    }

    @Test
    fun testWp33A_SpegnimentoComfortTempBassa_ProvidesCorrectCausalEvidence() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                temperaturaC = 24.3f
            ),
            logicaControllo = LogicaControllo(
                sogliaTemperaturaOffComfortC = 24.5f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("SPEGNIMENTO_COMFORT_TEMP_BASSA", aiData)

        assertEquals("Spegnimento per comfort temperatura", info.userTitle)
        assertEquals("Temperatura interna scesa sotto la soglia di comfort.", info.userExplanation)

        val tempMetric = info.metrics.firstOrNull { it.label == "Temperatura Int." }
        assertNotNull(tempMetric)
        assertTrue(tempMetric!!.value.contains("24,3") || tempMetric.value.contains("24.3"))

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Soglia Comfort" }
        assertNotNull(thresholdMetric)
        assertTrue(thresholdMetric!!.value.contains("24,5") || thresholdMetric.value.contains("24.5"))

        assertFalse(info.metrics.any { it.label == "Humidex Int." })
        assertFalse(info.metrics.any { it.label == "Humidex Camera" })
        assertFalse(info.metrics.any { it.label == "Soglia Humidex" })
        assertFalse(info.metrics.any { it.label == "Soglia Configurata" })
        assertFalse(info.metrics.any { it.label == "Surplus Solare" })
    }

    @Test
    fun testWp33A_NullSafety_NoFakeThresholds() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                temperaturaC = 24.3f
            ),
            logicaControllo = LogicaControllo(
                sogliaTemperaturaOffComfortC = null
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("SPEGNIMENTO_COMFORT_TEMP_BASSA", aiData)

        val tempMetric = info.metrics.firstOrNull { it.label == "Temperatura Int." }
        assertNotNull(tempMetric)
        assertTrue(tempMetric!!.value.contains("24,3") || tempMetric.value.contains("24.3"))

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Soglia Comfort" }
        assertNull(thresholdMetric)
    }

    @Test
    fun testWp33B_AttesaSogliaHumidex_ProvidesCorrectCausalEvidence() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 30.6f
            ),
            logicaControllo = LogicaControllo(
                sogliaAttivazioneApplicata = 34.0f
            ),
            metricheElettriche = MetricheElettriche(
                surplusW = 17f,
                powerwallSocPercent = 65f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("ATTESA_SOGLIA_HUMIDEX", aiData)

        assertEquals("In attesa soglia Humidex", info.userTitle)

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex zona giorno" }
        assertNotNull(humidexMetric)
        assertTrue(humidexMetric!!.value.contains("30,6") || humidexMetric.value.contains("30.6"))

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Humidex di attivazione" }
        assertNotNull(thresholdMetric)
        assertTrue(thresholdMetric!!.value.contains("34,0") || thresholdMetric.value.contains("34.0"))

        assertFalse(info.metrics.any { it.label == "Surplus Solare" })
        assertFalse(info.metrics.any { it.label == "SOC Batteria" })
        assertFalse(info.metrics.any { it.label == "Potenza Batteria" })
        assertFalse(info.metrics.any { it.label == "Temperatura Int." })
        assertFalse(info.metrics.any { it.label == "Humidex comfort" })
    }

    @Test
    fun testWp33B_ComfortDiurnoRaggiunto_ProvidesCorrectCausalEvidence() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 30.6f
            ),
            logicaControllo = LogicaControllo(
                sogliaAttivazioneApplicata = 34.0f
            ),
            metricheElettriche = MetricheElettriche(
                surplusW = 17f,
                powerwallSocPercent = 65f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("COMFORT_DIURNO_RAGGIUNTO", aiData)

        assertEquals("Comfort diurno raggiunto", info.userTitle)

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex zona giorno" }
        assertNotNull(humidexMetric)
        assertTrue(humidexMetric!!.value.contains("30,6") || humidexMetric.value.contains("30.6"))

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Humidex di attivazione" }
        assertNotNull(thresholdMetric)
        assertTrue(thresholdMetric!!.value.contains("34,0") || thresholdMetric.value.contains("34.0"))

        assertFalse(info.metrics.any { it.label == "Surplus Solare" })
        assertFalse(info.metrics.any { it.label == "SOC Batteria" })
        assertFalse(info.metrics.any { it.label == "Potenza Batteria" })
        assertFalse(info.metrics.any { it.label == "Temperatura Int." })
        assertFalse(info.metrics.any { it.label == "Humidex comfort" })
    }

    @Test
    fun testWp33C_BloccoDiurnoBatteria_ProvidesCorrectCausalEvidence() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 31.0f,
                humidexBedroom = 28.0f
            ),
            logicaControllo = LogicaControllo(
                socMinimoApplied = 35f
            ),
            metricheElettriche = MetricheElettriche(
                powerwallSocPercent = 30f,
                batteryPowerW = 1234f,
                surplusW = 500f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("BLOCCO_DIURNO_BATTERIA", aiData)

        val socMetric = info.metrics.firstOrNull { it.label == "SOC Batteria" }
        assertNotNull(socMetric)
        assertTrue(socMetric!!.value.contains("30%"))

        val minSocMetric = info.metrics.firstOrNull { it.label == "SOC Minimo" }
        assertNotNull(minSocMetric)
        assertTrue(minSocMetric!!.value.contains("35%"))

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex zona giorno" }
        assertNotNull(humidexMetric)
        assertTrue(humidexMetric!!.value.contains("31,0") || humidexMetric.value.contains("31.0"))

        assertFalse(info.metrics.any { it.label == "Potenza Batteria" })
        assertFalse(info.metrics.any { it.label == "Surplus Solare" })
        assertFalse(info.metrics.any { it.label == "Humidex camera" })
        assertFalse(info.metrics.any { it.label == "Humidex di attivazione" })
        assertFalse(info.metrics.any { it.label == "Humidex impostato" })
        assertFalse(info.metrics.any { it.label == "Temperatura Int." })
        assertFalse(info.metrics.any { it.label == "Humidex comfort" })
    }

    @Test
    fun testWp33C_BloccoNotteBatteriaBassa_ProvidesCorrectCausalEvidence() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 31.0f,
                humidexBedroom = 28.0f
            ),
            logicaControllo = LogicaControllo(
                socMinimoApplied = 35f
            ),
            metricheElettriche = MetricheElettriche(
                powerwallSocPercent = 30f,
                batteryPowerW = 1234f,
                surplusW = 500f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("BLOCCO_NOTTE_BATTERIA_BASSA", aiData)

        val socMetric = info.metrics.firstOrNull { it.label == "SOC Batteria" }
        assertNotNull(socMetric)
        assertTrue(socMetric!!.value.contains("30%"))

        val minSocMetric = info.metrics.firstOrNull { it.label == "SOC Minimo" }
        assertNotNull(minSocMetric)
        assertTrue(minSocMetric!!.value.contains("35%"))

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex camera" }
        assertNotNull(humidexMetric)
        assertTrue(humidexMetric!!.value.contains("28,0") || humidexMetric.value.contains("28.0"))
        // Protects against regression: ensure we are not showing the living humidex
        assertFalse(humidexMetric.value.contains("31,0") || humidexMetric.value.contains("31.0"))

        assertFalse(info.metrics.any { it.label == "Potenza Batteria" })
        assertFalse(info.metrics.any { it.label == "Surplus Solare" })
        assertFalse(info.metrics.any { it.label == "Humidex zona giorno" })
        assertFalse(info.metrics.any { it.label == "Humidex di attivazione" })
        assertFalse(info.metrics.any { it.label == "Humidex impostato" })
        assertFalse(info.metrics.any { it.label == "Temperatura Int." })
        assertFalse(info.metrics.any { it.label == "Humidex comfort" })
    }

    @Test
    fun testWp33D_BloccoTimerAnticicloOff_ProvidesCorrectCausalEvidence() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 31.0f,
                humidexBedroom = 28.0f,
                temperaturaC = 25f
            ),
            logicaControllo = LogicaControllo(
                socMinimoApplied = 35f,
                sogliaAttivazioneApplicata = 30f,
                sogliaTemperaturaOffComfortC = 24.5f,
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = "MIN_OFF",
                    timerResiduoMinuti = 12,
                    timerDurataMinuti = 20
                )
            ),
            metricheElettriche = MetricheElettriche(
                powerwallSocPercent = 70f,
                batteryPowerW = 800f,
                surplusW = 1500f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("BLOCCO_TIMER_ANTICICLO_OFF", aiData)

        assertEquals(12, info.remainingTimerMinutes)

        val timerMetric = info.metrics.firstOrNull { it.label == "Timer" }
        assertNotNull(timerMetric)
        assertEquals("MIN_OFF", timerMetric!!.value)

        val durationMetric = info.metrics.firstOrNull { it.label == "Durata minima" }
        assertNotNull(durationMetric)
        assertEquals("20 min", durationMetric!!.value)

        assertFalse(info.metrics.any { it.label == "Humidex zona giorno" })
        assertFalse(info.metrics.any { it.label == "Humidex camera" })
        assertFalse(info.metrics.any { it.label == "Humidex di attivazione" })
        assertFalse(info.metrics.any { it.label == "Humidex impostato" })
        assertFalse(info.metrics.any { it.label == "SOC Batteria" })
        assertFalse(info.metrics.any { it.label == "SOC Minimo" })
        assertFalse(info.metrics.any { it.label == "Potenza Batteria" })
        assertFalse(info.metrics.any { it.label == "Surplus Solare" })
        assertFalse(info.metrics.any { it.label == "Temperatura Int." })
        assertFalse(info.metrics.any { it.label == "Humidex comfort" })
        assertFalse(info.metrics.any { it.label == "Tempo residuo" })

        assertTrue(info.soglieCostanti.isBlank())
    }

    @Test
    fun testWp33D_BloccoTimerAnticicloOff_NullDiagnosticsDoesNotInventTimer() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                tempoMancanteAnticicloMinuti = 17,
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = null,
                    timerResiduoMinuti = null,
                    timerDurataMinuti = null
                )
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("BLOCCO_TIMER_ANTICICLO_OFF", aiData)

        assertNull(info.remainingTimerMinutes)
        assertFalse(info.metrics.any { it.label == "Durata minima" })
        assertFalse(info.userExplanation.contains("17 min"))
        assertTrue(info.soglieCostanti.isBlank())
    }

    @Test
    fun testWp33D_BloccoTimerAnticicloOff_ZeroResidualIsNotShownAsActiveTimer() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = "MIN_OFF",
                    timerResiduoMinuti = 0,
                    timerDurataMinuti = 20
                )
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("BLOCCO_TIMER_ANTICICLO_OFF", aiData)

        assertNull(info.remainingTimerMinutes)

        val timerMetric = info.metrics.firstOrNull { it.label == "Timer" }
        assertNotNull(timerMetric)
        assertEquals("MIN_OFF", timerMetric!!.value)

        val durationMetric = info.metrics.firstOrNull { it.label == "Durata minima" }
        assertNotNull(durationMetric)
        assertEquals("20 min", durationMetric!!.value)

        assertFalse(info.userExplanation.contains("0 min"))
    }

    @Test
    fun testWp33E_ComfortRaggiuntoAttesaTimer_ProvidesCorrectCausalEvidence() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 27.0f,
                humidexBedroom = 30.5f,
                temperaturaC = 25f
            ),
            logicaControllo = LogicaControllo(
                socMinimoApplied = 35f,
                sogliaAttivazioneApplicata = 30f,
                sogliaSpegnimentoComfort = 27.5f,
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = "MIN_RUN",
                    timerResiduoMinuti = 8,
                    timerDurataMinuti = 20
                )
            ),
            metricheElettriche = MetricheElettriche(
                powerwallSocPercent = 70f,
                batteryPowerW = 800f,
                surplusW = 1500f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("COMFORT_RAGGIUNTO_ATTESA_TIMER", aiData)

        assertEquals("Comfort raggiunto", info.userTitle)
        assertFalse(info.userExplanation.contains("8 min"))
        assertEquals(8, info.remainingTimerMinutes)

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex zona giorno" }
        assertNotNull(humidexMetric)
        assertTrue(humidexMetric!!.value.contains("27,0") || humidexMetric.value.contains("27.0"))

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Humidex comfort" }
        assertNotNull(thresholdMetric)
        assertTrue(thresholdMetric!!.value.contains("27,5") || thresholdMetric.value.contains("27.5"))

        val timerMetric = info.metrics.firstOrNull { it.label == "Timer" }
        assertNotNull(timerMetric)
        assertEquals("MIN_RUN", timerMetric!!.value)

        val durationMetric = info.metrics.firstOrNull { it.label == "Durata minima" }
        assertNotNull(durationMetric)
        assertEquals("20 min", durationMetric!!.value)

        assertFalse(info.metrics.any { it.label == "Humidex camera" })
        assertFalse(info.metrics.any { it.label == "Humidex di attivazione" })
        assertFalse(info.metrics.any { it.label == "Humidex impostato" })
        assertFalse(info.metrics.any { it.label == "SOC Batteria" })
        assertFalse(info.metrics.any { it.label == "SOC Minimo" })
        assertFalse(info.metrics.any { it.label == "Potenza Batteria" })
        assertFalse(info.metrics.any { it.label == "Surplus Solare" })
        assertFalse(info.metrics.any { it.label == "Temperatura Int." })
        assertFalse(info.metrics.any { it.label == "Tempo residuo" })

        assertTrue(info.soglieCostanti.isBlank())
    }

    @Test
    fun testWp33E_ComfortRaggiuntoAttesaTimer_NullTimerDiagnosticsDoesNotInventTimer() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                tempoMancanteAnticicloMinuti = 17,
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = null,
                    timerResiduoMinuti = null,
                    timerDurataMinuti = null
                )
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("COMFORT_RAGGIUNTO_ATTESA_TIMER", aiData)

        assertNull(info.remainingTimerMinutes)
        assertFalse(info.metrics.any { it.label == "Durata minima" })
        assertFalse(info.metrics.any { it.label == "Timer" })
        assertFalse(info.userExplanation.contains("17 min"))
        assertTrue(info.soglieCostanti.isBlank())
    }

    @Test
    fun testWp33E_ComfortRaggiuntoAttesaTimer_ZeroResidualIsNotActiveTimer() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = "MIN_RUN",
                    timerResiduoMinuti = 0,
                    timerDurataMinuti = 20
                )
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("COMFORT_RAGGIUNTO_ATTESA_TIMER", aiData)

        assertNull(info.remainingTimerMinutes)

        val timerMetric = info.metrics.firstOrNull { it.label == "Timer" }
        assertNotNull(timerMetric)
        assertEquals("MIN_RUN", timerMetric!!.value)

        val durationMetric = info.metrics.firstOrNull { it.label == "Durata minima" }
        assertNotNull(durationMetric)
        assertFalse(info.userExplanation.contains("0 min"))
    }

    @Test
    fun testWp33F_BatteriaScaricaAttesaTimer_ProvidesCorrectCausalEvidence() {
        val aiData = AiManagedData(
            metricheElettriche = MetricheElettriche(
                powerwallSocPercent = 30f,
                batteryPowerW = 1234f,
                surplusW = 500f
            ),
            logicaControllo = LogicaControllo(
                socMinimoApplied = 35f,
                sogliaAttivazioneApplicata = 30f,
                sogliaSpegnimentoComfort = 27.5f,
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = "MIN_RUN",
                    timerResiduoMinuti = 8,
                    timerDurataMinuti = 20
                )
            ),
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 31.0f,
                humidexBedroom = 28.0f,
                temperaturaC = 25f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("BATTERIA_SCARICA_ATTESA_TIMER", aiData)

        assertEquals("Batteria scarica - in attesa timer", info.userTitle)
        assertEquals(8, info.remainingTimerMinutes)

        val socMetric = info.metrics.firstOrNull { it.label == "SOC Batteria" }
        assertNotNull(socMetric)
        assertTrue(socMetric!!.value.contains("30%"))

        val minSocMetric = info.metrics.firstOrNull { it.label == "SOC Minimo" }
        assertNotNull(minSocMetric)
        assertTrue(minSocMetric!!.value.contains("35%"))

        val timerMetric = info.metrics.firstOrNull { it.label == "Timer" }
        assertNotNull(timerMetric)
        assertEquals("MIN_RUN", timerMetric!!.value)

        val durationMetric = info.metrics.firstOrNull { it.label == "Durata minima" }
        assertNotNull(durationMetric)
        assertEquals("20 min", durationMetric!!.value)

        assertFalse(info.metrics.any { it.label == "Potenza Batteria" })
        assertFalse(info.metrics.any { it.label == "Surplus Solare" })
        assertFalse(info.metrics.any { it.label == "Humidex Int." })
        assertFalse(info.metrics.any { it.label == "Humidex Camera" })
        assertFalse(info.metrics.any { it.label == "Soglia Humidex" })
        assertFalse(info.metrics.any { it.label == "Soglia Configurata" })
        assertFalse(info.metrics.any { it.label == "Temperatura Int." })
        assertFalse(info.metrics.any { it.label == "Soglia Comfort" })
        assertFalse(info.metrics.any { it.label == "Tempo residuo" })

        assertFalse(info.userExplanation.contains("8 min"))
        assertTrue(info.soglieCostanti.isBlank())
    }

    @Test
    fun testWp33F_BatteriaScaricaAttesaTimer_NullDiagnosticsDoesNotInventTimer() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                tempoMancanteAnticicloMinuti = 17,
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = null,
                    timerResiduoMinuti = null,
                    timerDurataMinuti = null
                )
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("BATTERIA_SCARICA_ATTESA_TIMER", aiData)

        assertNull(info.remainingTimerMinutes)
        assertFalse(info.metrics.any { it.label == "Timer" })
        assertFalse(info.metrics.any { it.label == "Durata minima" })
        assertFalse(info.metrics.any { it.label == "Tempo residuo" })
        assertFalse(info.userExplanation.contains("17 min"))
        assertTrue(info.soglieCostanti.isBlank())
    }

    @Test
    fun testWp33F_BatteriaScaricaAttesaTimer_ZeroResidualIsNotActiveTimer() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = "MIN_RUN",
                    timerResiduoMinuti = 0,
                    timerDurataMinuti = 20
                )
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("BATTERIA_SCARICA_ATTESA_TIMER", aiData)

        assertNull(info.remainingTimerMinutes)

        val timerMetric = info.metrics.firstOrNull { it.label == "Timer" }
        assertNotNull(timerMetric)
        assertEquals("MIN_RUN", timerMetric!!.value)

        val durationMetric = info.metrics.firstOrNull { it.label == "Durata minima" }
        assertNotNull(durationMetric)
        assertEquals("20 min", durationMetric!!.value)

        assertFalse(info.userExplanation.contains("0 min"))
    }

    @Test
    fun testWp33G_BloccoNottePausa45m_ProvidesCorrectCausalEvidence() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 31.0f,
                humidexBedroom = 28.0f
            ),
            logicaControllo = LogicaControllo(
                sogliaAttivazioneApplicata = 30.0f,
                diagnosticaAc = DiagnosticaAc(
                    pausaNotturnaResiduoMinuti = 23,
                    timerTipo = "MIN_OFF",
                    timerResiduoMinuti = 12,
                    timerDurataMinuti = 20
                )
            ),
            metricheElettriche = MetricheElettriche(
                powerwallSocPercent = 70f,
                batteryPowerW = 800f,
                surplusW = 1500f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("BLOCCO_NOTTE_PAUSA_45M", aiData)

        assertEquals("Pausa notturna in corso", info.userTitle)
        assertEquals(23, info.remainingTimerMinutes)
        assertNotEquals(12, info.remainingTimerMinutes)

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex camera" }
        assertNotNull(humidexMetric)
        assertTrue(humidexMetric!!.value.contains("28,0") || humidexMetric.value.contains("28.0"))

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Humidex impostato" }
        assertNotNull(thresholdMetric)
        assertTrue(thresholdMetric!!.value.contains("30,0") || thresholdMetric.value.contains("30.0"))

        assertFalse(info.metrics.any { it.label == "Humidex zona giorno" })
        assertFalse(info.metrics.any { it.label == "Humidex di attivazione" })
        assertFalse(info.metrics.any { it.label == "SOC Batteria" })
        assertFalse(info.metrics.any { it.label == "SOC Minimo" })
        assertFalse(info.metrics.any { it.label == "Potenza Batteria" })
        assertFalse(info.metrics.any { it.label == "Surplus Solare" })
        assertFalse(info.metrics.any { it.label == "Temperatura Int." })
        assertFalse(info.metrics.any { it.label == "Humidex comfort" })
        assertFalse(info.metrics.any { it.label == "Timer" })
        assertFalse(info.metrics.any { it.label == "Durata minima" })
        assertFalse(info.metrics.any { it.label == "Tempo residuo" })

        assertFalse(info.userExplanation.contains("23 min"))
        assertFalse(info.userExplanation.contains("12 min"))
        assertTrue(info.soglieCostanti.isBlank())
    }

    @Test
    fun testWp33G_BloccoNottePausa45m_NullDiagnosticsDoesNotInventTimer() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                tempoMancanteAnticicloMinuti = 17,
                diagnosticaAc = DiagnosticaAc(
                    pausaNotturnaResiduoMinuti = null,
                    timerResiduoMinuti = 12
                )
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("BLOCCO_NOTTE_PAUSA_45M", aiData)

        assertNull(info.remainingTimerMinutes)
        assertFalse(info.metrics.any { it.label == "Timer" })
        assertFalse(info.metrics.any { it.label == "Durata minima" })
        assertFalse(info.metrics.any { it.label == "Tempo residuo" })
        assertFalse(info.userExplanation.contains("12 min"))
        assertFalse(info.userExplanation.contains("17 min"))
        assertTrue(info.soglieCostanti.isBlank())
    }

    @Test
    fun testWp33G_BloccoNottePausa45m_ZeroResidualIsNotActiveTimer() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 31.0f,
                humidexBedroom = 28.0f
            ),
            logicaControllo = LogicaControllo(
                sogliaAttivazioneApplicata = 30.0f,
                diagnosticaAc = DiagnosticaAc(
                    pausaNotturnaResiduoMinuti = 0,
                    timerResiduoMinuti = 12
                )
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("BLOCCO_NOTTE_PAUSA_45M", aiData)

        assertNull(info.remainingTimerMinutes)

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex camera" }
        assertNotNull(humidexMetric)

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Humidex impostato" }
        assertNotNull(thresholdMetric)

        assertFalse(info.userExplanation.contains("0 min"))
    }

    @Test
    fun testWp33H_ComfortNotturno_MetricContamination_RemovesSocBattery() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 31.5f,
                humidexBedroom = 28.0f
            ),
            logicaControllo = LogicaControllo(
                nightDryHumidexThreshold = 30.0f,
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = "MIN_OFF",
                    timerResiduoMinuti = 12,
                    timerDurataMinuti = 20,
                    pausaNotturnaResiduoMinuti = 23
                )
            ),
            metricheElettriche = MetricheElettriche(
                powerwallSocPercent = 70f,
                batteryPowerW = 800f,
                surplusW = 1500f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("COMFORT_NOTTURNO_RAGGIUNTO", aiData)

        assertEquals("Comfort notturno raggiunto", info.userTitle)

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex camera" }
        assertNotNull(humidexMetric)
        assertTrue(humidexMetric!!.value.contains("28,0") || humidexMetric.value.contains("28.0"))

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Soglia NIGHT_DRY" }
        assertNotNull(thresholdMetric)
        assertTrue(thresholdMetric!!.value.contains("30,0") || thresholdMetric.value.contains("30.0"))

        assertFalse(info.metrics.any { it.label == "Humidex zona giorno" })
        assertFalse(info.metrics.any { it.label == "Humidex di attivazione" })
        assertFalse(info.metrics.any { it.label == "SOC Batteria" })
        assertFalse(info.metrics.any { it.label == "SOC Minimo" })
        assertFalse(info.metrics.any { it.label == "Potenza Batteria" })
        assertFalse(info.metrics.any { it.label == "Surplus Solare" })
        assertFalse(info.metrics.any { it.label == "Temperatura Int." })
        assertFalse(info.metrics.any { it.label == "Humidex comfort" })
        assertFalse(info.metrics.any { it.label == "Timer" })
        assertFalse(info.metrics.any { it.label == "Durata minima" })
        assertFalse(info.metrics.any { it.label == "Tempo residuo" })

        assertNull(info.remainingTimerMinutes)

        assertFalse(info.metrics.any { it.value.contains("31.5") || it.value.contains("31,5") })
        assertFalse(info.metrics.any { it.value.contains("30.5") || it.value.contains("30,5") })

        assertFalse(info.userExplanation.contains("12 min") || info.userExplanation.contains("23 min"))
        assertTrue(info.soglieCostanti.isBlank())
    }

    @Test
    fun testWp33I_StandbyDeficitSolare_NullsActiveTimerWithPoisonedPayload() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                tempoMancanteAnticicloMinuti = 9,
                diagnosticaAc = DiagnosticaAc(
                    deficitTolleranzaMinuti = 30,
                    deficitResiduoMinuti = 17,
                    timerTipo = "MIN_RUN",
                    timerResiduoMinuti = 12,
                    timerDurataMinuti = 20,
                    pausaNotturnaResiduoMinuti = 23
                )
            ),
            metricheElettriche = MetricheElettriche(
                powerwallSocPercent = 65f,
                surplusW = 200f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("STANDBY_DEFICIT_SOLARE", aiData)

        assertNull(info.remainingTimerMinutes)

        val surplusMetric = info.metrics.firstOrNull { it.label == "Surplus Solare" }
        assertNotNull(surplusMetric)

        val socMetric = info.metrics.firstOrNull { it.label == "SOC Batteria" }
        assertNotNull(socMetric)

        assertFalse(info.metrics.any { it.label == "Tolleranza" })
        assertFalse(info.metrics.any { it.label == "Timer" })
        assertFalse(info.metrics.any { it.label == "Durata minima" })
        assertFalse(info.metrics.any { it.label == "Tempo residuo" })

        assertTrue(info.soglieCostanti.isBlank())
    }

    @Test
    fun testWp33I_TolleranzaDeficit_DiscriminatesResiduo() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                tempoMancanteAnticicloMinuti = 9,
                diagnosticaAc = DiagnosticaAc(
                    deficitResiduoMinuti = 17,
                    timerResiduoMinuti = 12,
                    pausaNotturnaResiduoMinuti = 23
                )
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("TOLLERANZA_DEFICIT_IN_CORSO", aiData)

        assertEquals(17, info.remainingTimerMinutes)
    }

    @Test
    fun testWp33I_DeficitRilevato_ShowsTolleranzaDurationWithoutCountdown() {
        val aiData = AiManagedData(
            logicaControllo = LogicaControllo(
                diagnosticaAc = DiagnosticaAc(
                    deficitTolleranzaMinuti = 30,
                    deficitResiduoMinuti = 30
                )
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("DEFICIT_RILEVATO_AVVIO_TIMER", aiData)

        assertNull(info.remainingTimerMinutes)

        val tolMetric = info.metrics.firstOrNull { it.label == "Tolleranza" }
        assertNotNull(tolMetric)
        assertEquals("30 min", tolMetric!!.value)
    }

    @Test
    fun testWp33J_MantenimentoStandbyInverter_UsesClimateSupervisionEvidence() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 31.0f,
                humidexBedroom = 28.0f
            ),
            logicaControllo = LogicaControllo(
                sogliaAttivazioneApplicata = 29.0f,
                sogliaSpegnimentoComfort = 26.5f,
                diagnosticaAc = DiagnosticaAc(
                    timerTipo = "MIN_RUN",
                    timerResiduoMinuti = 12,
                    timerDurataMinuti = 20,
                    deficitTolleranzaMinuti = 30,
                    deficitResiduoMinuti = 17,
                    pausaNotturnaResiduoMinuti = 23
                )
            ),
            metricheElettriche = MetricheElettriche(
                powerwallSocPercent = 70f,
                surplusW = 1500f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("MANTENIMENTO_STANDBY_INVERTER", aiData)

        assertEquals("Standby energetico inverter", info.userTitle)
        assertNull(info.remainingTimerMinutes)

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex zona giorno" }
        assertNotNull(humidexMetric)
        assertTrue(humidexMetric!!.value.contains("31,0") || humidexMetric.value.contains("31.0"))

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Humidex di attivazione" }
        assertNotNull(thresholdMetric)
        assertTrue(thresholdMetric!!.value.contains("29,0") || thresholdMetric.value.contains("29.0"))

        val surplusMetric = info.metrics.firstOrNull { it.label == "Surplus Solare" }
        assertNotNull(surplusMetric)
        assertTrue(surplusMetric!!.value.contains("1500"))

        assertFalse(info.metrics.any { it.label == "SOC Batteria" })
        assertFalse(info.metrics.any { it.label == "Humidex comfort" })
        assertFalse(info.metrics.any { it.label == "Timer" })
        assertFalse(info.metrics.any { it.label == "Durata minima" })
        assertFalse(info.metrics.any { it.label == "Tolleranza" })
        assertFalse(info.metrics.any { it.label == "Tempo residuo" })

        assertTrue(info.soglieCostanti.isBlank())
    }

    @Test
    fun testWp33J_CoolingFamily_PreservesExistingEvidenceAndTimerSafety() {
        val reasons = listOf(
            "PRONTO_ACCENSIONE_COOLING",
            "RIPRESA_COOLING_DA_STANDBY",
            "MANTENIMENTO_COOLING_ON"
        )

        for (reason in reasons) {
            val aiData = AiManagedData(
                metricheAmbientali = MetricheAmbientali(
                    humidexLiving = 31.0f
                ),
                logicaControllo = LogicaControllo(
                    sogliaAttivazioneApplicata = 29.0f,
                    diagnosticaAc = DiagnosticaAc(
                        timerTipo = "MIN_RUN",
                        timerResiduoMinuti = 12,
                        timerDurataMinuti = 20,
                        deficitResiduoMinuti = 17,
                        pausaNotturnaResiduoMinuti = 23
                    )
                ),
                metricheElettriche = MetricheElettriche(
                    powerwallSocPercent = 70f,
                    surplusW = 1500f
                )
            )

            val info = AcReasonMapper.getAcReasonInfo(reason, aiData)

            assertNull("Reason $reason should have null remainingTimerMinutes", info.remainingTimerMinutes)

            val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex zona giorno" }
            assertNotNull("Reason $reason missing Humidex zona giorno", humidexMetric)
            assertTrue(humidexMetric!!.value.contains("31,0") || humidexMetric.value.contains("31.0"))

            val thresholdMetric = info.metrics.firstOrNull { it.label == "Humidex di attivazione" || it.label == "Humidex comfort" }
            assertNotNull("Reason $reason missing threshold metric", thresholdMetric)

            val surplusMetric = info.metrics.firstOrNull { it.label == "Surplus Solare" }
            assertNotNull("Reason $reason missing Surplus Solare", surplusMetric)

            assertFalse("Reason $reason should not have Timer", info.metrics.any { it.label == "Timer" })
            assertFalse("Reason $reason should not have Durata minima", info.metrics.any { it.label == "Durata minima" })
            assertFalse("Reason $reason should not have Tolleranza", info.metrics.any { it.label == "Tolleranza" })
        }
    }

    @Test
    fun testHumidexB32_ComfortNotturnoRaggiunto_DisplaysAuthoritativeNightValues() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexBedroom = 28.5f
            ),
            logicaControllo = LogicaControllo(
                sogliaAttivazioneApplicata = 29.0f,
                nightDryHumidexMargin = 0.7f,
                nightDryHumidexThreshold = 31.4f // Poisoned value different from 29.0 + 0.7 = 29.7
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("COMFORT_NOTTURNO_RAGGIUNTO", aiData)

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex camera" }
        assertNotNull(humidexMetric)
        assertTrue(humidexMetric!!.value.contains("28,5") || humidexMetric.value.contains("28.5"))

        val marginMetric = info.metrics.firstOrNull { it.label == "Margine NIGHT_DRY" }
        assertNotNull(marginMetric)
        assertTrue(marginMetric!!.value.contains("0,7") || marginMetric.value.contains("0.7"))

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Soglia NIGHT_DRY" }
        assertNotNull(thresholdMetric)
        assertTrue(thresholdMetric!!.value.contains("31,4") || thresholdMetric.value.contains("31.4"))

        // Assert no local arithmetic derivation like 29.7 is produced
        assertFalse(info.metrics.any { it.value.contains("29,7") || it.value.contains("29.7") })
    }

    @Test
    fun testHumidexB32_ProntoAccensioneNightDry_DisplaysNightValuesAndSoc() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexBedroom = 29.8f
            ),
            logicaControllo = LogicaControllo(
                sogliaAttivazioneApplicata = 29.0f,
                nightDryHumidexMargin = 0.5f,
                nightDryHumidexThreshold = 30.2f // Poisoned value
            ),
            metricheElettriche = MetricheElettriche(
                powerwallSocPercent = 80f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("PRONTO_ACCENSIONE_NIGHT_DRY", aiData)

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex camera" }
        assertNotNull(humidexMetric)

        val marginMetric = info.metrics.firstOrNull { it.label == "Margine NIGHT_DRY" }
        assertNotNull(marginMetric)

        val thresholdMetric = info.metrics.firstOrNull { it.label == "Soglia NIGHT_DRY" }
        assertNotNull(thresholdMetric)
        assertTrue(thresholdMetric!!.value.contains("30,2") || thresholdMetric.value.contains("30.2"))

        val socMetric = info.metrics.firstOrNull { it.label == "SOC Batteria" }
        assertNotNull(socMetric)
    }

    @Test
    fun testHumidexB32_MantenimentoNightDry_ExcludesMarginAndThreshold() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexBedroom = 28.0f
            ),
            logicaControllo = LogicaControllo(
                nightDryHumidexMargin = 0.5f,
                nightDryHumidexThreshold = 29.5f
            ),
            metricheElettriche = MetricheElettriche(
                powerwallSocPercent = 75f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("MANTENIMENTO_NIGHT_DRY", aiData)

        val humidexMetric = info.metrics.firstOrNull { it.label == "Humidex camera" }
        assertNotNull(humidexMetric)

        val socMetric = info.metrics.firstOrNull { it.label == "SOC Batteria" }
        assertNotNull(socMetric)

        assertFalse(info.metrics.any { it.label == "Margine NIGHT_DRY" })
        assertFalse(info.metrics.any { it.label == "Soglia NIGHT_DRY" })
    }

    @Test
    fun testHumidexB32_CoolingComfortReasons_UseSogliaSpegnimentoComfort() {
        val aiData = AiManagedData(
            metricheAmbientali = MetricheAmbientali(
                humidexLiving = 26.0f
            ),
            logicaControllo = LogicaControllo(
                sogliaAttivazioneApplicata = 30.0f,
                sogliaSpegnimentoComfort = 26.5f
            )
        )

        val info = AcReasonMapper.getAcReasonInfo("MANTENIMENTO_COOLING_ON", aiData)

        val comfortMetric = info.metrics.firstOrNull { it.label == "Humidex comfort" }
        assertNotNull(comfortMetric)
        assertTrue(comfortMetric!!.value.contains("26,5") || comfortMetric.value.contains("26.5"))

        assertFalse(info.metrics.any { it.label == "Soglia Humidex" || it.label == "Humidex di attivazione" && it.value.contains("30") })
    }
}
