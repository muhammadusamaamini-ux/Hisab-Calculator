#!/usr/bin/env bash
# Puts the calculator's three fonts into web/fonts so the app looks the same offline.
# If this fails, the app still works and uses the phone's or PC's own fonts.
set -euo pipefail
out="web/fonts"
mkdir -p "$out"
tmp="$(mktemp -d)"
( cd "$tmp" && npm pack @fontsource/barlow@5 @fontsource/barlow-condensed@5 @fontsource/jetbrains-mono@5 >/dev/null )
for f in "$tmp"/*.tgz; do d="$tmp/$(basename "$f" .tgz)"; mkdir -p "$d"; tar -xzf "$f" -C "$d"; done
css="$out/fonts.css"
: > "$css"
add() {
  local family="$1" stem="$2" weight="$3" file
  file="$(find "$tmp" -name "$stem-latin-$weight-normal.woff2" | head -1)"
  [ -n "$file" ] || { echo "missing $stem $weight"; return 0; }
  cp "$file" "$out/"
  echo "@font-face{font-family:'$family';font-style:normal;font-weight:$weight;font-display:swap;src:url('$(basename "$file")') format('woff2')}" >> "$css"
}
for w in 400 500 600 700; do add "Barlow" "barlow" "$w"; done
for w in 600 700; do add "Barlow Condensed" "barlow-condensed" "$w"; done
for w in 400 500 600; do add "JetBrains Mono" "jetbrains-mono" "$w"; done
cat "$css"
