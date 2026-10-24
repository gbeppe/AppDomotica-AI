// TESLA-STATUS.1: una GET per ogni endpoint del gateway, con il cookie del flusso Tesla principale
const cookie = flow.get("TeslaCookie");
if (!cookie) {
    node.status({fill: "yellow", shape: "ring", text: "nessun cookie, riprovo al prossimo giro"});
    return null;
}
const base = "https://192.168.1.46";
const endpoints = {
    operation: "/api/operation",
    grid: "/api/system_status/grid_status",
    sitemaster: "/api/sitemaster",
    system: "/api/system_status",
    faults: "/api/system_status/grid_faults",
    update: "/api/system/update/status"
};
const out = Object.keys(endpoints).map(k => ({topic: k, url: base + endpoints[k], headers: {Cookie: cookie}}));
node.status({fill: "blue", shape: "dot", text: "richieste " + new Date().toLocaleTimeString()});
return [out];
