"""Deterministic Italian presentation of tool results; no generated numbers.

Answers are read aloud, so the text is short and spoken-friendly (gradi, per cento,
kilowatt). Technical caveats go to each result's limitations via notes(), which the
app shows under "Mostra provenienza e limiti".
"""
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo
import math
import heating_profile_tools
import rules_tools

LABELS = {
    "grid_import_kwh": "Prelievo dalla rete", "grid_export_kwh": "Energia immessa in rete",
    "home_consumption_kwh": "Consumo della casa", "solar_production_kwh": "Produzione fotovoltaica",
    "battery_charge_kwh": "Energia caricata nella batteria", "battery_discharge_kwh": "Energia scaricata dalla batteria",
    "soc_mean_percent": "Livello medio della batteria Tesla", "solar_power_w": "Produzione fotovoltaica",
    "home_consumption_w": "Consumo della casa", "grid_power_w": "Scambio con la rete",
    "battery_power_w": "Potenza della batteria", "battery_soc_percent": "Carica della batteria Tesla",
}
# Short names for lists ("Accese: lampada TV, prolunga").
LIGHT_NAMES = {
    "lights_living": "soggiorno", "lights_libreria": "libreria", "lights_tv": "lampada TV",
    "lights_reading": "tavolino lettura", "lights_laundry": "lavanderia",
    "lights_porch_entrance": "portico ingresso", "lights_service_entrance": "ingresso servizio",
    "pool_water": "luci piscina", "pool_deck": "luci pedana piscina", "lights_prolunga": "prolunga",
}
# Phrases with article for sentences; True = plural.
LIGHT_SPOKEN = {
    "lights_living": ("la luce del soggiorno", False), "lights_libreria": ("la luce della libreria", False),
    "lights_tv": ("la lampada TV", False), "lights_reading": ("la lampada del tavolino lettura", False),
    "lights_laundry": ("la luce della lavanderia", False),
    "lights_porch_entrance": ("la luce del portico ingresso", False),
    "lights_service_entrance": ("la luce dell'ingresso servizio", False),
    "pool_water": ("le luci della piscina", True), "pool_deck": ("le luci della pedana piscina", True),
    "lights_prolunga": ("la prolunga", False),
}
SCENE_ACTIONS = {
    "tv": "attivo la modalità TV", "sleep": "tra 2 minuti spengo tutte le luci",
    "all_on": "accendo tutte le luci", "all_off": "spengo tutte le luci",
}
# What the Node-RED scene flows do (live flows .20, 2026-10-11): diagnostics only.
SCENE_DETAILS = {
    "tv": "Scena TV: accende lampada TV e soggiorno, spegne libreria e lampadario TV.",
    "sleep": "Scena notte: dopo 2 minuti esegue lo spegnimento di tutte le luci, una alla volta in circa 35 secondi.",
    "all_on": ("Accende soggiorno, TV, libreria, pedana e luci piscina e lettura in circa 30 secondi; con le luci "
               "ECO attive solo lettura e TV; in modalità vacanza non ha effetto."),
    "all_off": "Spegne luci interne, esterne e piscina, una alla volta in circa 35 secondi.",
}
THERMOSTAT_SPOKEN = {"thermostat_living": "il termostato del living", "thermostat_bath": "il termostato del bagno"}
THERMOSTAT_TITLES = {"thermostat_living": "Termostato del living", "thermostat_bath": "Termostato del bagno"}
ROOM_LABELS = {
    "living": "Living", "bedroom": "Camera da letto", "kids_room": "Cameretta",
    "service_bathroom": "Bagno servizio", "technical_room": "Locale tecnico", "outdoor": "Esterno",
}
ROOM_IN = {
    "living": "Nel living", "bedroom": "Nella camera da letto", "kids_room": "Nella cameretta",
    "service_bathroom": "Nel bagno servizio", "technical_room": "Nel locale tecnico", "outdoor": "All'esterno",
}
ROOM_THERMOSTAT = {"living": "thermostat_living", "service_bathroom": "thermostat_bath"}
UNITS = {"kWh": "kilowattora", "%": "per cento", "punti percentuali": "punti percentuali"}

ROME = ZoneInfo('Europe/Rome')


def day_text(day):
    """gg/mm for the current year, gg/mm/aaaa otherwise: easy to read and to speak."""
    value = date.fromisoformat(day) if isinstance(day, str) else day
    return value.strftime('%d/%m' if value.year == datetime.now(ROME).year else '%d/%m/%Y')


def moment_text(ms):
    instant = datetime.fromtimestamp(ms / 1000, ROME)
    return f"{day_text(instant.date())} alle {instant.strftime('%H:%M')}"


def period_text(start, end_exclusive):
    """Periods are end-exclusive internally; spoken as inclusive calendar days."""
    last = date.fromisoformat(end_exclusive) - timedelta(days=1)
    if last == date.fromisoformat(start):
        return f"il {day_text(start)}"
    return f"dal {day_text(start)} al {day_text(last)}"


def number(value, digits=2):
    return f"{value:.{digits}f}".replace('.', ',')


def spoken(value, digits=1):
    """Like number() but without a trailing ",0": 18 gradi, 22,4 gradi."""
    rounded = round(value, digits)
    return str(int(rounded)) if rounded == int(rounded) else number(rounded, digits)


def power(watts):
    watts = abs(watts)
    return f"{spoken(watts, 0)} watt" if watts < 1000 else f"{spoken(watts / 1000)} kilowatt"


def join_it(parts):
    parts = [p for p in parts if p]
    return parts[0] if len(parts) == 1 else ', '.join(parts[:-1]) + ' e ' + parts[-1]


def capitalize(text):
    return text[:1].upper() + text[1:]


def humanize(code):
    return str(code).strip().replace('_', ' ').lower()


def numeric(record, *path):
    for key in path:
        record = record.get(key) if isinstance(record, dict) else None
    try:
        return record if type(record) in (int, float) and math.isfinite(record) else None
    except OverflowError:
        return None


def received_note(observations):
    if not observations:
        return []
    received = max(observation['received_at_ms'] for observation in observations)
    notes = [f"Ultimo dato ricevuto il {moment_text(received)}."]
    if any(observation['retained'] for observation in observations):
        notes.append("Uno o più messaggi sono retained: valore conservato dal broker, non necessariamente recente.")
    return notes


# Predictive Reserve reasons the climate controller can record (Node-RED snapshot 2026-10-10).
PR_REASONS = {
    'PR_PRESTART_PENDING': "l'avvio notturno in deumidificazione era in attesa dell'autorizzazione del Predictive Reserve",
    'BLOCCO_PR_GATE_INVALID': "avvio notturno bloccato: stato del Predictive Reserve non determinabile",
    'BLOCCO_PR_REQUEST_INVALID': "avvio notturno bloccato: richiesta al Predictive Reserve non correlabile",
}

DISCONNECTED = " La casa non è collegata: sono gli ultimi dati ricevuti."


def predictive_reserve_text(r):
    observations = r['observations']
    if not observations:
        return "Lo stato del Predictive Reserve non è disponibile."
    value = lambda metric: observations[metric]['value'] if metric in observations else None
    control, mode, ready = value('control_enabled'), value('operating_mode'), value('ready')
    if control == 'false':
        text = "Il Predictive Reserve è disattivato."
    elif control is None:
        text = "Non risulta se il Predictive Reserve sia attivo."
    elif mode == 'SHADOW':
        text = "Il Predictive Reserve è attivo in modalità SHADOW: blocca gli avvii notturni in deumidificazione."
    elif mode == 'ACTIVE' and ready == 'false':
        text = ("Il Predictive Reserve è attivo in modalità ACTIVE ma il modello non è pronto: "
                "blocca gli avvii notturni in deumidificazione.")
    elif mode == 'ACTIVE' and ready == 'true':
        text = ("Il Predictive Reserve è attivo in modalità ACTIVE con modello pronto: autorizza l'avvio "
                "notturno solo in eco26dry e quando il bilancio energetico è favorevole.")
    else:
        text = "Il Predictive Reserve è attivo, ma modalità o prontezza non sono disponibili."
    return text + ('' if r['connected'] else DISCONNECTED)


def predictive_reserve_notes(r):
    observations = r['observations']
    value = lambda metric: observations[metric]['value'] if metric in observations else None
    mode, ready = value('operating_mode'), value('ready')
    return ['Modalità: {} · Modello: {}.'.format(
        mode or 'non ricevuta', {'true': 'pronto', 'false': 'non pronto'}.get(ready, 'stato non ricevuto'))
    ] + received_note(observations.values())


def log_text(result):
    prefix = f"Log {result['file']} del {day_text(result['day'])}"
    if not result['records']:
        if result['status'] == 'source_not_updated':
            since = day_text(datetime.fromtimestamp(result['last_modified_ms'] / 1000, ROME).date())
            text = f"{prefix}: il log non è più aggiornato dal {since}; non contiene dati per questo giorno."
            if result.get('cadence') == 'archive':
                text += " È un archivio storico della simulazione SHADOW, non lo stato attuale del Predictive Reserve."
            return text
        return prefix + ": " + ("sorgente non disponibile." if result['status'] in ('unavailable', 'source_rejected')
                                 else "nessun record utilizzabile; non prova assenza di eventi.")
    # Latest by evidence timestamp, not file order; forecast has only target date.
    key = 'timestamp_ms' if result['source'] == 'reserve_shadow' else 'timestamp'
    last = (result['records'][-1] if result['source'] == 'solar_forecast' else
            max(result['records'], key=lambda r: numeric(r['record'], key) or 0))
    record = last['record']
    parts = [f"{prefix}: {result['matched_records']} record trovati."]
    if result['source'] == 'solar_forecast':
        value = numeric(record, 'kwh_stimati_impianto_reali')
        parts.append(f"Produzione prevista: {number(value, 1)} kilowattora." if value is not None else "Produzione prevista non disponibile.")
        if result["matched_records"] > 1:
            parts.append("Sono presenti più record per questo giorno; riporto l’ultimo restituito in ordine di file.")
    elif result['source'] == 'learning':
        for key, label in [('consumo_medio_w', 'Consumo medio orario registrato'), ('produzione_media_w', 'Produzione media oraria registrata'), ('carica_media_batteria_w', 'Carica media oraria registrata')]:
            value = numeric(record, 'metriche_medie_ora', key)
            if value is not None:
                parts.append(f"{label}: {spoken(value, 0)} watt.")
        value = numeric(record, 'calcolo_efficienza', 'nuova_efficienza_storica_ema')
        if value is not None:
            parts.append(f"Efficienza storica EMA registrata: {number(value, 3)}; non è una misura istantanea.")
    elif result['source'] == 'reserve_shadow':
        for key, label in [('reserve_kwh', 'Riserva simulata'), ('climate_budget_kwh', 'Budget climatico simulato'), ('support_pred_kwh', 'Supporto previsto')]:
            value = numeric(record, 'energy', key)
            if value is not None:
                parts.append(f"{label}: {number(value, 1)} kilowattora.")
        parts.append("Questo log è telemetria SHADOW: non dimostra attivazioni o autorizzazioni ai dispositivi.")
    else:
        logic = record.get('logica_controllo', {})
        reason = logic.get('motivo_ac') if isinstance(logic, dict) else None
        if isinstance(reason, str):
            parts.append(f"Motivo AC registrato: {reason[:180]}.")
            if reason.strip() in PR_REASONS:
                parts.append(f"Significato: {PR_REASONS[reason.strip()]}.")
    if result['truncated']:
        parts.append("Risultati limitati: il dettaglio non comprende tutti i record.")
    if result['invalid_lines']:
        parts.append(f"Righe non utilizzabili: {result['invalid_lines']}.")
    return ' '.join(parts)


def thermostat_facts(observations, thermostat, with_temperature=True):
    value = lambda metric: observations.get(f'{thermostat}.{metric}', {}).get('value')
    facts = []
    if with_temperature and value('current_temperature') is not None:
        facts.append(f"{spoken(value('current_temperature'))} gradi")
    if value('target_temperature') is not None:
        facts.append(f"setpoint {spoken(value('target_temperature'))} gradi")
    if value('power') is not None:
        # Tasmota relay: the room asks for heat, not the state of the heating plant.
        facts.append('chiede calore' if value('power') else 'non chiede calore')
    return facts


def heating_text(r):
    observations = r['observations']
    if not observations:
        return "Lo stato dell'impianto di riscaldamento non è disponibile."
    value = lambda key: observations[key]['value'] if key in observations else None
    parts = []
    power, level, mode = value('fireplace.power'), value('fireplace.level'), value('fireplace.mode')
    start, stop, auto = value('fireplace.start_time'), value('fireplace.stop_time'), value('fireplace.auto_power')
    if power is not None or level is not None or mode is not None:
        # Two sentences so the mode name is heard as a name when read aloud.
        text = 'Caminetto ' + ('acceso' if power else 'spento' if power is False else 'stato non disponibile')
        if power and level is not None:
            text += f" a livello {spoken(level, 0)}"
        parts.append(text + '.')
        auto_text = None if auto is None else 'potenza automatica ' + ('attiva' if auto else 'disattivata')
        if mode is not None:
            text = f"È in modalità {mode}"
            if mode not in ('Disattivato', 'Manuale') and start and stop:
                text += f", programmato dalle {start} alle {stop}"
            parts.append(text + (f", con {auto_text}" if auto_text else '') + '.')
        elif auto_text:
            parts.append(capitalize(auto_text) + '.')
    puffer = [f"{label} {spoken(value(key))} gradi" for key, label in (
        ('puffer.top_temperature', 'alto'), ('puffer.bottom_temperature', 'basso'),
        ('puffer.acs_temperature', 'acqua sanitaria')) if value(key) is not None]
    if puffer:
        parts.append('Puffer: ' + ', '.join(puffer) + '.')
    enabled, running = value('floor_pump.enabled'), value('floor_pump.running')
    if enabled is not None or running is not None:
        facts = []
        if enabled is not None:
            facts.append('abilitata' if enabled else 'disabilitata')
        if running is not None:
            facts.append('in funzione' if running else 'ferma')
        parts.append('Pompa pavimento ' + ' e '.join(facts) + '.')
    flame, modulation = value('gas_boiler.flame'), value('gas_boiler.modulation')
    if flame is not None:
        text = 'Caldaia a gas: fiamma ' + ('accesa' if flame else 'spenta')
        if flame and modulation is not None:
            text += f", modulazione {spoken(modulation, 0)} per cento"
        parts.append(text + '.')
    pump, collector = value('solar_thermal.pump_speed'), value('solar_thermal.collector_temperature')
    if pump is not None or collector is not None:
        facts = []
        if pump is not None:
            facts.append(f"pompa al {spoken(pump, 0)} per cento" if pump > 0 else 'pompa ferma')
        if collector is not None:
            facts.append(f"collettore {spoken(collector)} gradi")
        parts.append('Solare termico: ' + ', '.join(facts) + '.')
    return ' '.join(parts) + ('' if r['connected'] else DISCONNECTED)


def thermostats_text(item):
    r = item['result']
    selected = list(THERMOSTAT_SPOKEN) if item['thermostat'] == 'all' else [item['thermostat']]
    parts = []
    for thermostat in selected:
        facts = thermostat_facts(r['observations'], thermostat)
        name = THERMOSTAT_TITLES[thermostat]
        parts.append(f"{name}: " + ', '.join(facts) + '.' if facts else f"{name}: dati non disponibili.")
    return ' '.join(parts) + ('' if r['connected'] else DISCONNECTED)


def thermostats_notes(item):
    r = item['result']
    notes = []
    for thermostat in THERMOSTAT_SPOKEN:
        low = r['observations'].get(f'{thermostat}.min_temperature', {}).get('value')
        high = r['observations'].get(f'{thermostat}.max_temperature', {}).get('value')
        if low is not None and high is not None:
            notes.append(f"{THERMOSTAT_TITLES[thermostat]}: timer delle fasce orarie "
                         f"{spoken(high)} gradi in fascia e {spoken(low)} gradi fuori fascia.")
    return notes + received_note(r['observations'].values())


def environment_text(item):
    r = item['result']
    observations, room, metric = r['observations'], item['room'], item['metric']
    value = lambda rm, m: observations.get(f'{rm}.{m}', {}).get('value')
    rooms = list(ROOM_LABELS) if room == 'all' else [room]
    if room != 'all' and metric != 'all':
        v = value(room, metric)
        if v is None:
            name = {'temperature': 'la temperatura', 'humidity': "l'umidità", 'humidex': "l'humidex"}[metric]
            text = f"{ROOM_IN[room]} {name} non è disponibile."
        else:
            text = {'temperature': f"{ROOM_IN[room]} ci sono {spoken(v)} gradi.",
                    'humidity': f"{ROOM_IN[room]} l'umidità è al {spoken(v)} per cento.",
                    'humidex': f"{ROOM_IN[room]} l'humidex è {spoken(v)}."}[metric]
    elif room != 'all':
        facts = []
        if value(room, 'temperature') is not None:
            facts.append(f"{spoken(value(room, 'temperature'))} gradi")
        if value(room, 'humidity') is not None:
            facts.append(f"umidità {spoken(value(room, 'humidity'))} per cento")
        if value(room, 'humidex') is not None:
            facts.append(f"humidex {spoken(value(room, 'humidex'))}")
        text = f"{ROOM_LABELS[room]}: " + (', '.join(facts) + '.' if facts else 'dati non disponibili.')
        thermostat = ROOM_THERMOSTAT.get(room)
        heating = thermostat_facts(r.get('thermostat', {}), thermostat, with_temperature=False) if thermostat else []
        if heating:
            text += ' Termostato: ' + ', '.join(heating) + '.'
    elif metric != 'all':
        listed = [f"{ROOM_LABELS[rm].lower()} {spoken(value(rm, metric))}" for rm in rooms if value(rm, metric) is not None]
        title = {'temperature': 'Temperature', 'humidity': 'Umidità', 'humidex': 'Humidex'}[metric]
        unit = {'temperature': ' gradi', 'humidity': ' per cento', 'humidex': ''}[metric]
        text = f"{title}: {', '.join(listed)}{unit}." if listed else f"{title}: dati non disponibili."
    else:
        parts = []
        for rm in rooms:
            facts = []
            if value(rm, 'temperature') is not None:
                facts.append(f"{spoken(value(rm, 'temperature'))} gradi")
            if value(rm, 'humidity') is not None:
                facts.append(f"{spoken(value(rm, 'humidity'))} per cento")
            parts.append(f"{ROOM_LABELS[rm]} " + ', '.join(facts) + '.' if facts else f"{ROOM_LABELS[rm]}: dati non disponibili.")
        text = ' '.join(parts)
    return text + ('' if r['connected'] else DISCONNECTED)


def light_state_sentence(light, value):
    phrase, plural = LIGHT_SPOKEN[light]
    state = ('accese' if value else 'spente') if plural else ('accesa' if value else 'spenta')
    return f"{capitalize(phrase)} {'sono' if plural else 'è'} {state}."


def lights_text(item):
    r = item['result']
    observations, commands = r['observations'], r.get('commands', {})
    if item['light'] == 'all':
        on = [LIGHT_NAMES[k] for k in LIGHT_NAMES if k in observations and observations[k]['value']]
        off = [LIGHT_NAMES[k] for k in LIGHT_NAMES if k in observations and not observations[k]['value']]
        missing = [LIGHT_NAMES[k] for k in LIGHT_NAMES if k not in observations]
        if not observations:
            text = "Lo stato delle luci non è disponibile."
        elif not on:
            text = "Tutte le luci risultano spente."
        else:
            text = f"Accese: {', '.join(on)}." + (f" Spente: {', '.join(off)}." if off else '')
        if missing and observations:
            text += f" Senza dato: {', '.join(missing)}."
    else:
        light = item['light']
        observation = observations.get(light)
        text = (light_state_sentence(light, observation['value']) if observation
                else f"{capitalize(LIGHT_SPOKEN[light][0])}: stato non disponibile.")
        command = commands.get(light)
        if command:
            who = "dell'utente" if command['actor'] == 'utente' else "di un'automazione"
            what = 'accensione' if command['requested_state'] else 'spegnimento'
            text += f" Ultimo comando registrato: {what} {who}, {moment_text(command['observed_at_ms'])}."
    return text + ('' if r['connected'] else DISCONNECTED)


def lights_notes(item):
    r = item['result']
    notes = []
    stage = {'routed': 'instradato dal router', 'dispatched': 'inoltrato al ramo di invio',
             'send_invoked': 'invocata la funzione di invio Broadlink (sendData)',
             'publisher_input': 'arrivato all’ingresso del publisher MQTT'}
    for light, command in r.get('commands', {}).items():
        notes.append(f"{capitalize(LIGHT_NAMES[light])}, ultimo comando documentato: "
                     f"{'accensione' if command['requested_state'] else 'spegnimento'}, autore {command['actor']}, "
                     f"origine {command['origin']}, {moment_text(command['observed_at_ms'])}: {stage[command['stage']]}.")
    if r.get('commands'):
        notes.append("L’autore riguarda il comando documentato, non la causa provata dello stato attuale; "
                     "non è uno storico completo né una conferma di esecuzione fisica.")
        if any(command['retained'] for command in r['commands'].values()):
            notes.append("Record di comando retained: evidenza conservata, non un nuovo evento.")
    elif item['light'] != 'all':
        notes.append("Autore non disponibile: nessun record Node-RED valido con la catalogazione corrente.")
    return notes + received_note(r['observations'].values())


def air_conditioner_text(r):
    observations = r['observations']
    if not observations:
        return "Lo stato del condizionatore non è disponibile."
    value = lambda metric: observations[metric]['value'] if metric in observations else None
    state, mode, setpoint, reason = (value('current_state'), value('air_mode'),
                                     value('temperature_set_c'), value('recorded_reason'))
    if state is None:
        text = "Lo stato del condizionatore non è disponibile."
    elif state.strip().upper() == 'OFF':
        text = "Il condizionatore è spento."
    else:
        details = [] if humanize(state) in ('acceso', 'on') else [humanize(state)]
        if mode and mode.strip().upper() != 'OFF':
            details.append(f"modalità {humanize(mode)}")
        try:
            degrees = float(str(setpoint).replace(',', '.'))
            if degrees > 0:
                details.append(f"{spoken(degrees)} gradi")
        except ValueError:
            pass
        text = f"Il condizionatore è acceso: {', '.join(details)}." if details else "Il condizionatore è acceso."
    if reason:
        text += f" Motivo: {humanize(reason)}."
    return text + ('' if r['connected'] else DISCONNECTED)


FLOW_SOURCES = (("solar", "Dal fotovoltaico"), ("battery", "Dalla batteria"), ("grid", "Dalla rete"))
FLOW_SINKS = {"home": "alla casa", "battery": "alla batteria", "grid": "in rete"}
STALE_NAMES = {"solar_power_w": "fotovoltaico", "battery_soc_percent": "livello della batteria",
               "battery_power_w": "batteria", "home_consumption_w": "consumo della casa", "grid_power_w": "rete"}
IDLE_W = 50


def energy_overview_text(item):
    r = item['result']
    value = lambda metric: r['observations'][metric]['value'] if metric in r['observations'] else None
    parts = []
    solar = value('solar_power_w')
    if solar is not None:
        parts.append(f"il fotovoltaico produce {power(solar)}" if solar >= IDLE_W else "il fotovoltaico è fermo")
    soc, battery = value('battery_soc_percent'), value('battery_power_w')
    if soc is not None or battery is not None:
        flow = ("si sta caricando" if battery is not None and battery <= -IDLE_W else
                "si sta scaricando" if battery is not None and battery >= IDLE_W else None)
        if soc is None:
            parts.append(f"la batteria {flow or 'è ferma'}")
        else:
            parts.append(f"la batteria è al {spoken(soc, 0)} per cento" + (f" e {flow}" if flow else ""))
    home = value('home_consumption_w')
    if home is not None:
        parts.append(f"la casa consuma {power(home)}")
    grid = value('grid_power_w')
    if grid is not None:
        parts.append(f"stai importando {power(grid)} dalla rete" if grid >= IDLE_W else
                     f"stai immettendo {power(grid)} in rete" if grid <= -IDLE_W else
                     "non stai importando dalla rete")
    sentences = [capitalize(', '.join(parts)) + '.'] if parts else ["I dati energetici attuali non sono disponibili."]
    if r['stale']:
        sentences.append("Non aggiornati da più di 3 minuti: " + join_it([STALE_NAMES[m] for m in r['stale']]) + ".")
    if item.get('detail') == 'flows':
        flows = r.get('flows')
        if flows is None:
            sentences.append("Flussi non calcolabili: mancano dati aggiornati.")
        else:
            for source, title in FLOW_SOURCES:
                listed = [f"{power(watts)} {FLOW_SINKS[name.split('_to_')[1]]}"
                          for name, watts in flows.items() if name.startswith(source + '_to_')]
                if listed:
                    sentences.append(f"{title}: {join_it(listed)}.")
    return ' '.join(sentences) + ('' if r['connected'] else DISCONNECTED)


def current_energy_text(item):
    r = item['result']
    obs = r['observation']
    label = LABELS.get(item['metric'], 'Dato')
    if obs is None:
        return f"{label}: dato non disponibile."
    value, metric = obs['value'], item['metric']
    if metric == 'solar_power_w':
        text = f"Il fotovoltaico produce {power(value)}."
    elif metric == 'home_consumption_w':
        text = f"La casa consuma {power(value)}."
    elif metric == 'grid_power_w':
        text = (f"Stai prelevando {power(value)} dalla rete." if value > 0 else
                f"Stai immettendo {power(value)} in rete." if value < 0 else "Nessuno scambio con la rete.")
    elif metric == 'battery_power_w':
        text = (f"La batteria si sta scaricando a {power(value)}." if value > 0 else
                f"La batteria si sta caricando a {power(value)}." if value < 0 else "La batteria è ferma.")
    else:
        text = f"La batteria è al {spoken(value, 0)} per cento."
    return text + ('' if r['connected'] else DISCONNECTED)


def energy_value(value, unit):
    return f"{number(value, 1)} {UNITS.get(unit, unit)}"


def describe(item):
    r = item['result']
    schema = r['schema']
    if is_action(r):
        return capitalize(command_action(r)) + '.'
    if schema == 'house_ai.rules_list.v1':
        return rules_tools.list_text(r)
    if schema == 'house_ai.heating_profile.v1':
        return heating_profile_tools.text(r)
    if schema == 'house_ai.rule_command.v1':
        return rules_tools.rejected_text(r)
    if schema in ('house_ai.fireplace_settings_command.v1', 'house_ai.fireplace_power_command.v1',
                  'house_ai.fireplace_level_command.v1'):
        return fireplace_command_text(r)
    if schema == 'house_ai.backend_log_evidence.v1':
        return log_text(r)
    if schema == 'house_ai.energy_comparison.v1':
        if r['value'] is None:
            return 'Confronto non calcolabile: servono dati completi per entrambi i periodi.'
        left = period_text(r['left_period']['start'], r['left_period']['end_exclusive'])
        right = period_text(r['right_period']['start'], r['right_period']['end_exclusive'])
        trend = 'in più' if r['value'] > 0 else 'in meno' if r['value'] < 0 else 'di differenza'
        text = (f"{LABELS[r['metric']]} {left}: {energy_value(abs(r['value']), r['unit'])} {trend} "
                f"rispetto al periodo {right}")
        if r['change_percent'] is not None:
            text += f", {'+' if r['change_percent'] > 0 else ''}{number(r['change_percent'], 1)} per cento"
        return text + '.'
    if schema == 'house_ai.energy_daily_extreme.v1':
        label = LABELS.get(r['metric'], item.get('id', 'Dato'))
        period = period_text(r['period']['start'], r['period']['end_exclusive'])
        if r['winner'] is None:
            return f"{label}: nessun giorno con dati completi {period}."
        kind = 'maggiore' if r['extremum'] == 'maximum' else 'minore'
        text = (f"Il giorno con {label.lower()} {kind} {period} è stato il {day_text(r['winner']['day'])}: "
                f"{energy_value(r['winner']['value'], r['unit'])}.")
        if r['absolute_winner'] is None and r['excluded_days']:
            text += " Alcuni giorni hanno dati incompleti: il risultato riguarda solo i giorni completi."
        return text
    if schema == 'house_ai.source_error.v1':
        return f"{LABELS.get(item.get('metric'), item.get('id', 'Dato'))}: sorgente non disponibile, riprova più tardi."
    if schema == 'house_ai.current_energy_result.v1':
        return current_energy_text(item)
    if schema == 'house_ai.current_energy_overview.v1':
        return energy_overview_text(item)
    if schema == 'house_ai.current_predictive_reserve.v1':
        return predictive_reserve_text(r)
    if schema == 'house_ai.current_thermostats.v1':
        return thermostats_text(item)
    if schema == 'house_ai.current_heating.v1':
        return heating_text(r)
    if schema == 'house_ai.current_environment.v1':
        return environment_text(item)
    if schema == 'house_ai.current_air_conditioner.v1':
        return air_conditioner_text(r)
    if schema == 'house_ai.current_lights.v1':
        return lights_text(item)
    label = LABELS.get(item.get('metric'), item.get('id', 'Dato'))
    period = period_text(r['period']['start'], r['period']['end_exclusive'])
    if r['value'] is None:
        return f"{label}: dati insufficienti {period}."
    text = f"{label} {period}: {energy_value(r['value'], r['unit'])}."
    if r['status'] != 'complete':
        text += f" Dati parziali: copertura {number(r['coverage_ratio'] * 100, 1)} per cento."
    return text


COMMAND_SCHEMAS = ('house_ai.light_command.v1', 'house_ai.light_scene_command.v1', 'house_ai.thermostat_command.v1',
                   'house_ai.fireplace_settings_command.v1', 'house_ai.fireplace_level_command.v1')


def is_action(r):
    return r['schema'] in COMMAND_SCHEMAS and r['status'] == 'command_ready'


def needs_confirmation(r):
    """Heating commands (thermostats, fireplace) wait for the user's confirmation in the app."""
    return r['status'] == 'confirmation_required'


def confirmation_actions(results):
    actions, fireplace = [], False
    for item in results:
        r = item['result']
        if not needs_confirmation(r):
            continue
        if r['schema'] == 'house_ai.rule_command.v1':
            actions.append(f"creo questa regola: {r['text']}" if r['verb'] == 'create'
                           else "cancello " + ("la regola «" + r['texts'][0] + "»" if len(r['texts']) == 1
                                               else f"{len(r['texts'])} regole"))
            continue
        if r['schema'] == 'house_ai.fireplace_power_command.v1':
            verb = 'accendo' if r['state'] == 'on' else 'spengo'
            actions.append(f"lo {verb}" if fireplace else f"{verb} il caminetto")
        else:
            actions.append(command_action(r))
        fireplace = fireplace or r['schema'].startswith('house_ai.fireplace_')
    return actions


def confirmation(results):
    """Prompt shown beside Conferma/Annulla and the sentence said once confirmed; None if nothing waits."""
    actions = confirmation_actions(results)
    if not actions:
        return None
    return {'prompt': f"{capitalize(join_it(actions))}. Confermi?", 'done': f"Ok, {join_it(actions)}."}


def fireplace_settings_action(r):
    mode, start, stop, auto = r['mode'], r['start_time'], r['stop_time'], r['auto_power']
    times = (f"dalle {start} alle {stop}" if start and stop else
             f"con avvio alle {start}" if start else f"con spegnimento alle {stop}" if stop else "")
    actions = []
    if mode:
        actions.append(f"metto il caminetto in modalità {mode}" + (f", {times}" if times else ""))
    elif times:
        actions.append(f"imposto gli orari del caminetto {times}")
    if auto is not None:
        actions.append(f"{'attivo' if auto else 'disattivo'} la potenza automatica del caminetto")
    return join_it(actions)


def fireplace_command_text(r):
    if r['schema'] == 'house_ai.fireplace_settings_command.v1':
        return (f"Non imposto gli orari del caminetto: l'avvio alle {r['start_time']} deve essere prima "
                f"dello spegnimento alle {r['stop_time']}.")
    if r['schema'] == 'house_ai.fireplace_level_command.v1':
        return fireplace_level_text(r)
    on = r['state'] == 'on'
    if r['status'] == 'unchanged':
        return f"Il caminetto risulta già {'acceso' if on else 'spento'}."
    if r['reason'] == 'mode_unknown':
        return "Non conosco la modalità del caminetto: non posso accenderlo o spegnerlo a mano."
    return (f"Il caminetto è in modalità {r['mode']}: per {'accenderlo' if on else 'spegnerlo'} a mano "
            "passa prima a Manuale.")


def fireplace_level_text(r):
    if r['status'] == 'unchanged':
        return f"Il caminetto è già al livello {r['level']}."
    if r['reason'] == 'mode_unknown':
        return "Non conosco la modalità del caminetto: non posso cambiarne il livello."
    if r['reason'] == 'off':
        return "Il caminetto è spento: il livello si cambia quando è acceso."
    text = f"Il caminetto è in modalità {r['mode']}: il livello si cambia solo in Manuale."
    if r['auto_power'] is True:
        text += " Con la potenza automatica attiva il livello lo decide la casa."
    elif r['auto_power'] is False:
        text += " La potenza automatica è disattivata."
    return text


def command_action(r):
    if r['schema'] == 'house_ai.fireplace_level_command.v1':
        return f"imposto il caminetto al livello {r['level']}"
    if r['schema'] == 'house_ai.fireplace_settings_command.v1':
        return fireplace_settings_action(r)
    if r['schema'] == 'house_ai.light_command.v1':
        return f"{'accendo' if r['state'] == 'on' else 'spengo'} {LIGHT_SPOKEN[r['light']][0]}"
    if r['schema'] == 'house_ai.light_scene_command.v1':
        return SCENE_ACTIONS[r['scene']]
    return f"imposto {THERMOSTAT_SPOKEN[r['thermostat']]} a {spoken(float(r['target_c']))} gradi"


def notes(item):
    """Technical details moved out of the spoken answer, for the diagnostics view."""
    r = item['result']
    schema = r['schema']
    if schema == 'house_ai.light_scene_command.v1':
        return [SCENE_DETAILS[r['scene']]]
    if schema == 'house_ai.fireplace_settings_command.v1' and r['status'] == 'confirmation_required':
        return (["Le modalità Manuale e Disattivato non usano gli orari."]
                if r['mode'] in ('Manuale', 'Disattivato') and (r['start_time'] or r['stop_time']) else [])
    if schema == 'house_ai.thermostat_command.v1':
        return ["Il setpoint resta valido fino al prossimo intervento del timer delle fasce orarie (cambio fascia, "
                "modifica dei valori in/fuori fascia o ripristino ogni 8 ore) o della modalità vacanza."]
    if schema == 'house_ai.current_lights.v1':
        return lights_notes(item)
    if schema == 'house_ai.current_thermostats.v1':
        return thermostats_notes(item)
    if schema == 'house_ai.current_heating.v1':
        return received_note(r['observations'].values())
    if schema == 'house_ai.current_environment.v1':
        return received_note(list(r['observations'].values()) + list(r.get('thermostat', {}).values()))
    if schema == 'house_ai.current_air_conditioner.v1':
        return received_note(r['observations'].values())
    if schema == 'house_ai.current_predictive_reserve.v1':
        return predictive_reserve_notes(r)
    if schema == 'house_ai.current_energy_result.v1':
        return received_note([r['observation']] if r['observation'] else [])
    if schema == 'house_ai.current_energy_overview.v1':
        return received_note(r['observations'].values())
    if schema == 'house_ai.backend_log_evidence.v1':
        extra = []
        if r['records']:
            key = 'timestamp_ms' if r['source'] == 'reserve_shadow' else 'timestamp'
            last = (r['records'][-1] if r['source'] == 'solar_forecast' else
                    max(r['records'], key=lambda rec: numeric(rec['record'], key) or 0))
            extra.append(f"Ultimo record restituito: riga {last['source']['line']}.")
        if r['source'] == 'solar_forecast':
            computed = r['records'][-1]['record'].get('elaborata_alle') if r['records'] else None
            try:  # SOLAR-FORECAST.1 (2026-10-11) records the computation time; older lines do not
                when = ("elaborata il " + moment_text(
                    int(datetime.fromisoformat(computed.replace('Z', '+00:00')).timestamp() * 1000)))
            except (AttributeError, TypeError, ValueError):
                when = "ora di elaborazione non presente"
            extra.append(f"È una previsione per il giorno indicato, non produzione misurata; {when}.")
        if r['source'] == 'climate':
            extra.append("È un evento registrato, non una conferma fisica né una spiegazione causale aggiuntiva.")
        return extra + ["I log non garantiscono copertura continua e non sostituiscono lo stato corrente."]
    if schema == 'house_ai.energy_comparison.v1':
        return ["I periodi non sono normalizzati per durata."]
    if schema == 'house_ai.energy_daily_extreme.v1':
        extra = [f"Giorni completi confrontati: {r['eligible_days']}; con copertura incompleta: {len(r['excluded_days'])}."]
        observed = r['observed_winner']
        if observed is not None:
            extra.append(f"Su tutti i dati disponibili il giorno osservato è il {day_text(observed['day'])}: "
                         f"{'almeno ' if observed['status'] != 'complete' else ''}{energy_value(observed['value'], r['unit'])}, "
                         f"copertura {number(observed['coverage_ratio'] * 100, 1)} per cento.")
        return extra + ["Stima da campioni EmonCMS, non misura fiscale."]
    if schema == 'house_ai.energy_history.v1':
        return [f"Copertura {number(r['coverage_ratio'] * 100, 1)} per cento.", "Stima da campioni EmonCMS, non misura fiscale."]
    return []


def compose(results):
    """One answer: immediate commands in a single "Ok, …" sentence, then each reading, then the
    heating commands waiting for confirmation (last, so the spoken question ends the answer).
    The spoken text never contains "confermo"/"conferma": the microphone opens right after the
    reading and must not hear the phone itself confirm (field test 16/10, "bagno a 21")."""
    actions = [command_action(item['result']) for item in results if is_action(item['result'])]
    texts = [describe(item) for item in results
             if not is_action(item['result']) and not needs_confirmation(item['result'])]
    if actions:
        texts.insert(0, f"Ok, {join_it(actions)}.")
    pending = confirmation(results)
    if pending:
        texts.append(pending['prompt'])
    return '\n\n'.join(texts)
