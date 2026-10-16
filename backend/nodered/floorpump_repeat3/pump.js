// FLOORPUMP-REPEAT.3 (2026-10-16): the floor pump depends only on "enabled" and the puffer top
// temperature (on above 35 °C, off below 30 °C, the last command repeated in between, so the
// 15-minute safety stop of floorpump_v2 does not stop the pump early). The fireplace times are
// no longer read: they were only used by code that had been commented out.
// As before, the fireplace mode is recorded only while the pump is enabled: the old thermostat
// timers (tab TASMOTAs) read it, and recording it always would wake them up.
var pompapavimentoenabled = global.get("pompapavimentoenabled", 'file');

if (!pompapavimentoenabled) {
    msg.payload = 0;
    context.set("lastPump", 0);
    return msg;
}

if (msg.topic == "zara/domotics/palazzetti/modalita") {
    global.set("palazzettiModalita", msg.payload);
} else if (msg.topic == "emon/resolDL2/sensor3") {
    global.set("tempPufferSup", msg.payload);
}

var tempPufferSup = global.get("tempPufferSup");

if (tempPufferSup > 35) msg.payload = 1;
else if (tempPufferSup < 30) msg.payload = 0;
else if (context.get("lastPump") !== undefined) msg.payload = context.get("lastPump");
else return null;

context.set("lastPump", msg.payload);
return msg;
