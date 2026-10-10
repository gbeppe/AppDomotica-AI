let topic = msg.topic;
let payload = String(msg.payload).toLowerCase();
let parts = topic.split('/');
let device = parts[parts.length - 1];

// 1. Gestione Anti-Loop (RBE in memoria per singolo topic)
let lastStates = context.get("lastStates") || {};
if (lastStates[topic] === payload) {
    return null; // Scarta il messaggio se lo stato non è cambiato, evitando loop infiniti
}
lastStates[topic] = payload;
context.set("lastStates", lastStates);

// Validazione payload
if (payload !== "on" && payload !== "off") {
    return null;
}

// 23 output slots (0-22).
// Output 0 is intentionally left without downstream MQTT wiring.
// Remaining outputs route the supported lights, pool devices and gates.
let outputs = new Array(24).fill(null);

// Slot 0: Messaggio di Eco MQTT (da collegare al nodo mqtt out per l'app/router)
outputs[0] = {
    topic: topic,
    payload: payload,
    retain: true
};

// Tabella di associazione tra dispositivo/stato e numero di uscita del nodo function
let routingTable = {
    "shelflamp":    { on: 1,  off: 2  },
    "livinglamp":   { on: 3,  off: 4  },
    "tvlamp":       { on: 5,  off: 6  },
    "readinglight": { on: 7,  off: 8  },
    "prolunga":     { on: 9,  off: 10 },
    "poolpump":     { on: 11, off: 12 },
    "poolskimmer":  { on: 13, off: 14 },
    "poolfloor":    { on: 15, off: 16 },
    "poollight":    { on: 17, off: 18 },
    "gate_1":       { on: 19, off: 20 },
    "gate_2":       { on: 21, off: 22 }
};

// 3. Instradamento dinamico verso l'uscita corretta
if (routingTable[device] && routingTable[device][payload] !== undefined) {
    let targetIndex = routingTable[device][payload];
    // This event attests dispatch selection after the existing RBE, not IR success.
    const c = msg._semantic_command;
    if (c && c.schema === 'domopi.semantic_command.v1' &&
        c.actor === 'utente' && c.stage === 'routed' &&
        c.routed_topic === topic && c.requested_state === (payload === 'on') &&
        typeof c.command_id === 'string') {
        const event = {...c, schema:'domopi.semantic_event.v1',
            event_id:c.command_id + ':dispatch', kind:'user_command_dispatched',
            stage:'dispatched', dispatched_at_ms:Date.now(),
            execution_confirmed:false, physical_confirmation:false};
        outputs[23] = [
            {topic:'zara/interface/semantic/v1/dispatched_commands/' + c.entity_id + '/stat',
             payload:JSON.stringify({...event,schema:'domopi.semantic_command.v1'}),qos:1,retain:true},
            {topic:'zara/interface/semantic/v1/events',payload:JSON.stringify(event),qos:1,retain:false}
        ];
    }
    outputs[targetIndex] = {
        topic: topic,
        payload: payload
    };
}

// Carry provenance only along the verified TV path.
if (device === 'tvlamp' && outputs[23]) {
    outputs[routingTable[device][payload]]._semantic_command = JSON.parse(outputs[23][0].payload);
}
return outputs;