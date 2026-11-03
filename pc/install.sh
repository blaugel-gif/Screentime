#!/bin/bash
# Screentime – Einrichtung auf dem Linux-PC des Kindes (Debian/Ubuntu)
#
# Aufruf (als Eltern-Admin, z. B. wolfram):
#   wget -qO- https://raw.githubusercontent.com/blaugel-gif/screentime/main/pc/install.sh | sudo bash -s -- paul
#
# Argumente: [Kinderkonto, Standard: paul] [Admin-Konto, Standard: wer sudo aufruft]
set -euo pipefail

CHILD="${1:-paul}"
ADMIN="${2:-${SUDO_USER:-}}"
BASE="https://raw.githubusercontent.com/blaugel-gif/screentime/main/pc"

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
die()  { printf '\n\033[31mFehler: %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "Bitte mit sudo ausführen."
[ -n "$ADMIN" ] && [ "$ADMIN" != "root" ] || die "Admin-Konto unbekannt. Aufruf: ... | sudo bash -s -- $CHILD <admin>"
id "$CHILD" >/dev/null 2>&1 || die "Das Kinderkonto '$CHILD' gibt es nicht."
id "$ADMIN" >/dev/null 2>&1 || die "Das Admin-Konto '$ADMIN' gibt es nicht."
[ "$CHILD" != "$ADMIN" ] || die "Kinder- und Admin-Konto müssen verschieden sein."

if id -nG "$CHILD" | grep -qwE 'sudo|wheel|admin'; then
  printf '\n\033[33mWARNUNG: %s hat noch Adminrechte und könnte alles abschalten.\n' "$CHILD"
  printf 'Entfernen mit:  sudo deluser %s sudo\033[0m\n' "$CHILD"
fi

say "1/5  Pakete installieren (SSH, Timekpr, Python, Benachrichtigungen) …"
apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openssh-server timekpr-next python3 wget libnotify-bin pulseaudio-utils sound-theme-freedesktop >/dev/null

say "2/5  Programme installieren …"
SRC=""
if [ -n "${BASH_SOURCE[0]:-}" ] && [ -f "${BASH_SOURCE[0]}" ]; then
  SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fi
fetch() {
  local name="$1" dest="$2"
  if [ -n "$SRC" ] && [ -f "$SRC/$name" ]; then
    install -o root -g root -m 755 "$SRC/$name" "$dest"
  else
    wget -qO "$dest.tmp" "$BASE/$name" || die "Download von $name fehlgeschlagen."
    install -o root -g root -m 755 "$dest.tmp" "$dest"
    rm -f "$dest.tmp"
  fi
}
fetch screentime-ctl    /usr/local/sbin/screentime-ctl
fetch screentime-logger /usr/local/sbin/screentime-logger

say "3/5  Einstellungen anlegen …"
install -d -o root -g root -m 755 /etc/screentime
install -d -o root -g root -m 700 /var/lib/screentime
printf '{\n  "child": "%s"\n}\n' "$CHILD" > /etc/screentime/config.json
if [ ! -f /etc/screentime/apps.json ]; then
  cat > /etc/screentime/apps.json <<'EOF'
[
  {"name": "Minecraft", "procs": ["java", "javaw", "minecraft-launcher"], "contains": "minecraft", "game": true},
  {"name": "Steam", "procs": ["steam", "steamwebhelper"], "contains": "", "game": true},
  {"name": "Discord", "procs": ["discord", "Discord"], "contains": "", "game": false},
  {"name": "Firefox", "procs": ["firefox", "firefox-esr", "firefox-bin"], "contains": "", "game": false},
  {"name": "Chrome / Chromium", "procs": ["chrome", "chromium", "google-chrome"], "contains": "", "game": false}
]
EOF
fi
chmod 644 /etc/screentime/config.json /etc/screentime/apps.json

say "4/5  Rechte für die App ($ADMIN) einrichten …"
SUDOERS=/etc/sudoers.d/screentime
TMP="$(mktemp)"
echo "$ADMIN ALL=(root) NOPASSWD: /usr/local/sbin/screentime-ctl" > "$TMP"
visudo -cf "$TMP" >/dev/null || { rm -f "$TMP"; die "sudo-Regel ungültig."; }
install -o root -g root -m 440 "$TMP" "$SUDOERS"
rm -f "$TMP"

say "5/5  Dienste starten …"
cat > /etc/systemd/system/screentime-logger.service <<'EOF'
[Unit]
Description=Screentime – Nutzungszeit pro Programm erfassen
After=multi-user.target

[Service]
ExecStart=/usr/bin/python3 /usr/local/sbin/screentime-logger
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now ssh >/dev/null 2>&1 || systemctl enable --now sshd >/dev/null 2>&1 || true
systemctl enable --now screentime-logger >/dev/null
systemctl restart screentime-logger

IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
say "Fertig!"
echo "In der App eintragen:"
echo "  PC-Adresse:   ${IP:-<IP des PCs>}"
echo "  Port:         22"
echo "  Benutzer:     $ADMIN"
echo "  Passwort:     das Linux-Passwort von $ADMIN"
echo
if ! timekpra --userinfo "$CHILD" >/dev/null 2>&1; then
  echo "Hinweis: Timekpr kennt '$CHILD' noch nicht. $CHILD muss sich einmal am PC anmelden."
fi
echo "Tipp: In der Fritzbox dem PC immer dieselbe IP-Adresse zuweisen."
