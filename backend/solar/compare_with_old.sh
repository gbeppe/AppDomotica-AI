#!/bin/sh
# Compares solarEnergy.php with the previous script over three days, without MQTT:
#   sh compare_with_old.sh /home/pi/solarEnergy.php
# The old script is copied to a temporary file with its MQTT part (and password) cut out and the
# time read from $T; the new one runs with dry-run at=. Exit 0 = same values within the rounding.
set -eu
OLD=$1
NEW=$(dirname "$0")/solarEnergy.php
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
sed -e 's/gmdate("\([YmdHi]\)")/gmdate("\1", (int)getenv("T"))/g' \
    -e '/^\$client = new/,/^\$client->disconnect();/c\
echo json_encode([$azimuth, $altitude, $Smodule]), "\\n";' "$OLD" > "$TMP/old.php"
grep -q setCredentials "$TMP/old.php" && { echo "MQTT part not removed"; exit 1; }
php -r '
$old = $argv[1]; $new = $argv[2]; $bad = 0; $n = 0;
foreach (["2026-03-20", "2026-06-21", "2026-12-21"] as $day) {
    for ($t = strtotime("$day 00:00 UTC"); $t < strtotime("$day 24:00 UTC"); $t += 900) {
        foreach (["Eclipse", "Viessmann"] as $model) {
            [$az, $alt, $sm] = json_decode(shell_exec("T=$t php $old panelModel=$model"));
            $v = array_values(json_decode(shell_exec("php $new panelModel=$model dry-run at=@$t"), true)["values"]);
            $n++;
            if (abs($v[0] - $az) > 0.006 || abs($v[1] - $alt) > 0.006 || abs($v[2] - $sm) > 0.06) {
                $bad++;
                echo gmdate("c", $t), " $model old=", json_encode([$az, $alt, $sm]), " new=", json_encode($v), "\n";
            }
        }
    }
}
echo "$n confronti, $bad diversi\n";
exit($bad ? 1 : 0);
' "$TMP/old.php" "$NEW"
