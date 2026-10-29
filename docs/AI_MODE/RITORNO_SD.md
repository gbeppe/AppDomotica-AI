# Tornare alla vecchia SD (istruzioni per chi è in casa)

Dal 29/10/2026 i due Raspberry della sala tecnica partono da un **disco SSD** collegato con un cavo USB, non più
dalla schedina SD. Le vecchie SD sono nel cassetto, etichettate. Se un Raspberry smette di funzionare e Giuseppe
non è raggiungibile, si può tornare al sistema vecchio in due minuti. **Non serve un computer.**

## Quale Raspberry
| | Cosa fa | Segni di guasto |
|---|---|---|
| **`.15` (emonPi, scatola di alluminio con display spento)** | caminetto, pompa del pavimento, contatori di energia | il caminetto non risponde ai comandi, i grafici di energia nell'app sono fermi |
| **`.20` (domopi)** | Internet di casa (DNS), luci dall'app e da Alexa, app, notifiche | **nessun dispositivo naviga**, le luci non rispondono, l'app non si collega |

Prima di tutto: **staccare e riattaccare l'alimentazione** del Raspberry e aspettare 3 minuti. Spesso basta.

## Se non basta: tornare alla SD
1. Togliere l'**alimentazione** del Raspberry (il cavo USB-C nero).
2. Staccare il **disco SSD** (il cavo USB dalla porta **blu**). Lasciare collegati il cavo di rete e, sul `.20`,
   la chiavetta della caldaia (porta **nera**).
3. Prendere dal cassetto la **SD con l'etichetta di quel Raspberry** e inserirla nella fessura sotto la scheda
   (contatti verso la scheda; entra senza forzare).
4. Riattaccare l'**alimentazione**. Dopo 2–3 minuti il Raspberry funziona come prima del 29/10.
5. Avvisare Giuseppe: il disco SSD va lasciato da parte **senza toccarlo**.

**Mai** lasciare collegati insieme SD e SSD dello stesso Raspberry quando si riaccende, salvo il caso del punto 3
(SSD staccato).
