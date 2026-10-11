// IRRIGATION.1: app command -> OpenSprinkler HTTP API. Only known zones/programs, 1-60 min.
// Outputs: 1 = HTTP request, 2 = result for the app (zara/interface/irrigation/last_command/stat).
const BASE = "http://192.168.1.3:9003";
const MAX_MINUTES = 60;
const match = /^zara\/interface\/irrigation\/(.+)\/cmd$/.exec(msg.topic || "");
if (!match) return null;
const command = match[1];
const path = command.split("/");
const value = String(msg.payload).trim().toLowerCase();
const state = flow.get("irrigation_state");

function reject(reason) {
    node.warn("Irrigazione: comando " + command + " scartato (" + reason + ")");
    return [null, {topic: "zara/interface/irrigation/last_command/stat", retain: false,
                   payload: {ok: false, command: command, reason: reason, at_ms: Date.now()}}];
}

const pw = flow.get("os_pw");
if (!pw) return reject("password OpenSprinkler non caricata");
if (!state) return reject("stato OpenSprinkler non ancora letto");

let query;
if (path[0] === "zone" && path.length === 3) {
    const id = Number(path[1]);
    if (!state.zones.some(z => z.id === id)) return reject("zona sconosciuta");
    if (path[2] === "start") {
        const minutes = Number(value);
        if (!Number.isInteger(minutes) || minutes < 1 || minutes > MAX_MINUTES) return reject("minuti non validi");
        query = "cm?sid=" + (id - 1) + "&en=1&t=" + minutes * 60;
    } else if (path[2] === "stop") {
        query = "cm?sid=" + (id - 1) + "&en=0";
    } else return reject("azione sconosciuta");
} else if (path[0] === "program" && path.length === 3 && path[2] === "start") {
    const id = Number(path[1]);
    if (!state.programs.some(p => p.id === id)) return reject("programma sconosciuto");
    if (value !== "true") return reject("valore non valido");
    query = "mp?pid=" + (id - 1) + "&uwt=0";
} else if (command === "all/stop") {
    if (value !== "true") return reject("valore non valido");
    query = "cv?rsn=1";
} else if (command === "controller/enabled") {
    if (value !== "true" && value !== "false") return reject("valore non valido");
    query = "cv?en=" + (value === "true" ? 1 : 0);
} else return reject("comando sconosciuto");

return [{url: BASE + "/" + query + "&pw=" + pw, kind: "command", command: command}, null];
