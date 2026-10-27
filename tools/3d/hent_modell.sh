#!/bin/bash
# Laster ned MakeHuman-modellen (CC0) som rigg3d.py bruker, til mappen modell/ ved siden av dette skriptet.
# Kilde: https://github.com/makehumancommunity/makehuman (alt under makehuman/data er CC0 fra 2020).
set -e
cd "$(dirname "$0")"
mkdir -p modell/lokal
B=https://raw.githubusercontent.com/makehumancommunity/makehuman/master/makehuman/data
get() { curl -sSfL -o "$2" "$B/$1"; echo "  $2"; }
get 3dobjs/base.obj modell/base.obj
get rigs/default.mhskel modell/default.mhskel
get rigs/default_weights.mhw modell/default_weights.mhw
for t in caucasian-male-young universal-male-young-maxmuscle-averageweight universal-male-young-maxmuscle-minweight; do
  get targets/macrodetails/$t.target modell/$t.target
done
get targets/macrodetails/height/male-young-averagemuscle-averageweight-maxheight.target modell/male-young-averagemuscle-averageweight-maxheight.target
for t in torso-muscle-dorsi torso-muscle-pectoral; do get targets/torso/$t-incr.target modell/lokal/$t-incr.target; done
for s in l r; do for p in lowerarm-muscle upperarm-shoulder-muscle upperarm-muscle lowerleg-muscle upperleg-muscle; do
  get targets/armslegs/$s-$p-incr.target modell/lokal/$s-$p-incr.target
done; done
echo "Ferdig."
