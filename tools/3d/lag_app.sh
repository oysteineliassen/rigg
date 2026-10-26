#!/bin/bash
# Rendrer 3D-animasjonene og pakker dem som bilder i appen (app/src/main/assets/figur3d/<øvelse>/NN.webp).
# Bruk: tools/3d/lag_app.sh [øvelse ...]   (uten argumenter: alle). Krever Blender-modulen (bpy) i B3D-venv.
set -e
HER=$(cd "$(dirname "$0")" && pwd)
B=${B3D:-$HER/b3d}/bin/python
TMP=${TMPDIR:-/tmp}/rigg3d_app
N=48
OVELSER=${@:-goblet_squat db_shoulder_press db_curl rdl_db db_lunge db_lateral_raise}
for E in $OVELSER; do
  mkdir -p "$TMP/$E"
  "$B" "$HER/rigg3d.py" "$E" "$TMP/$E" $N 600 > "$TMP/$E.log" 2>&1
  python3 "$HER/pakk.py" "$TMP/$E" "$HER/../../app/src/main/assets/figur3d/$E"
  echo "ferdig $E"
done
