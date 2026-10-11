#!/bin/bash
# Sécurité de la VM du site HeraliZ, lancée en root par .github/workflows/site.yml (choix « securite », et à la fin
# de « installer ») : fail2ban bloque les adresses IP qui essaient des mots de passe SSH, et LLMNR (résolution de noms
# du réseau local, ouverte sur internet par systemd-resolved) est coupé. Peut être relancée sans danger.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

echo "== Sécurité de la VM"
apt-get install -y -q fail2ban python3-systemd nftables > /dev/null

# Debian 13 : OpenSSH 10 écrit ses refus sous le nom « sshd-session », que le filtre de fail2ban 1.1.0 ne regarde pas
cat > /etc/fail2ban/filter.d/sshd.local <<'CONF'
# VM du site HeraliZ, écrit par site/vm/securite.sh : modifier le dépôt, pas ce fichier.
[Definition]
_daemon = sshd(?:-session)?

[Init]
journalmatch = _SYSTEMD_UNIT=ssh.service + _SYSTEMD_UNIT=sshd.service + _COMM=sshd + _COMM=sshd-session
CONF
cat > /etc/fail2ban/jail.d/heraliz.conf <<'CONF'
# VM du site HeraliZ, écrit par site/vm/securite.sh : modifier le dépôt, pas ce fichier.
# SSH : 6 mots de passe faux en 10 minutes depuis une même adresse IP = adresse bloquée 1 heure, de plus en plus
# longtemps si elle recommence (2 jours au plus).
[sshd]
enabled = true
backend = systemd
port = ssh
maxretry = 6
findtime = 10m
bantime = 1h
bantime.increment = true
bantime.maxtime = 2d
CONF
systemctl enable fail2ban > /dev/null 2>&1
systemctl restart fail2ban
for _ in $(seq 1 15); do fail2ban-client ping > /dev/null 2>&1 && break; sleep 1; done
if fail2ban-client status sshd > /dev/null 2>&1; then
  echo "fail2ban : actif sur SSH (6 essais ratés en 10 min = adresse bloquée 1 h)"
else
  echo "::error::fail2ban ne surveille pas SSH : voir « journalctl -u fail2ban » sur la VM."
  exit 1
fi
# le filtre reconnaît-il les vrais essais des dernières 24 h ?
essais=$(journalctl --since "-24h" _COMM=sshd-session _COMM=sshd 2> /dev/null | grep -cE "Failed password|Invalid user|authentication failure" || true)
echo "Essais de connexion SSH ratés ces dernières 24 h : ${essais:-0}"
if [ "${essais:-0}" -gt 0 ]; then
  # (fail2ban-regex lit le journal avec le filtre « sshd », réglages de sshd.local compris)
  reconnus=$(timeout 180 fail2ban-regex systemd-journal sshd 2> /dev/null \
    | awk '/^Lines:/ {for (i = 1; i <= NF; i++) if ($i == "matched,") print $(i - 1)}' | head -n 1)
  echo "Reconnus par le filtre de fail2ban : ${reconnus:-?}"
  if [ "${reconnus:-}" = 0 ]; then
    echo "::warning::fail2ban ne reconnaît aucun des essais ratés du journal : vérifier filter.d/sshd.local sur la VM."
  fi
fi
echo "Adresses bloquées en ce moment : $(fail2ban-client status sshd | awk -F: '/Currently banned/ {gsub(/[ \t]/, "", $2); print $2}')"

# HSTS sur un site déjà installé (installer.sh l'écrit dans les nouveaux) : sur un Wi-Fi piégé, personne ne peut plus
# détourner un joueur vers une fausse page http:// pour voler son mot de passe
SITE_NGINX=/etc/nginx/sites-available/heraliz
if [ -f "$SITE_NGINX" ] && ! grep -q "Strict-Transport-Security" "$SITE_NGINX"; then
  cp "$SITE_NGINX" /tmp/heraliz-nginx.avant
  sed -i '/add_header Referrer-Policy/a\    add_header Strict-Transport-Security "max-age=31536000" always;' "$SITE_NGINX"
  if nginx -t > /dev/null 2>&1; then
    systemctl reload nginx
    echo "HSTS : ajouté (le site ne passe plus jamais par http://)"
  else
    cp /tmp/heraliz-nginx.avant "$SITE_NGINX"
    echo "::warning::HSTS non ajouté : la configuration de nginx a été refusée, remise comme avant."
  fi
  rm -f /tmp/heraliz-nginx.avant
elif [ -f "$SITE_NGINX" ]; then
  echo "HSTS : déjà en place"
fi

# LLMNR (et mDNS) : inutiles sur un serveur, et ouverts sur internet par systemd-resolved
if systemctl is-active --quiet systemd-resolved; then
  install -d /etc/systemd/resolved.conf.d
  printf '# VM du site HeraliZ, écrit par site/vm/securite.sh\n[Resolve]\nLLMNR=no\nMulticastDNS=no\n' \
    > /etc/systemd/resolved.conf.d/heraliz.conf
  systemctl restart systemd-resolved
  echo "LLMNR et mDNS : coupés"
fi
ouverts=$(ss -ltnH 2> /dev/null | awk '{print $4}' | grep -vE '^(127\.[0-9.]+|\[::1\]|::1)(%[[:alnum:]]+)?:' \
  | awk -F: '{print $NF}' | sort -n | uniq | tr '\n' ' ')
echo "Ports ouverts sur le réseau : ${ouverts:-aucun}"
