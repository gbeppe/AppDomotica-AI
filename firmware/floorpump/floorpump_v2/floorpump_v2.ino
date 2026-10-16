/*
  Pompa del pavimento - relè MQTT su ESP8266 (versione 2, ottobre 2026)

  Compatibile con la versione precedente (pompapavimento_switch): stessi topic e comandi, così la
  logica della pompa sul Node-RED di .15 non cambia.

  Scheda: ESP8266 con relè sul GPIO12 (attivo ALTO) e uscita 2 sul GPIO13, come la versione
  precedente (schema Sonoff Basic: sul GPIO13 può esserci il LED). OTA "floorPump".
  Librerie: PubSubClient 2.8, ArduinoJson 7.
  Credenziali: in secrets.h (vedi secrets.h.example), mai nel codice.

  Topic (base /esp8266/floorPump):
    /switch1/enable  "1" | "0"            relè della pompa (dal .15)
    /switch1/status  "close" | "open"     (uscita, retained) relè chiuso = pompa alimentata
    /switch2/enable  "1" | "0"            uscita 2, come prima
    /switch2/status  "close" | "open"     (uscita, retained)
    /hello           (uscita, retained)   stato JSON ogni 15 minuti e a ogni cambio
    /online          (uscita, retained)   "online" / "offline" (last will)

  Sicurezza (scelta dell'utente del 16/10/2026): se per 15 minuti non arriva nessun comando
  sul relè della pompa (.15, Wi-Fi o broker fuori servizio) la pompa si spegne da sola: con il
  puffer caldo caminetto e caldaia modulano da soli, una pompa accesa senza controllo
  consumerebbe gas e pellet inutilmente. Con la pompa abilitata il .15 ripete il comando ogni
  3 minuti. All'avvio il relè è aperto (pompa spenta) fino al primo comando.
*/

#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <ArduinoOTA.h>
#include <ArduinoJson.h>
#include "secrets.h"

// ---------------------------------------------------------------- configurazione

const char* OTA_HOSTNAME = "floorPump";
const char* FIRMWARE = "floorpump_v2";
const uint16_t MQTT_PORT = 1883;
const String BASE = "/esp8266/floorPump";
const String T_ENABLE1 = BASE + "/switch1/enable";
const String T_STATUS1 = BASE + "/switch1/status";
const String T_ENABLE2 = BASE + "/switch2/enable";
const String T_STATUS2 = BASE + "/switch2/status";
const String T_HELLO = BASE + "/hello";
const String T_ONLINE = BASE + "/online";

const uint8_t PIN_PUMP = 12;
const uint8_t PIN_OUT2 = 13;

const unsigned long COMMAND_TIMEOUT_MS = 15UL * 60UL * 1000UL;   // pompa spenta senza comandi
const unsigned long HELLO_EVERY_MS = 15UL * 60UL * 1000UL;
const unsigned long WIFI_RETRY_MS = 10000;
const unsigned long WIFI_RESTART_MS = 10UL * 60UL * 1000UL;      // riavvio se il Wi-Fi non torna
const unsigned long MQTT_RETRY_MS = 5000;

// ---------------------------------------------------------------- stato

WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);
WiFiServer telnetServer(8266);  // fa comparire la scheda tra le porte di rete dell'IDE

bool pumpOn = false;
bool out2On = false;
unsigned long lastPumpCommand = 0;   // millis dell'ultimo comando sul relè della pompa
bool everCommanded = false;
uint32_t watchdogStops = 0;
String lastStop = "";                // motivo dell'ultimo spegnimento non comandato
unsigned long lastHello = 0;
unsigned long lastWifiAttempt = 0;
unsigned long wifiLostAt = 0;
unsigned long lastMqttAttempt = 0;
bool helloPending = true;
String clientId;

// ---------------------------------------------------------------- uscite

void publishStatus(const String& topic, bool closed) {
  if (mqtt.connected()) mqtt.publish(topic.c_str(), closed ? "close" : "open", true);
}

void setPump(bool on) {
  pumpOn = on;
  digitalWrite(PIN_PUMP, on ? HIGH : LOW);
  publishStatus(T_STATUS1, on);
  helloPending = true;
}

void setOut2(bool on) {
  out2On = on;
  digitalWrite(PIN_OUT2, on ? HIGH : LOW);
  publishStatus(T_STATUS2, on);
  helloPending = true;
}

void publishHello() {
  if (!mqtt.connected()) return;
  JsonDocument doc;
  doc["Firmware"] = FIRMWARE;
  doc["WiFi: IP address"] = WiFi.localIP().toString();
  doc["WiFi: RSSI"] = WiFi.RSSI();
  doc["Pompa: accesa"] = pumpOn;
  doc["Uscita 2: accesa"] = out2On;
  doc["Ultimo comando s fa"] = everCommanded ? (long)((millis() - lastPumpCommand) / 1000UL) : -1;
  doc["Spegnimenti di sicurezza"] = watchdogStops;
  doc["Ultimo spegnimento di sicurezza"] = lastStop;
  doc["Uptime s"] = millis() / 1000UL;
  String out;
  serializeJson(doc, out);
  mqtt.publish(T_HELLO.c_str(), out.c_str(), true);
  lastHello = millis();
}

// ---------------------------------------------------------------- comandi

void onMessage(char* topic, byte* payload, unsigned int length) {
  if (length == 0) return;
  char c = (char)payload[0];
  String t = topic;
  if (t == T_ENABLE1 && (c == '1' || c == '0')) {
    lastPumpCommand = millis();
    everCommanded = true;
    if ((c == '1') != pumpOn) setPump(c == '1');
    else publishStatus(T_STATUS1, pumpOn);   // conferma anche i comandi ripetuti
  } else if (t == T_ENABLE2 && (c == '1' || c == '0')) {
    setOut2(c == '1');
  }
}

// Spegne la pompa se il .15 non la comanda più (Wi-Fi, broker o Node-RED fuori servizio).
void checkWatchdog() {
  if (!pumpOn) return;
  if (millis() - lastPumpCommand < COMMAND_TIMEOUT_MS) return;
  watchdogStops++;
  lastStop = "nessun comando da 15 minuti, uptime " + String(millis() / 1000UL) + " s";
  setPump(false);
}

// ---------------------------------------------------------------- rete

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
    mqtt.subscribe(T_ENABLE1.c_str());
    mqtt.subscribe(T_ENABLE2.c_str());
    publishStatus(T_STATUS1, pumpOn);
    publishStatus(T_STATUS2, out2On);
    helloPending = true;
  }
}

void maintainWifi() {
  unsigned long now = millis();
  if (WiFi.status() == WL_CONNECTED) {
    wifiLostAt = 0;
    return;
  }
  if (wifiLostAt == 0) wifiLostAt = now;
  if (now - wifiLostAt > WIFI_RESTART_MS) ESP.restart();   // il relè riparte aperto
  if (now - lastWifiAttempt < WIFI_RETRY_MS) return;
  lastWifiAttempt = now;
  WiFi.disconnect();
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

// ---------------------------------------------------------------- avvio e ciclo

void setup() {
  // relè aperti prima di tutto: pompa spenta fino al primo comando
  digitalWrite(PIN_PUMP, LOW);
  pinMode(PIN_PUMP, OUTPUT);
  digitalWrite(PIN_OUT2, LOW);
  pinMode(PIN_OUT2, OUTPUT);
  Serial.begin(115200);

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
#ifdef OTA_PASSWORD
  ArduinoOTA.setPassword(OTA_PASSWORD);
#endif
  ArduinoOTA.onStart([]() { digitalWrite(PIN_PUMP, LOW); });   // durante l'aggiornamento pompa spenta
  ArduinoOTA.begin();
  telnetServer.begin();
}

void loop() {
  ArduinoOTA.handle();
  maintainWifi();
  maintainMqtt();
  checkWatchdog();
  if (mqtt.connected() && (helloPending || millis() - lastHello >= HELLO_EVERY_MS)) {
    helloPending = false;
    publishHello();
  }
}
