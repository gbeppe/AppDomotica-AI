/*
  It connects to an MQTT server then:
  - on 0 switches off relay
  - on 1 switches on relay
  - on 2 switches the state of the relay

  - sends 0 on off relay
  - sends 1 on on relay

  It will reconnect to the server if the connection is lost using a blocking
  reconnect function. See the 'mqtt_reconnect_nonblocking' example for how to
  achieve the same result without blocking the main loop.

  The current state is stored in EEPROM and restored on bootup

*/

/*
   manual OTA

  giuseppe@ubuntu-Inspiron-7520:$ python ~/.arduino15/packages/esp8266/hardware/esp8266/2.3.0/tools/espota.py
  -d -i 192.168.1.21 -I 192.168.1.184 -p 8266
  -f /home/giuseppe/Desktop/arduino-dev/pompapavimento_switch/pompapavimento_switch.ino.generic.bin

*/


#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <EEPROM.h>
#include <ESP8266mDNS.h>
#include <WiFiUdp.h>
#include <ArduinoOTA.h>

#include "secrets.h"  // credenziali spostate fuori dal codice (16/10/2026)
#define mqtt_server MQTT_HOST
#define mqtt_user MQTT_USER
#define mqtt_password MQTT_PASSWORD
#define wifi_ssid WIFI_SSID
#define wifi_password WIFI_PASSWORD

#define inTopic1  "/switch1/enable"
#define outTopic1 "/switch1/status"
#define inTopic2  "/switch2/enable"
#define outTopic2 "/switch2/status"

String mqtt_client_id = "ESP8266-";
String mqtt_base_topic = "/esp8266/";


WiFiClient wifiClient;
PubSubClient client(wifiClient);

//Necesary to make Arduino Software autodetect OTA device
WiFiServer TelnetServer(8266);

String msg;

int relay_pin1 = 12;
int relay_pin2 = 13;
uint8_t wifi_mac[6];

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
  // We start by connecting to a WiFi network
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(wifi_ssid);
  WiFi.mode(WIFI_STA);
  WiFi.begin(wifi_ssid, wifi_password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.println("WiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());

  WiFi.macAddress(wifi_mac);
}

void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("] ");
  for (int i = 0; i < length; i++) {
    Serial.print((char)payload[i]);
  }
  Serial.println();

  // Switch on the LED for 0,2 sec (impulse) if an 1 was received as first character
  if ( strcmp(topic, (char*)(mqtt_base_topic + inTopic1).c_str()) == 0 ) {
    if ( (char)payload[0] == '1' ) {
      digitalWrite(relay_pin1, HIGH);
      Serial.println("relay_pin -> switched to HIGH");

      msg = String("close");
      if (client.publish((mqtt_base_topic + outTopic1).c_str(), (char*) msg.c_str())) {
        Serial.println("Publish ok");
      } else {
        Serial.println("Publish failed");
      }
    } else if ( (char)payload[0] == '0' ) {
      digitalWrite(relay_pin1, LOW);
      Serial.println("relay_pin -> switched to LOW");

      msg = String("open");
      if (client.publish((mqtt_base_topic + outTopic1).c_str(), (char*) msg.c_str())) {
        Serial.println("Publish ok");
      } else {
        Serial.println("Publish failed");
      }
    }
  }
  if (strcmp(topic, (char*)(mqtt_base_topic + inTopic2).c_str()) == 0) {
    if ( (char)payload[0] == '1' ) {
      digitalWrite(relay_pin2, HIGH);  // impulse
      Serial.println("relay_pin -> switched to HIGH");

      msg = String("close");
      if (client.publish((mqtt_base_topic + outTopic2).c_str(), (char*) msg.c_str())) {
        Serial.println("Publish ok");
      } else {
        Serial.println("Publish failed");
      }
    } else if ( (char)payload[0] == '0' ) {
      digitalWrite(relay_pin2, LOW);  // impulse
      Serial.println("relay_pin -> switched to LOW");

      msg = String("open");
      if (client.publish((mqtt_base_topic + outTopic2).c_str(), (char*) msg.c_str())) {
        Serial.println("Publish ok");
      } else {
        Serial.println("Publish failed");
      }
    }
  }
}

void reconnect() {
  // Loop until we're reconnected
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    // Attempt to connect

    if (client.connect((char*)mqtt_client_id.c_str(), mqtt_user, mqtt_password)) {
      Serial.println("connected");
      // Once connected,subscribe
      String subscribeTopic = mqtt_base_topic + "/#";
      client.subscribe((char*)subscribeTopic.c_str());
      Serial.println(" subcribed to : " + subscribeTopic);
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      // Wait 5 seconds before retrying
      delay(5000);
    }
  }
}

void setup() {
  pinMode(relay_pin1, OUTPUT);
  pinMode(relay_pin2, OUTPUT);

  Serial.begin(9600);

  setup_wifi();                   // Connect to wifi

  delay(9000);

  TelnetServer.begin(); //Necesary to make Arduino Software autodetect OTA device

  Serial.println("Configuring MQTT server...");
  mqtt_client_id += macToStr(wifi_mac);
  mqtt_base_topic += "floorPump";
  client.setServer(mqtt_server, 1883);
  Serial.printf("   Server IP: %s\r\n", mqtt_server);
  Serial.printf("   Username:  %s\r\n", mqtt_user);
  Serial.println("   Cliend Id: " + mqtt_client_id);
  Serial.println("   Base topic: " + mqtt_base_topic);
  Serial.println("   MQTT configured!");

  client.setCallback(callback);

  ArduinoOTA.setHostname("floorPump");
  ArduinoOTA.begin();
}

void loop() {

  ArduinoOTA.handle();

  if (!client.connected()) {
    reconnect();
  }
  client.loop();
}

