# Credenziali MQTT dei nodi broker di Node-RED

`set_broker_user.py BROKER_ID UTENTE FILE_PASSWORD` (su `.20`) imposta utente e password di un
nodo `mqtt-broker` tramite l'Admin API: la password è letta da un file e finisce solo nelle
credenziali cifrate di Node-RED, mai nel repository. Backup completo prima della modifica.

2026-10-14 (livello 3 credenziali): broker "domopi mqtt" (`3ed6302a.5af928`) passato all'utente
`domotica` (password in `/home/pi/.config/domopi/mqtt_domotica.pass`).
