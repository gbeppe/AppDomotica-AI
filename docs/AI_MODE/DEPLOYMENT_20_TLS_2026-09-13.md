# Deployment persistente House AI su `.20`

Eseguito il 13 settembre 2026 con autorizzazione esplicita dell'utente.

## Stato installato

- Release attiva: `/opt/domopi-house-ai/releases/20260913-daily-extreme`, con
  `REVISION` uguale a `16e47fa`;
- release precedente disponibile per rollback:
  `/opt/domopi-house-ai/releases/20260913-tls`, revisione `3c04bd1`;
- symlink attivo: `/opt/domopi-house-ai/current`;
- utente non interattivo: `house-ai`;
- `house-ai-gateway.service`: abilitato e attivo, `127.0.0.1:8766`;
- `house-ai-backend.service`: abilitato e attivo, `127.0.0.1:8765`;
- configurazione: `/etc/house-ai/{gateway,backend}.env`, `root:house-ai`, 0640;
- chiave Groq: `/etc/house-ai/secrets/groq.key`, `house-ai:house-ai`, 0600;
- token Android recuperabile privatamente dall'utente in
  `/home/pi/.config/house-ai/android.token`, `pi:pi`, 0600;
- Tailscale Serve: `https://domopi.tailf30ba8.ts.net/` verso backend 8765,
  accessibile solo dalla tailnet; gateway non esposto;
- il precedente servizio Tailscale `svc:emoncms` è rimasto invariato.

Health locale autenticato: HTTP 200, `mode=read_only`, planner e log configurati.
TLS: certificato Let's Encrypt valido per `domopi.tailf30ba8.ts.net`; health
autenticato HTTP 200. Domanda reale HTTPS sul prelievo del 6 settembre: piano
corretto, una evidenza EmonCMS e stato `complete`.

L'aggiornamento successivo aggiunge `energy_daily_extreme`: selezione read-only
del massimo o minimo giornaliero su EmonCMS, con esclusione dei giorni senza
copertura completa. Non richiede nuove dipendenze, porte, credenziali o modifiche
a `.15`, Node-RED, Mosquitto e Tailscale.

La release `16e47fa` è stata compilata e ha superato 63 test come utente di
servizio su Python 3.9.2 prima dello spostamento atomico del symlink. Dopo il
riavvio, health autenticato, servizi e quattro log allowlisted risultavano
disponibili. La domanda originale è riuscita attraverso il TLS dell'app; dati e
limiti sono in
[`VERIFICA_ESTREMI_GIORNALIERI_2026-09-13.md`](VERIFICA_ESTREMI_GIORNALIERI_2026-09-13.md).

L'app precompila questo URL e `HouseAiRepository` accetta HTTP soltanto per
loopback di test. Il token non è incorporato nell'APK e resta in memoria nella
schermata. Sul telefono connesso alla stessa tailnet, copiare privatamente il
contenuto di `~/.config/house-ai/android.token` nel campo token. Non inviarlo in
chat o screenshot.

## Operazioni utili

```sh
systemctl status house-ai-gateway house-ai-backend
tailscale serve status
```

Riavvio dopo aggiornamento:

```sh
sudo systemctl restart house-ai-gateway house-ai-backend
```

Rollback applicativo: puntare `/opt/domopi-house-ai/current` alla release
precedente verificata, poi riavviare i due servizi. Disattivazione TLS House AI:

```sh
sudo tailscale serve --https=443 off
```

Arresto e disabilitazione completa, senza rimuovere configurazioni o release:

```sh
sudo systemctl disable --now house-ai-backend house-ai-gateway
```

Non usare `tailscale serve reset`: rimuoverebbe anche configurazioni Serve non
appartenenti a House AI. `.15`, Node-RED, Mosquitto e i log non sono stati modificati.

## Limiti residui

Il TLS è privato Tailscale: il telefono deve appartenere alla tailnet e avere
connettività Tailscale. Non è stato configurato Funnel pubblico. Il test da
workstation ha verificato TLS e query completa; resta da inserire il token sul
telefono fisico e provare testo, riconoscimento e sintesi vocale. La release
finale è costruita dal commit registrato in `REVISION`; il symlink `current`
viene spostato solo dopo copia e test completi.
