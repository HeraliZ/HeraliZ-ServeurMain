#!/bin/bash
# lancer.sh NOM DISPLAY LARGEUR HAUTEUR LENT MEMOIRE
N=$1; D=$2; W=$3; H=$4; LENT=${5:-1}; MEM=${6:-2500M}
pgrep -f "Xvfb $D " >/dev/null || (Xvfb $D -screen 0 ${W}x${H}x24 -nolisten tcp >/dev/null 2>&1 &)
sleep 1
cd /root/mc
export LP_NUM_THREADS=3
DISPLAY=$D JAVA_TOOL_OPTIONS="-Xmx$MEM -XX:MaxDirectMemorySize=256M -Dtournage.lent=$LENT -Dtournage.dossier=/root/tournage" exec portablemc --main-dir /root/mc start --mc-dir /root/mc/$N forge:1.20.1:stable -u $N --resolution ${W}x${H} --join-server 127.0.0.1 > /root/mc/$N.log 2>&1
