package com.domopi.app.data

import kotlinx.serialization.Serializable

@Serializable
data class AiManagedData(
    val timestamp: Long = 0,
    val data_ora_formattata: String = "",
    val stagione_attiva: String = "",
    val metriche_elettriche: MetricheElettriche = MetricheElettriche(),
    val metriche_ambientali: MetricheAmbientali = MetricheAmbientali(),
    val logica_controllo: LogicaControllo = LogicaControllo(),
    val stato_condizionatore: StatoCondizionatore = StatoCondizionatore(),
    val stato_vmc: StatoVmc = StatoVmc()
)

@Serializable
data class MetricheElettriche(
    val produzione_fv_w: Float = 0f,
    val consumo_casa_w: Float = 0f,
    val surplus_w: Float = 0f,
    val grid_power_w: Float = 0f,
    val grid_import_w: Float = 0f,
    val grid_export_w: Float = 0f,
    val battery_power_w: Float = 0f,
    val battery_charge_w: Float = 0f,
    val battery_discharge_w: Float = 0f,
    val powerwall_soc_percent: Float = 0f,
    val consumo_ac_w: Float = 0f,
    val consumo_medio_storico_fascia_w: Float = 0f
)

@Serializable
data class MetricheAmbientali(
    val temp_cameraMatrimoniale: Float = 0f,
    val temperatura_c: Float = 0f,
    val humidex: Float = 0f,
    val humidex_living: Float = 0f,
    val humidex_bedroom: Float = 0f,
    val altitudine_sole: Float = 0f
)

@Serializable
data class LogicaControllo(
    val vmc_portata_stimata_m3h: Int = 0,
    val stanza_rilevamento_vmc: String = "",
    val soglia_attivazione_applicata: Float = 0f,
    val soc_minimo_applied: Float = 0f,
    val tempo_mancante_anticiclo_minuti: Int = 0,
    val previsione_solare_domani_kwh: Float = 0f,
    val previsione_solare_data: String = "",
    val previsione_ricarica_battery_percent: Int = 0,
    val kwh_stimati_in_batteria: Float = 0f,
    val blocco_emergenza_attivo: Boolean = false,
    val cuscinetto_sicurezza_kwh: Float = 0f,
    val cuscinetto_richiesto_kwh: Float = 0f
)

@Serializable
data class StatoCondizionatore(
    val stato_attuale: String = "",
    val motivo_logica: String = "",
    val temperatura_impostata_c: Float = 0f,
    val modalita_aria: String = ""
)

@Serializable
data class StatoVmc(
    val velocita_attuale: Int = 0,
    val motivo_logica: String = "",
    val humidex_esterno: Float = 0f,
    val temperatura_esterna_c: Float = 0f
)
