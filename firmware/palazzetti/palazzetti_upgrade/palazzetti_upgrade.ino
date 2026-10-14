// Versione precedente (aprile 2026), caricata sulla scheda fino al 14/10/2026.
// Conservata solo per tornare indietro: vedi ../README.md. Unica modifica: credenziali in secrets.h.
/*

  Palazzetti Idro 45 remote MQTT ESP8266 based controller

  generic esp8266 module, 80MHz, DIO, 115200, 4M (3M SPIFFS), ck, Disabled, None on 192.168.1.22

  change PubSubClinet.h var MQTT_MAX_PACKET_SIZE to 512

*/

#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <EEPROM.h>
#include <ESP8266mDNS.h>
#include <WiFiUdp.h>
#include <ArduinoOTA.h>
#include <ArduinoJson.h>
#include "secrets.h"  // credenziali estratte il 14/10/2026 (stesso secrets.h di palazzetti_v2)

#define mqtt_server MQTT_HOST
#define mqtt_user MQTT_USER
#define mqtt_password MQTT_PASSWORD
#define wifi_ssid WIFI_SSID
#define wifi_password WIFI_PASSWORD
#define delayShortPush 200
#define delayLongPush 3500

String mqtt_client_id = "ESP8266-";
String mqtt_base_topic = "/esp8266/palazzetti";
String subscribeTopic = mqtt_base_topic + "/#";
String inTopic_powerset = "/power";
String inTopic_command = "/command";
String inTopic_status = "/getstatus";
String outTopic = "/hello";
String outTopicLog = "/logging/message";

uint8_t wifi_mac[6];
boolean acceso = false;

WiFiClient wifiClient;
PubSubClient client(wifiClient);

//Necesary to make Arduino Software autodetect OTA device
WiFiServer TelnetServer(8266);

String msg;


int relay_pin_on   = 14;
int relay_pin_off  = 12;
int relay_pin_giu  = 16;
int relay_pin_su   = 13;
int relay_pin_meno = 4;
int relay_pin_piu  = 5;

int powerset = 6;
int requested_power = 6;

String macToStr(const uint8_t* mac)
{
  String result;
  for (int i = 0; i < 6; ++i) {
    result += String(mac[i], 16);
    if (i < 5)
      result += '-';
  }
  return result;
}

void setup_wifi() {
  delay(10);
  WiFi.mode(WIFI_STA);
  WiFi.begin(wifi_ssid, wifi_password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
  }
  WiFi.macAddress(wifi_mac);
}

void simulate_push (int pin, int duration) {
  digitalWrite(pin, HIGH);
  delay(duration);
  digitalWrite(pin, LOW);
  // attesa per successive azioni
  delay(500);
}

void simulate_long_push (int pin) {
  simulate_push(pin, delayLongPush);
}

void simulate_short_push (int pin) {
  simulate_push(pin, delayShortPush);
}

void reset_menu() {
  // simulo di premere 5 volte il tasto off
  for (int cnt = 0; cnt < 5; cnt++) {
    simulate_short_push(relay_pin_off);
  }
  // attesa per successive azioni
  delay(500);
}

void publish_log (String logmessage) {
  JsonDocument doc;
  boolean retain;
  String msg;

  doc["Palazzetti Control: log"] = logmessage;
  serializeJson(doc, msg);
  client.publish((mqtt_base_topic + outTopicLog).c_str(), msg.c_str(), retain=true);
  delay(2000);
}

void publish_status () {
  boolean retain;
  JsonDocument doc;
  String msg;

  doc["WiFi: IP address"] = WiFi.localIP().toString();
  byte ar[6]; WiFi.macAddress(ar);
  char macAddr[18];
  sprintf(macAddr, "%2X:%2X:%2X:%2X:%2X:%2X", ar[0], ar[1], ar[2], ar[3], ar[4], ar[5]);
  macAddr[17] = '\0';
  doc["WiFi: MAC address"] = macAddr;
  long rssi = WiFi.RSSI();
  doc["WiFi: RSSI"] = rssi;
  doc["MQTT: Base Topic "] = mqtt_base_topic;
  doc["Stufa: Livello Potenza "] = powerset;
  doc["Stufa: acceso "] = acceso;

  serializeJson(doc, msg);
  client.publish((mqtt_base_topic + outTopic).c_str(), msg.c_str(), retain=true);
}

void callback(char* topic, byte* payload, unsigned int length) {
  payload[length] = '\0'; // Null terminator used to terminate the char array
  String message = (char*)payload;

/*
  if ( strcmp(topic, (mqtt_base_topic + outTopicLog).c_str()) ) {
    //String aa = topic;
    //String messagelog = "Message arrived on topic: [" + aa + "], " + "Payload: " + message;
    //publish_log(messagelog);

    // In order to republish this payload, a copy must be made
    // as the orignal payload buffer will be overwritten whilst
    // constructing the PUBLISH packet.

    // Allocate the correct amount of memory for the payload copy
    byte* payloadCopy = (byte*)malloc(length);
    // Copy the payload to the new buffer
    memcpy(payloadCopy,payload,length);
    payloadCopy[length] = '\0';
    String msgp = (char *)payloadCopy;
    char* topicCopy = (char*)malloc(strlen(topic)+1);
    memcpy(topicCopy, topic, strlen(topic));
    topicCopy[strlen(topic)] = '\0';
    String aa = topicCopy;
    String messagelog = "Message arrived on topic: [" + aa + "], " + "Payload: " + msgp;
    JsonDocument doc;
    boolean retain;
    String msg;

    doc["Palazzetti Control: log"] = messagelog;
    serializeJson(doc, msg);
    client.publish((mqtt_base_topic + outTopicLog).c_str(), msg.c_str(), retain=true);
    // Free the memory
    free(payloadCopy);
    free(topicCopy);
  }
*/
  if ( strcmp(topic, (mqtt_base_topic + inTopic_status).c_str()) == 0 ) {
    publish_status ();
  } else if ( strcmp(topic, (mqtt_base_topic + inTopic_powerset).c_str()) == 0 ) {
    if ( message == "6" ||
         message == "5" ||
         message == "4" ||
         message == "3" ||
         message == "2" ||
         message == "1" ) {
      powerset = atoi((char*)message.c_str());
    }
  } else if ( strcmp(topic, (mqtt_base_topic + inTopic_command).c_str()) == 0 ) {
    // ACCENSIONE
    if ( message == "on" ) {
      // mi metto nella pagina iniziale del menu
      reset_menu();
      // tengo premuto il tasto on per almeno 3 sec
      simulate_long_push(relay_pin_on);
      // seleziono la voce pellet
      simulate_short_push(relay_pin_meno);
      acceso = true;
    }
    // SPEGNIMENTO
    if ( message == "off" ) {
      // mi metto nella pagina iniziale del menu
      reset_menu();
      // tengo premuto il tasto off per almeno 3 sec
      simulate_long_push(relay_pin_off);
      // seleziono la voce spegnimento
      simulate_short_push(relay_pin_giu);
      acceso = false;
    }
    // regolazione della POTENZA
    if ( message == "6" ||
         message == "5" ||
         message == "4" ||
         message == "3" ||
         message == "2" ||
         message == "1" ) {
      requested_power = atoi((char*)message.c_str());
      if (requested_power != powerset) {
        // mi metto nella pagina iniziale del menu
        reset_menu();
        // selezione  "menu lavoro"
        simulate_short_push(relay_pin_on);
        // selezione  "menu potenza lavoro"
        simulate_short_push(relay_pin_on);
        // regolo la potenza
        if (requested_power > powerset) {
          do {
            simulate_short_push(relay_pin_piu); powerset++;
          } while (powerset < requested_power);
        }
        if (requested_power < powerset) {
          do {
            simulate_short_push(relay_pin_meno); powerset--;
          } while (powerset > requested_power);
        }
        // conferma
        simulate_short_push(relay_pin_on);
        // esci "menu potenza lavoro"
        simulate_short_push(relay_pin_off);
        // esci "menu lavoro"
        simulate_short_push(relay_pin_off);
      }
    }
  }
//  publish_status ();
}

void reconnect() {
  // Loop until we're reconnected
  while (!client.connected()) {
    // Attempt to connect
    if (client.connect((char*)mqtt_client_id.c_str(), mqtt_user, mqtt_password)) {
      // Once connected,subscribe
      delay(5000);
      client.subscribe((char*)subscribeTopic.c_str());
      delay(5000);
      publish_status ();
    } else {
      // Wait 5 seconds before retrying
      delay(5000);
    }
  }
}

void setup() {
  pinMode(relay_pin_on, OUTPUT);
  pinMode(relay_pin_off, OUTPUT);
  pinMode(relay_pin_giu, OUTPUT);
  pinMode(relay_pin_su, OUTPUT);
  pinMode(relay_pin_meno, OUTPUT);
  pinMode(relay_pin_piu, OUTPUT);

  Serial.begin(115200);

  delay(10000);
  setup_wifi();                   // Connect to wifi
  delay(2000);
  TelnetServer.begin(); //Necesary to make Arduino Software autodetect OTA device

  mqtt_client_id += macToStr(wifi_mac);

  client.setServer(mqtt_server, 1883);
  client.setCallback(callback);

  ArduinoOTA.setHostname("palazzetti");
  ArduinoOTA.begin();
}

void loop() {
  ArduinoOTA.handle();
  if (!client.connected()) {
    reconnect();
  }
  client.loop();
}