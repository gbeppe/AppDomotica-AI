# Fotovoltaico della casa: teoria e pratica

Documento di progetto nato dall'analisi del 30/10/2026. Spiega come si calcolano teorico e previsione, cosa si
misura davvero per ciascuna falda e come leggere i grafici. Comandi e configurazione tecnica:
[`../backend/solar/README.md`](../backend/solar/README.md).

## 1. L'impianto

| | Eclipse | Viessmann |
|---|---|---|
| Pannelli | 12 da 1655×990 mm, 19,66 m² | 10 da 1722×1134 mm, 19,52 m² |
| Potenza di picco | 3 kWp | 3,6 kWp |
| Età | circa 10 anni | circa 3 anni |
| Rendimento di targa (kWp / m²) | 0,153 | 0,184 |
| Inclinazione / orientamento | 40°, azimut 142,5° (sud-est) | 15°, azimut 142,5° (sud-est) |
| Inverter | Huawei | Viessmann, d'estate taglia a 3 kW |

**Orizzonte:** nessuna ombra sui pannelli, ma edifici lontani a est al mattino e alberi a ovest alla sera nascondono
il sole basso, soprattutto d'inverno (utente).

## 2. Tre curve diverse

| Curva | Da dove viene | A cosa serve |
|---|---|---|
| **Teorico a cielo sereno** (feed 146, 302, verde nei grafici) | `solarEnergy.php` sul `.20`, ogni 5 minuti, × rendimento in EmonCMS | riferimento fisso: reale contro teorico nel tempo |
| **Previsto** (feed 450, 452, arancione) | Node-RED, Open-Meteo `global_tilted_irradiance`, media oraria, × rendimento in EmonCMS | sapere quanto si produrrà, con il meteo |
| **Reale** (feed 426 Huawei, 483 Viessmann) | misure | quello che è successo |

Script e Node-RED calcolano solo **luce in watt sulla superficie**. Il rendimento lo applica sempre EmonCMS, sull'input.

## 3. Teoria: come nasce la curva verde

```
massa d'aria      = 1 / cos(90° − altezza del sole)
luce diretta      = 1,353 × 0,7^(massa d'aria^0,678)     kW/m², perpendicolare ai raggi
frazione          = geometria fra sole e falda (inclinazione, azimut)
luce sulla falda  = luce diretta × frazione × superficie × 1000     W
teorico elettrico = luce sulla falda × 0,15 (Eclipse) o 0,205 (Viessmann)
```

Il modello contiene due semplificazioni che vanno in direzioni opposte e in parte **si compensano**:

| Semplificazione | Effetto sulla curva |
|---|---|
| solo luce **diretta**, niente luce diffusa dal cielo | la abbassa |
| rendimento **di targa da nuovo** (0,15 Eclipse), niente perdite di inverter, cavi, calore, sporco, invecchiamento | la alza |

Non conosce neppure l'orizzonte reale e la temperatura dei pannelli. Quindi **un reale vicino alla curva verde non
vuol dire che i pannelli rendano come nuovi**: la curva è un riferimento comodo, non la produzione di un impianto
nuovo. Il suo valore sta nel restare **fissa**, per questo i coefficienti del teorico non si toccano (0,15 e 0,205).

## 4. Pratica: cosa si misura

La produzione per falda ha due possibili misure, che il 30/10 sono state confrontate con le app degli inverter:

| Misura del 30/10 | kWh | Scarto dall'app |
|---|---|---|
| App Huawei (Eclipse) | 9,65 | — |
| Feed Huawei 426 | 9,64 | 0 % |
| Pinza emonTx `power2` (feed 359) | 10,36 | +7 % |
| App Viessmann | 12,90 | — |
| Tesla − Huawei (feed 483) | 12,67 | −2 % |
| Tesla − pinza (feed 360) | 11,95 | −7 % |
| Somma delle app / Tesla 307 | 22,55 / 22,31 | Tesla −1 % |

**Vale il Huawei.** La pinza legge circa l'8 % in più a mezzogiorno e coincide a bassa potenza: l'errore cambia con la
potenza, quindi non si corregge con una moltiplicazione. Entra solo nei feed 359, 360 e nei loro contatori; consumi e
tariffe usano Tesla e `power1`. La pinza si può spostare su un altro carico (da decidere).

## 5. Taratura della previsione (30/10/2026)

Giornata limpida: dalle 9 alle 13 salto medio di 11 W al minuto. Totale reale 22,3 kWh, previsti 28,0.

| Rapporto reale/previsto (coefficienti di allora) | 09 | 10 | 11 | 12 | 14 | 15 |
|---|---|---|---|---|---|---|
| Eclipse | 0,83 | 0,79 | 0,79 | 0,82 | 0,45 | 0,16 |
| Viessmann | 0,99 | 0,99 | 0,96 | 0,91 | 0,80 | 0,22 |

Coefficienti della previsione, tarati sulle **ore centrali**: Eclipse da 0,15 a **0,12**, Viessmann da 0,205 a **0,195**
(input EmonCMS 1527, 1528). Il sole basso è escluso perché dipende dall'orizzonte: con questi valori quel giorno la
previsione resta circa il 10 % sopra il reale. I grafici mostrano una salita brusca verso le 8 e una discesa a scatti
della Eclipse dopo le 14. Una maschera dell'orizzonte tarata sui dati migliorerebbe la previsione; per ora non serve.

## 6. Invecchiamento: come leggerlo

- Pannelli di 10 anni perdono in genere il 5–10 %: è un ordine di grandezza, non una misura su questo impianto.
- Rispetto alla previsione (luce globale) la Eclipse rende 0,12 contro 0,153 di targa, il 78 %. Comprende le perdite
  normali di ogni impianto, oltre all'età: una giornata sola non separa le cause.
- Le due falde non si confrontano direttamente: inclinazioni diverse.
- **Metodo:** rapporto reale/teorico nelle ore centrali delle giornate limpide, confrontato stagione con stagione e
  anno con anno. Una perdita che cresce più del previsto segnala sporco, guasti o degrado. Il riferimento parte da
  questo autunno (Huawei dal 17/10/2026, teorico fermo dal 29/10 al 30/10 alle 13:34).

## 7. Punti aperti

- Viessmann a 0,195 supera la targa 0,184. Il reale è confermato dall'app: forse la luce prevista a 15° è bassa.
  Non cambia la previsione, che è tarata sul reale.
- Primo rapporto reale/teorico su una giornata limpida intera (il 30/10 il teorico mancava fino alle 13:34).
- Taglio estivo a 3 kW della Viessmann: i dati per falda partono da ottobre, verifica la prossima estate.
- Destinazione della pinza dell'emonTx.
