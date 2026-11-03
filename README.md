# Screentime

Android-App, mit der Eltern die Bildschirmzeit auf dem Linux-PC ihres Kindes
sehen und steuern – über [Timekpr-nExT](https://launchpad.net/timekpr-next).

- Restzeit, genutzte Zeit und Tageslimit live anzeigen
- Bonuszeit geben oder abziehen, „Für heute beenden“
- PC sofort sperren und wieder entsperren
- Kind rufen: Nachricht mit Ton auf dem PC-Bildschirm
- Sprachanruf zum PC: Mikrofon und Lautsprecher des PCs, das Kind nimmt per Klick an
- Tageslimits je Wochentag, erlaubte Uhrzeiten und Sperrart ändern
- Spielzeit-Limit für ausgewählte Programme (z. B. Minecraft, Steam)
- Nutzungszeit pro Programm (heute / letzte 7 Tage)

Die App verbindet sich per SSH direkt mit dem PC. Es gibt keinen Server im
Internet; Zugangsdaten liegen nur verschlüsselt auf dem Handy.

```
Handy (App) ──SSH──▶ PC: screentime-ctl ──▶ Timekpr-nExT
                         screentime-logger ──▶ Nutzung pro Programm
```

## 1. PC einrichten (einmalig)

Voraussetzung: Das Kind hat ein **Standardkonto ohne Adminrechte**, die Eltern
ein eigenes Admin-Konto (hier `wolfram`).

Als Eltern-Admin im Terminal:

```bash
wget -qO- https://raw.githubusercontent.com/blaugel-gif/screentime/main/pc/install.sh | sudo bash -s -- paul
```

(`paul` = Kinderkonto. Das Admin-Konto wird automatisch erkannt.)

Das Skript

1. installiert `openssh-server`, `timekpr-next` und `python3`,
2. legt `screentime-ctl` (Schnittstelle für die App) und `screentime-logger`
   (Nutzung pro Programm) nach `/usr/local/sbin`,
3. erlaubt **nur** dem Admin-Konto, `screentime-ctl` ohne erneute
   Passwortabfrage per sudo auszuführen,
4. startet den Protokoll-Dienst und zeigt am Ende die IP-Adresse für die App.

Tipp: In der Fritzbox unter *Heimnetz → Netzwerk* beim PC
„Diesem Netzwerkgerät immer die gleiche IPv4-Adresse zuweisen“ anhaken.

## 2. App installieren (auf jedem Eltern-Handy)

1. Auf dem Handy öffnen:
   **https://github.com/blaugel-gif/screentime/releases/latest/download/screentime.apk**
2. Download öffnen. Android fragt einmalig, ob der Browser Apps installieren
   darf → erlauben.
3. In der App unter **Einstellungen** eintragen: IP des PCs, Port 22,
   Benutzer `wolfram`, Linux-Passwort → **Speichern & verbinden**.

Updates: einfach den Link oben erneut öffnen und drüberinstallieren.

## Bedienung

| Reiter | Inhalt |
|---|---|
| **Heute** | Restzeit, Sofort sperren/entsperren, Rufen, Bonus (+15/+30/+60, −15, eigene Minuten), Für heute beenden, Spielzeit, Nutzung pro Programm |
| **Limits** | Zeit pro Wochentag, erlaubte Uhrzeit, Spielzeit-Limit, was bei Zeitablauf passiert |
| **Programme** | Welche Programme erfasst werden und welche als „Spiel“ zählen |
| **Einstellungen** | Verbindung, PC-Schlüssel |

Von unterwegs funktioniert die App nur über ein VPN ins Heimnetz
(z. B. Fritzbox-VPN mit der WireGuard-App).

## Wissenswertes

- **Nutzung pro Programm** zählt, solange ein Programm in der Sitzung des
  Kindes läuft und der Bildschirm nicht gesperrt ist – auch im Hintergrund.
- **Spielzeit** ist bei Timekpr *ein* gemeinsames Limit für alle als Spiel
  markierten Programme.
- **Sperrart „Bildschirm sperren“** ist für Kinder ungeeignet (Entsperren mit
  eigenem Passwort). Empfohlen: „Abmelden“.
- Der PC-Schlüssel wird beim ersten Verbinden gespeichert; danach verbindet
  sich die App nur noch mit genau diesem PC.

- **Sofort sperren** sperrt den Bildschirm und deaktiviert das Passwort des
  Kinderkontos, bis in der App „Entsperren“ gedrückt wird – auch über einen
  Neustart hinweg. Offene Programme bleiben erhalten.
- **Rufen** zeigt eine Nachricht mit Ton, wenn das Kind angemeldet ist.
- **Anrufen** klingelt am PC. Erst wenn das Kind auf „Annehmen“ klickt, werden
  Mikrofon und Lautsprecher am PC eingeschaltet; es kann jederzeit auflegen.
  Mit Headset am PC klingt es am besten (sonst evtl. Echo). Die App während des
  Gesprächs geöffnet lassen.
- Nach einem Update der App das Installationsskript auf dem PC **erneut
  ausführen**, damit neue Funktionen dort ankommen.

## Deinstallieren (PC)

```bash
sudo systemctl disable --now screentime-logger
sudo rm -f /etc/systemd/system/screentime-logger.service /etc/sudoers.d/screentime \
           /usr/local/sbin/screentime-ctl /usr/local/sbin/screentime-logger
sudo rm -rf /etc/screentime /var/lib/screentime
```

## Aufbau

- `android/` – App (Kotlin, Jetpack Compose, SSH über JSch)
- `pc/` – Installationsskript und PC-Programme
- `.github/workflows/build.yml` – baut bei jeder Änderung die APK und
  veröffentlicht sie als Release
