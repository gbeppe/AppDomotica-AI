/*
  Palazzetti Idro 45 - controller MQTT su ESP8266 (versione 2, ottobre 2026)

  Simula le pressioni dei tasti del pannello con 6 relè (attivi ALTI).
  Compatibile con la versione precedente (palazzetti_upgrade): stessi topic e comandi.

  Scheda: Generic ESP8266 Module, 80 MHz, DIO, 4M (3M SPIFFS), reset ck; OTA "palazzetti" su 192.168.1.22.
  Librerie: PubSubClient 2.8, ArduinoJson 7.
  Credenziali: in secrets.h (vedi secrets.h.example), mai nel codice.

  Topic (base /esp8266/palazzetti):
    /command    "on" | "off" | "1".."6"     comandi al caminetto
    /power      "1".."6"                    riallinea il livello presunto senza premere tasti
    /getstatus  qualunque                   ripubblica lo stato
    /hello      (uscita, retained)          stato JSON (stesse chiavi della versione precedente + nuove)
    /online     (uscita, retained)          "online" / "offline" (last will)

  Il caminetto non comunica il proprio stato: acceso e livello sono PRESUNTI dai comandi inviati.
  Il livello presunto sopravvive ai riavvii (EEPROM) e si corregge con /power.

  /command è RETAINED sul broker di .15: alla (ri)connessione il broker reinvia l'ultimo comando.
  I comandi ricevuti nei primi secondi dopo la sottoscrizione NON premono tasti: servono solo a
  riallineare lo stato presunto (la versione precedente li rieseguiva a ogni riconnessione).
*/

#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <EEPROM.h>
#include <ArduinoOTA.h>
#include <ArduinoJson.h>
#include "secrets.h"

// ---------------------------------------------------------------- configurazione

const char* OTA_HOSTNAME = "palazzetti";
const uint16_t MQTT_PORT = 1883;
const String BASE = "/esp8266/palazzetti";
const String T_COMMAND = BASE + "/command";
const String T_POWER = BASE + "/power";
const String T_GETSTATUS = BASE + "/getstatus";
const String T_STATUS = BASE + "/hello";
const String T_ONLINE = BASE + "/online";

const uint8_t PIN_ON = 14;
const uint8_t PIN_OFF = 12;
const uint8_t PIN_GIU = 16;
const uint8_t PIN_SU = 13;
const uint8_t PIN_MENO = 4;
const uint8_t PIN_PIU = 5;
const uint8_t ALL_PINS[] = {PIN_ON, PIN_OFF, PIN_GIU, PIN_SU, PIN_MENO, PIN_PIU};

const uint16_t SHORT_PUSH_MS = 200;
const uint16_t LONG_PUSH_MS = 3500;
const uint16_t GAP_MS = 500;            // pausa dopo ogni pressione (come la versione precedente)
const uint16_t MENU_SETTLE_MS = 500;    // pausa extra dopo il ritorno al menu iniziale

const uint8_t MIN_LEVEL = 1;
const uint8_t MAX_LEVEL = 6;

// true = prima di ogni cambio livello porta il pannello al minimo (5 x "meno") e poi sale al valore
// richiesto. NON USARE con questo caminetto: premendo "meno" a 1 il pannello riparte da 6
// (verificato il 14/10/2026), quindi non esiste un punto di partenza sicuro.
const bool LEVEL_HOMING = false;

const unsigned long WIFI_RETRY_MS = 10000;
const unsigned long MQTT_RETRY_MS = 5000;
const unsigned long RETAINED_WINDOW_MS = 3000;  // finestra in cui un /command è il retained del broker

// ---------------------------------------------------------------- stato

WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);
WiFiServer telnetServer(8266);  // fa comparire la scheda tra le porte di rete dell'IDE

uint8_t level = 6;          // livello presunto
bool lit = false;           // acceso presunto
String lastCommand = "";
String clientId;

// Una pressione: pin, durata, pausa successiva, variazione del livello presunto a fine pressione.
struct Step {
  uint8_t pin;
  uint16_t pushMs;
  uint16_t gapMs;
  int8_t levelDelta;
};

const uint8_t STEP_CAPACITY = 32;
Step steps[STEP_CAPACITY];
uint8_t stepHead = 0, stepCount = 0;

enum StepPhase { IDLE, PRESSING, WAITING };
StepPhase phase = IDLE;
unsigned long phaseStart = 0;
Step current;
bool publishWhenIdle = false;

// Comandi in attesa (eseguiti uno alla volta, a sequenza precedente finita).
const uint8_t CMD_CAPACITY = 4;
String pending[CMD_CAPACITY];
uint8_t pendingCount = 0;

unsigned long lastWifiAttempt = 0;
unsigned long lastMqttAttempt = 0;
unsigned long subscribedAt = 0;

// ---------------------------------------------------------------- EEPROM (livello presunto)

const int EEPROM_SIZE = 8;
const uint8_t EEPROM_MAGIC = 0xA7;

void loadLevel() {
  EEPROM.begin(EEPROM_SIZE);
  if (EEPROM.read(0) == EEPROM_MAGIC) {
    uint8_t saved = EEPROM.read(1);
    if (saved >= MIN_LEVEL && saved <= MAX_LEVEL) level = saved;
  }
}

void saveLevel() {
  if (EEPROM.read(0) == EEPROM_MAGIC && EEPROM.read(1) == level) return;
  EEPROM.write(0, EEPROM_MAGIC);
  EEPROM.write(1, level);
  EEPROM.commit();
}

// ---------------------------------------------------------------- coda delle pressioni

bool enqueueStep(uint8_t pin, uint16_t pushMs, uint16_t gapMs = GAP_MS, int8_t levelDelta = 0) {
  if (stepCount >= STEP_CAPACITY) return false;
  steps[(stepHead + stepCount) % STEP_CAPACITY] = {pin, pushMs, gapMs, levelDelta};
  stepCount++;
  return true;
}

void shortPush(uint8_t pin, int8_t levelDelta = 0) { enqueueStep(pin, SHORT_PUSH_MS, GAP_MS, levelDelta); }
void longPush(uint8_t pin) { enqueueStep(pin, LONG_PUSH_MS); }

void resetMenu() {
  // 5 pressioni di "off" riportano il pannello alla pagina iniziale
  for (uint8_t i = 0; i < 5; i++) enqueueStep(PIN_OFF, SHORT_PUSH_MS, i == 4 ? GAP_MS + MENU_SETTLE_MS : GAP_MS);
}

void runSteps() {
  unsigned long now = millis();
  if (phase == IDLE) {
    if (stepCount == 0) return;
    current = steps[stepHead];
    stepHead = (stepHead + 1) % STEP_CAPACITY;
    stepCount--;
    digitalWrite(current.pin, HIGH);
    phase = PRESSING;
    phaseStart = now;
  } else if (phase == PRESSING) {
    if (now - phaseStart < current.pushMs) return;
    digitalWrite(current.pin, LOW);
    if (current.levelDelta != 0) {
      int next = (int)level + current.levelDelta;
      level = (uint8_t)constrain(next, MIN_LEVEL, MAX_LEVEL);
    }
    phase = WAITING;
    phaseStart = now;
  } else if (phase == WAITING) {
    if (now - phaseStart < current.gapMs) return;
    phase = IDLE;
    if (stepCount == 0) {
      saveLevel();
      publishWhenIdle = true;
    }
  }
}

bool busy() { return phase != IDLE || stepCount > 0; }

// ---------------------------------------------------------------- comandi

int parseLevel(const String& s) {
  if (s.length() == 1 && s[0] >= '0' + MIN_LEVEL && s[0] <= '0' + MAX_LEVEL) return s[0] - '0';
  return -1;
}

void planCommand(const String& cmd) {
  lastCommand = cmd;
  if (cmd == "on") {
    resetMenu();
    longPush(PIN_ON);       // on tenuto premuto
    shortPush(PIN_MENO);    // voce "pellet"
    lit = true;
    return;
  }
  if (cmd == "off") {
    resetMenu();
    longPush(PIN_OFF);      // off tenuto premuto
    shortPush(PIN_GIU);     // voce "spegnimento"
    lit = false;
    return;
  }
  int target = parseLevel(cmd);
  if (target < 0) return;
  if (!LEVEL_HOMING && target == level) {
    publishWhenIdle = true;
    return;
  }
  resetMenu();
  shortPush(PIN_ON);        // "menu lavoro"
  shortPush(PIN_ON);        // "menu potenza lavoro"
  int from = level;
  if (LEVEL_HOMING) {
    for (int i = MIN_LEVEL; i < MAX_LEVEL; i++) shortPush(PIN_MENO, -1);
    from = MIN_LEVEL;
  }
  for (int l = from; l < target; l++) shortPush(PIN_PIU, +1);
  for (int l = from; l > target; l--) shortPush(PIN_MENO, -1);
  shortPush(PIN_ON);        // conferma
  shortPush(PIN_OFF);       // esci "menu potenza lavoro"
  shortPush(PIN_OFF);       // esci "menu lavoro"
}

void queueCommand(const String& cmd) {
  bool isLevel = parseLevel(cmd) > 0;
  if (cmd != "on" && cmd != "off" && !isLevel) return;
  if (isLevel) {
    // un livello in attesa viene sostituito dal più recente
    for (uint8_t i = 0; i < pendingCount; i++) {
      if (parseLevel(pending[i]) > 0) { pending[i] = cmd; return; }
    }
  }
  if (pendingCount >= CMD_CAPACITY) return;  // coda piena: comando scartato
  pending[pendingCount++] = cmd;
}

void runCommands() {
  if (busy() || pendingCount == 0) return;
  String cmd = pending[0];
  for (uint8_t i = 1; i < pendingCount; i++) pending[i - 1] = pending[i];
  pendingCount--;
  planCommand(cmd);
}

// ---------------------------------------------------------------- MQTT

void publishStatus() {
  if (!mqtt.connected()) return;
  JsonDocument doc;
  doc["WiFi: IP address"] = WiFi.localIP().toString();
  doc["WiFi: MAC address"] = WiFi.macAddress();
  doc["WiFi: RSSI"] = WiFi.RSSI();
  doc["MQTT: Base Topic "] = BASE;          // chiavi identiche alla versione precedente
  doc["Stufa: Livello Potenza "] = level;
  doc["Stufa: acceso "] = lit;
  doc["Stufa: ultimo comando"] = lastCommand;
  doc["Stufa: in esecuzione"] = busy();
  doc["Firmware"] = "palazzetti_v2";
  doc["Uptime s"] = millis() / 1000;
  String out;
  serializeJson(doc, out);
  mqtt.publish(T_STATUS.c_str(), out.c_str(), true);
}

// Ultimo comando conservato dal broker: già eseguito in passato, aggiorna solo lo stato presunto.
void syncFromRetained(const String& cmd) {
  int l = parseLevel(cmd);
  if (l > 0) {
    level = l;
    saveLevel();
  } else if (cmd == "on") {
    lit = true;
  } else if (cmd == "off") {
    lit = false;
  } else {
    return;
  }
  lastCommand = cmd + " (retained)";
  publishWhenIdle = true;
}

void onMessage(char* topic, byte* payload, unsigned int length) {
  String message;
  message.reserve(length);
  for (unsigned int i = 0; i < length; i++) message += (char)payload[i];
  message.trim();
  String t = topic;

  if (t == T_COMMAND) {
    if (millis() - subscribedAt < RETAINED_WINDOW_MS) {
      syncFromRetained(message);
    } else {
      queueCommand(message);
    }
  } else if (t == T_POWER) {
    int l = parseLevel(message);
    if (l > 0) {
      level = l;
      saveLevel();
      publishWhenIdle = true;
    }
  } else if (t == T_GETSTATUS) {
    publishWhenIdle = true;
  }
}

void maintainMqtt() {
  if (WiFi.status() != WL_CONNECTED) return;
  if (mqtt.connected()) {
    mqtt.loop();
    return;
  }
  unsigned long now = millis();
  if (lastMqttAttempt != 0 && now - lastMqttAttempt < MQTT_RETRY_MS) return;
  lastMqttAttempt = now;
  if (mqtt.connect(clientId.c_str(), MQTT_USER, MQTT_PASSWORD, T_ONLINE.c_str(), 0, true, "offline")) {
    mqtt.publish(T_ONLINE.c_str(), "online", true);
    subscribedAt = millis();
    mqtt.subscribe(T_COMMAND.c_str());
    mqtt.subscribe(T_POWER.c_str());
    mqtt.subscribe(T_GETSTATUS.c_str());
    publishWhenIdle = true;
  }
}

void maintainWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  unsigned long now = millis();
  if (now - lastWifiAttempt < WIFI_RETRY_MS) return;
  lastWifiAttempt = now;
  WiFi.disconnect();
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

// ---------------------------------------------------------------- avvio e ciclo

void setup() {
  // relè a riposo prima di tutto, per non premere tasti all'avvio
  for (uint8_t pin : ALL_PINS) {
    digitalWrite(pin, LOW);
    pinMode(pin, OUTPUT);
    digitalWrite(pin, LOW);
  }
  Serial.begin(115200);
  loadLevel();

  WiFi.persistent(false);
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.hostname(OTA_HOSTNAME);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  lastWifiAttempt = millis();

  clientId = "ESP8266-" + WiFi.macAddress();
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setCallback(onMessage);
  mqtt.setBufferSize(512);
  mqtt.setKeepAlive(30);

  ArduinoOTA.setHostname(OTA_HOSTNAME);
  ArduinoOTA.onStart([]() {
    // durante l'aggiornamento nessun relè resta premuto
    for (uint8_t pin : ALL_PINS) digitalWrite(pin, LOW);
    stepCount = 0;
    phase = IDLE;
    pendingCount = 0;
  });
  ArduinoOTA.begin();
  telnetServer.begin();
}

void loop() {
  ArduinoOTA.handle();
  maintainWifi();
  maintainMqtt();
  runSteps();
  runCommands();
  if (publishWhenIdle && !busy()) {
    publishWhenIdle = false;
    publishStatus();
  }
}
