# Pixel Bloom

Platform 2D in pixel art per Android, orizzontale, con meccanica "Meraviglia".
Tutto (grafica, musica chiptune, livelli) e' generato dal codice: nessun asset esterno.

## Caratteristiche
- Menu: Inizia, Continua, Editor, Opzioni, Esci. 3 profili di salvataggio.
- 20 livelli in 4 mondi (Prato, Deserto, Ghiaccio, Vulcano), il quinto di ogni mondo e' un boss. Mappa dei mondi.
- 2 personaggi: **Rina** (doppio salto, corsa piu' veloce) e **Toki** (planata, pestone che rompe i blocchi).
- Potenziamenti: Bulbo (+1 vita) e Fiore (spara semi).
- Seme Meraviglia in ogni livello: 4 effetti (Gravita' Lunare, Gigante, Turbo, Pioggia di Monete).
- Editor di livelli: matita, gomma, mano, annulla, prova subito, salva, condividi/importa come testo.
- Comandi a schermo (dimensione e opacita' regolabili), tastiera e controller.

## Controlli
| Azione | Tastiera | Controller |
|---|---|---|
| Muovi | Frecce / WASD | D-pad / stick |
| Salta / conferma | Z, Spazio, K | A, Y |
| Corri / spara / indietro | X, J, Shift | X, B |
| Pausa | Invio, P | Start |
| Indietro | Esc | Select |
| Editor: oggetto prec./succ. | Q / E | L1 / R1 |

Nell'editor da tastiera: frecce = cursore, Z = piazza, X = cancella, Invio = menu.

## Compilare
Il workflow `.github/workflows/build.yml` compila l'APK a ogni push: scaricalo da *Actions > Build APK > Artifacts*.
In locale: `gradle :app:assembleDebug` (Gradle 8.9, JDK 17, Android SDK 34).

## Struttura
Il nucleo (`com.marco.pixelbloom`) e' Kotlin puro, senza dipendenze Android; il livello Android sta in `android/`.
