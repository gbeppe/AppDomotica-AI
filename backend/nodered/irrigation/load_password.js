// IRRIGATION.1: OpenSprinkler password hash (MD5, 32 hex) read from
// /home/pi/.config/domopi/opensprinkler.md5; kept only in flow context, never logged.
const value = String(msg.payload || "").trim().toLowerCase();
if (/^[0-9a-f]{32}$/.test(value)) {
    flow.set("os_pw", value);
    node.status({fill: "green", shape: "dot", text: "password caricata"});
} else {
    node.status({fill: "red", shape: "ring", text: "password mancante o non valida"});
    node.warn("Irrigazione: file della password OpenSprinkler mancante o non valido");
}
return null;
