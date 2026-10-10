"""Bounded Node-RED evidence transport. Never infer actors from state changes."""
STAGES = {"routed": "routed_commands", "dispatched": "dispatched_commands",
          "send_invoked": "rm_receipts", "publisher_input": "publisher_inputs"}
KEYS = {"schema", "entity_id", "command_id", "actor", "actor_policy", "origin",
        "attribution", "requested_state", "stage", "observed_at_ms", "received_at_ms",
        "retained", "source_topic", "physical_confirmation", "execution_confirmed",
        "publisher_authenticated"}


def validate_commands(commands, topics):
    if not isinstance(commands, dict) or set(commands) - set(topics):
        raise ValueError("Invalid semantic lights")
    clean = {}
    for light, record in commands.items():
        if not isinstance(record, dict) or set(record) != KEYS:
            raise ValueError("Invalid semantic command fields")
        strings = KEYS - {"requested_state", "observed_at_ms", "received_at_ms", "retained",
                          "physical_confirmation", "execution_confirmed", "publisher_authenticated"}
        if any(not isinstance(record[k], str) or not 1 <= len(record[k]) <= 256 for k in strings):
            raise ValueError("Invalid semantic command strings")
        entity = topics[light].removeprefix("zara/interface/").removesuffix("/stat").replace("/", ".")
        stage = record["stage"]
        if (record["schema"] != "domopi.semantic_command.v1"
                or record["actor_policy"] != "dashboard_only_v2"
                or record["entity_id"] != entity or stage not in STAGES
                or record["actor"] not in ("utente", "automazione")
                or (record["actor"] == "utente") != (record["origin"] in ("app_interface", "nodered_dashboard"))
                or record["source_topic"] != f"zara/interface/semantic/v1/{STAGES[stage]}/{entity}/stat"):
            raise ValueError("Invalid semantic identity or provenance")
        for key in ("requested_state", "retained", "physical_confirmation", "execution_confirmed", "publisher_authenticated"):
            if type(record[key]) is not bool:
                raise ValueError("Invalid semantic boolean")
        if any(record[key] for key in ("physical_confirmation", "execution_confirmed", "publisher_authenticated")):
            raise ValueError("Unsupported confirmation")
        for key in ("observed_at_ms", "received_at_ms"):
            if type(record[key]) is not int or not 0 <= record[key] <= 253402214400000:
                raise ValueError("Invalid semantic timestamp")
        if record["observed_at_ms"] > record["received_at_ms"] + 60000:
            raise ValueError("Semantic timestamp in the future")
        clean[light] = dict(record)
    return clean
