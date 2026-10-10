@file:OptIn(InternalSerializationApi::class)
package com.domopi.app.data

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AiManagedData(
    val timestamp: Long = 0,
    @SerialName("data_ora_formattata")
    val dataOraFormattata: String = "",
    @SerialName("stagione_attiva")
    val stagioneAttiva: String = "",
    @SerialName("metriche_elettriche")
    val metricheElettriche: MetricheElettriche = MetricheElettriche(),
    @SerialName("metriche_ambientali")
    val metricheAmbientali: MetricheAmbientali = MetricheAmbientali(),
    @SerialName("logica_controllo")
    val logicaControllo: LogicaControllo = LogicaControllo(),
    @SerialName("stato_condizionatore")
    val statoCondizionatore: StatoCondizionatore = StatoCondizionatore(),
    @SerialName("stato_vmc")
    val statoVmc: StatoVmc = StatoVmc(),
)

@Serializable
data class MetricheElettriche(
    @SerialName("produzione_fv_w")
    val produzioneFvW: Float = 0f,
    @SerialName("consumo_casa_w")
    val consumoCasaW: Float = 0f,
    @SerialName("surplus_w")
    val surplusW: Float = 0f,
    @SerialName("grid_power_w")
    val gridPowerW: Float = 0f,
    @SerialName("grid_import_w")
    val gridImportW: Float = 0f,
    @SerialName("grid_export_w")
    val gridExportW: Float = 0f,
    @SerialName("battery_power_w")
    val batteryPowerW: Float = 0f,
    @SerialName("battery_charge_w")
    val batteryChargeW: Float = 0f,
    @SerialName("battery_discharge_w")
    val batteryDischargeW: Float = 0f,
    @SerialName("powerwall_soc_percent")
    val powerwallSocPercent: Float = 0f,
    @SerialName("consumo_ac_w")
    val consumoAcW: Float = 0f,
    @SerialName("consumo_medio_storico_fascia_w")
    val consumoMedioStoricoFasciaW: Float = 0f,
    @SerialName("grid_import_kwh_oggi")
    val gridImportKwhOggi: Float = 0f,
    @SerialName("grid_import_kwh_ieri")
    val gridImportKwhIeri: Float = 0f,
)

@Serializable
data class MetricheAmbientali(
    @SerialName("temp_cameraMatrimoniale")
    val tempCameraMatrimoniale: Float = 0f,
    @SerialName("temperatura_c")
    val temperaturaC: Float = 0f,
    val humidex: Float = 0f,
    @SerialName("humidex_living")
    val humidexLiving: Float = 0f,
    @SerialName("humidex_bedroom")
    val humidexBedroom: Float = 0f,
    @SerialName("altitudine_sole")
    val altitudineSole: Float = 0f,
)

@Serializable
data class DiagnosticaAc(
    @SerialName("timer_tipo")
    val timerTipo: String? = null,
    @SerialName("timer_residuo_minuti")
    val timerResiduoMinuti: Int? = null,
    @SerialName("timer_durata_minuti")
    val timerDurataMinuti: Int? = null,
    @SerialName("deficit_tolleranza_minuti")
    val deficitTolleranzaMinuti: Int? = null,
    @SerialName("deficit_residuo_minuti")
    val deficitResiduoMinuti: Int? = null,
    @SerialName("pausa_notturna_residuo_minuti")
    val pausaNotturnaResiduoMinuti: Int? = null,
    @SerialName("causa_transizione")
    val causaTransizione: String? = null,
)

@Serializable
data class LogicaControllo(
    @SerialName("vmc_portata_stimata_m3h")
    val vmcPortataStimataM3h: Int = 0,
    @SerialName("stanza_rilevamento_vmc")
    val stanzaRilevamentoVmc: String = "",
    @SerialName("soglia_attivazione_applicata")
    val sogliaAttivazioneApplicata: Float = 0f,
    @SerialName("soglia_spegnimento_comfort")
    val sogliaSpegnimentoComfort: Float = 0f,
    @SerialName("ampiezza_isteresi_humidex")
    val ampiezzaIsteresiHumidex: Float = 0f,
    @SerialName("soglia_temperatura_avvio_cooling_c")
    val sogliaTemperaturaAvvioCoolingC: Float? = null,
    @SerialName("soglia_temperatura_off_comfort_c")
    val sogliaTemperaturaOffComfortC: Float? = null,
    @SerialName("soc_minimo_applied")
    val socMinimoApplied: Float = 0f,
    @SerialName("tempo_mancante_anticiclo_minuti")
    val tempoMancanteAnticicloMinuti: Int = 0,
    @SerialName("previsione_solare_domani_kwh")
    val previsioneSolareDomaniKwh: Float = 0f,
    @SerialName("previsione_solare_data")
    val previsioneSolareData: String = "",
    @SerialName("previsione_ricarica_battery_percent")
    val previsioneRicaricaBatteryPercent: Int = 0,
    @SerialName("kwh_stimati_in_batteria")
    val kwhStimatiInBatteria: Float = 0f,
    @SerialName("blocco_emergenza_attivo")
    val bloccoEmergenzaAttivo: Boolean = false,
    @SerialName("cuscinetto_sicurezza_kwh")
    val cuscinettoSicurezzaKwh: Float = 0f,
    @SerialName("cuscinetto_richiesto_kwh")
    val cuscinettoRichiestoKwh: Float = 0f,
    @SerialName("stagione_attuale")
    val stagioneAttuale: String = "",
    @SerialName("night_dry_humidex_margin")
    val nightDryHumidexMargin: Float = 0f,
    @SerialName("night_dry_humidex_threshold")
    val nightDryHumidexThreshold: Float = 0f,
    @SerialName("diagnostica_ac")
    val diagnosticaAc: DiagnosticaAc = DiagnosticaAc(),
)

@Serializable
data class StatoCondizionatore(
    @SerialName("stato_attuale")
    val statoAttuale: String = "",
    @SerialName("motivo_logica")
    val motivoLogica: String = "",
    @SerialName("temperatura_impostata_c")
    val temperaturaImpostataC: Float = 0f,
    @SerialName("modalita_aria")
    val modalitaAria: String = "",
)

@Serializable
data class StatoVmc(
    @SerialName("velocita_attuale")
    val velocitaAttuale: Int = 0,
    @SerialName("motivo_logica")
    val motivoLogica: String = "",
    @SerialName("humidex_esterno")
    val humidexEsterno: Float = 0f,
    @SerialName("temperatura_esterna_c")
    val temperaturaEsternaC: Float = 0f,
)
