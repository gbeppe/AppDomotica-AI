"""Publish (or republish) the DomoPi Android build identity as a retained MQTT message.

Same contract as WallBox REL-APP.1: QoS 2, retained, then an exact read-back from the
broker through a fresh subscription. Credentials are read from files outside the
repository and are never printed.
"""
import argparse
from dataclasses import asdict, dataclass
import json
from pathlib import Path
import re
import sys
from threading import Event

TOPIC = "domopi/build/android"
SCHEMA = "domopi.build_identity.v1"
CONFIG_DIR = Path.home() / ".config" / "domopi"
DEFAULT_CONFIG_PATH = CONFIG_DIR / "mqtt.json"
DEFAULT_PASSWORD_PATH = CONFIG_DIR / "mqtt_password"
RECORDS_DIR = Path(__file__).resolve().parents[1] / "work" / "android-builds"


class AndroidPublishError(RuntimeError):
    """Safe command failure without external output or credentials."""


@dataclass(frozen=True)
class BuildIdentity:
    git_sha: str
    dirty: str
    build_id: str
    build_timestamp: str

    def message(self):
        return {"schema": SCHEMA, "component": "android", **asdict(self)}


@dataclass(frozen=True)
class MqttConfig:
    host: str
    port: int
    username: str
    password: str

    def __repr__(self):
        return f"MqttConfig(host={self.host!r}, port={self.port}, username={self.username!r})"


def validate_android_identity(git_sha, dirty, build_id, build_timestamp):
    if not git_sha or not re.fullmatch(r"[0-9a-f]{40}", git_sha.lower()):
        raise AndroidPublishError("Invalid Git SHA identity")
    if dirty not in ("clean", "dirty"):
        raise AndroidPublishError("Invalid dirty status")
    if not build_id or not build_id.startswith("bld_"):
        raise AndroidPublishError("Invalid build_id")
    if not build_timestamp:
        raise AndroidPublishError("Invalid build_timestamp")
    return BuildIdentity(git_sha.lower(), dirty, build_id, build_timestamp)


def load_mqtt_config(config_path=DEFAULT_CONFIG_PATH, password_path=DEFAULT_PASSWORD_PATH):
    """{"host": ..., "port": ..., "username": ...} plus a separate password file."""
    try:
        data = json.loads(Path(config_path).read_text(encoding="utf-8"))
        password = Path(password_path).read_text(encoding="utf-8").strip()
    except (OSError, json.JSONDecodeError):
        raise AndroidPublishError("MQTT configuration unavailable") from None
    if not isinstance(data, dict) or set(data) != {"host", "port", "username"}:
        raise AndroidPublishError("Invalid MQTT configuration")
    host, port, username = data["host"], data["port"], data["username"]
    if not isinstance(host, str) or not host or not isinstance(username, str) or not username \
            or not isinstance(port, int) or not 1 <= port <= 65535 or not password:
        raise AndroidPublishError("Invalid MQTT configuration")
    return MqttConfig(host, port, username, password)


def retained_identity_matches(received, topic, expected):
    """Validate broker data without coercing, defaulting or losing duplicate keys."""
    def unique_object(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("Duplicate identity field")
            result[key] = value
        return result

    try:
        if received.topic != topic or received.retain is not True or not received.payload:
            return False
        return json.loads(received.payload, object_pairs_hook=unique_object) == expected.message()
    except Exception:
        return False


def _paho_client():
    import paho.mqtt.client as mqtt
    return mqtt.Client(callback_api_version=mqtt.CallbackAPIVersion.VERSION2, reconnect_on_failure=False)


def publish_build_identity(config, identity, *, client_factory=_paho_client, timeout=5.0):
    """Publish once, then require an exact retained response from the same broker."""
    payload = json.dumps(identity.message(), separators=(",", ":"))
    connected, received = Event(), Event()
    state = {"connection_ok": False, "matched": False}
    client = None
    loop_started = False

    def on_connect(client, userdata, flags, reason_code, properties):
        state["connection_ok"] = reason_code == 0
        connected.set()

    def on_message(client, userdata, broker_message):
        # Fresh subscription after publish completion: only the broker retained replay qualifies.
        if not received.is_set():
            state["matched"] = retained_identity_matches(broker_message, TOPIC, identity)
            received.set()

    def operation():
        nonlocal client, loop_started
        client = client_factory()
        client.connect_timeout = timeout
        client.on_connect = on_connect
        client.on_message = on_message
        client.username_pw_set(config.username, config.password)
        client.connect(config.host, config.port, keepalive=10)
        if client.loop_start() != 0:
            return False
        loop_started = True
        if not connected.wait(timeout) or not state["connection_ok"]:
            return False
        info = client.publish(TOPIC, payload, qos=2, retain=True)
        info.wait_for_publish(timeout=timeout)
        if not info.is_published():
            return False
        result, _ = client.subscribe(TOPIC, qos=2)
        if result != 0:
            return False
        return received.wait(timeout) and state["matched"]

    success = False
    try:
        success = operation()
    except Exception:
        success = False
    finally:
        if client is not None:
            try:
                client.disconnect()
            except Exception:
                success = False
            if loop_started:
                try:
                    client.loop_stop()
                except Exception:
                    success = False
    return success


def save_identity_record(records_dir, identity, artifact_info=None):
    directory = Path(records_dir)
    directory.mkdir(parents=True, exist_ok=True)
    record_path = directory / (identity.build_id + ".json")
    payload = {"artifact_type": "apk", "identity": identity.message()}
    if artifact_info:
        payload["artifact_info"] = artifact_info
    with record_path.open("x", encoding="utf-8") as record:
        json.dump(payload, record, indent=2)
        record.write("\n")
    return record_path


def publish(identity, config_path=DEFAULT_CONFIG_PATH, password_path=DEFAULT_PASSWORD_PATH,
            *, publisher=publish_build_identity, config_loader=load_mqtt_config):
    return publisher(config_loader(config_path, password_path), identity)


def identity_from_record(record_path):
    record_file = Path(record_path)
    if not record_file.is_file():
        raise AndroidPublishError("Record file does not exist")
    data = json.loads(record_file.read_text(encoding="utf-8"))
    raw = data.get("identity") if isinstance(data, dict) else None
    if not isinstance(raw, dict) or raw.get("schema") != SCHEMA or raw.get("component") != "android":
        raise AndroidPublishError("Invalid record format")
    return validate_android_identity(raw.get("git_sha", ""), raw.get("dirty", ""),
                                     raw.get("build_id", ""), raw.get("build_timestamp", ""))


def main(argv=None, publisher=publish_build_identity, config_loader=load_mqtt_config):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mqtt-config", type=Path, default=DEFAULT_CONFIG_PATH)
    parser.add_argument("--mqtt-password-file", type=Path, default=DEFAULT_PASSWORD_PATH)
    actions = parser.add_subparsers(dest="action", required=True)
    republish_p = actions.add_parser("republish", help="publish again the identity of an existing record")
    republish_p.add_argument("record", type=Path)
    args = parser.parse_args(argv)

    try:
        identity = identity_from_record(args.record)
        success = publish(identity, args.mqtt_config, args.mqtt_password_file,
                          publisher=publisher, config_loader=config_loader)
    except Exception:
        print("Android build publication failed. Check parameters and local record.", file=sys.stderr)
        return 1
    if not success:
        print("Identity publication/read-back failed; Android build not successfully published.", file=sys.stderr)
        return 1
    print("Successful published Android build: exact retained identity verified from broker.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
