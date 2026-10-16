# Istruzioni per agenti AI (DomoPi / Z-AI)

Questo repository governa una **casa reale** (luci, piscina, irrigazione, clima, caldaia via
Node-RED e MQTT). Prima di qualunque lavoro leggere **[`docs/HANDOFF.md`](docs/HANDOFF.md)**, poi
[`docs/DECISIONI_E_LEZIONI.md`](docs/DECISIONI_E_LEZIONI.md) e
[`docs/SICUREZZA_RETE_E_CREDENZIALI.md`](docs/SICUREZZA_RETE_E_CREDENZIALI.md).

Regole:

1. Rispondere in **italiano**.
2. Analizzare prima (verifiche in sola lettura sono benvenute); modificare file solo dopo aver
   concordato i passi con l'utente.
3. Chiedere conferma esplicita prima di **commit, push, deploy** (backend `.20`, Node-RED,
   Mosquitto, dispositivi, router) e prima di installare l'app sul telefono.
4. **Mai stampare segreti** (token, password, chiavi): leggerli da file e passarli ai comandi. Il
   repository GitHub è pubblico: nessun segreto nei commit.
5. **Mai attivare o cambiare lo stato del controller OpenSprinkler.**
6. Linea di sviluppo: branch `feature/ui-topbar` nella worktree
   `~/.cache/Google/AndroidStudio2026.1.4/aia/agents/DomoPiAndroidApp-ai-mode`; niente commit su `main`.
7. Ogni patch al registro del Digital Twin Router deve incrementare `router_initialized_vNNN`.
8. Aggiornare la documentazione nello stesso commit del codice
   ([`docs/AI_MODE/POLITICA_FILE_E_RIPRESA.md`](docs/AI_MODE/POLITICA_FILE_E_RIPRESA.md)) e
   [`docs/HANDOFF.md`](docs/HANDOFF.md) quando cambiano stato, piano o procedure.
9. Ogni modifica alle funzioni di AI Smart (domande, risposte, comandi, schermata, messaggi)
   aggiorna nello stesso commit la guida utente
   [`docs/GUIDA_UTENTE_AI_SMART.md`](docs/GUIDA_UTENTE_AI_SMART.md) (riga «Aggiornata al» e
   tabella delle modifiche).
10. Ogni comando di AI Smart sugli **impianti di riscaldamento** (caminetto, termostati e in futuro
    pompa pavimento, caldaia o pompa di calore) parte **solo dopo la conferma** dell'utente (tasto o
    «confermo»), una sola per richiesta; luci e scene restano immediate.
