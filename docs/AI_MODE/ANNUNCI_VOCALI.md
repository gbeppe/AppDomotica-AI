# Annunci vocali della casa

Progetto deciso con l'utente il 30–31/10/2026. Tabella di lavoro: https://claude.ai/artifact/MbK7AZCu4PJD51rmcAedTL (privata).
Annunciatore unico sul `.20`, Alexa Speak (scheda oggi disattivata) come uscita, ntfy resta com'è.

## Annunci

| Livello | Evento | Frase | Echo |
|---|---|---|---|
| Urgente | puffer alto oltre 60 °C per 1 minuto | «Attenzione: puffer a 61 gradi» | living e camera |
| Urgente | servizio critico fermo o Internet assente (salute impianto), l'altro server muto da 15 minuti | «Il server punto 15 non risponde» | living e camera |
| Urgente | allarme movimento, se attivo | «Qualcuno è passato da…» | living e camera |
| Informativo (comando) | condizionatore acceso/spento, anche automatico | «Condizionatore acceso in caldo a 20 gradi, manuale» | living |
| Informativo (comando) | caminetto acceso/spento | «Caminetto acceso in modalità automatica dalle 17:00 alle 19:00» | living |
| Informativo | Powerwall non carica | «La batteria non si sta caricando» | living |
| Informativo | schede ESP offline, sensori muti (HOUSE-ALERTS.1) | titolo dell'avviso | living |
| Informativo | batteria bassa dei sensori IKEA | «Batteria bassa, sensore…» | living |
| Riepilogo | ogni sera alle 21:00 | «Oggi 22 chilowattora, batteria 96 percento, impianto regolare» | living |

## Regole

- Ore attive degli informativi: 8:30–21:30, sabato e domenica dalle 9:30.
- Ore tranquille: solo urgenti, in living e camera, a basso volume.
- Tetto: 3 avvisi l'ora, 8 al giorno; gli urgenti sempre; i comandi di condizionatore e caminetto non contano.
- Stesso avviso: non prima di 2 ore; più eventi ravvicinati in una frase (da fare con il parlato).
- Presenza: irrilevante, annuncia sempre. Senza Internet Alexa non parla: resta il telefono, se raggiungibile.
- Avviso ntfy del puffer portato da 85 a 60 °C: per costruzione arriva a 60 °C (utente). A 60 °C può scattare spesso in estate: da
  rivedere se capita.

## Fasi

1. **Ombra** (ANNOUNCER.1, [`../../backend/nodered/announcer/`](../../backend/nodered/announcer/README.md)): una settimana di registro, nessuna voce.
2. Lettura del registro con l'utente, ritocchi.
3. Login Amazon di Alexa Speak (utente), prova a basso volume, attivazione.
4. Interruttore e «non disturbare» nell'app.

**Non ancora coperti in ombra:** movimento e batterie IKEA (nei flow non c'è più la fonte dei dati, solo l'uscita della vecchia
scheda: da ritrovare con l'utente); `.20` fermo annunciato dal `.15` (serve un annunciatore sul `.15`); raggruppamento in una frase.
