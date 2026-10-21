<?php
// Theoretical PV light on the two roof arrays: clear sky, direct beam on the tilted plane.
// Called by Node-RED on .20 (tab "start local scripts", every 5 min, Eclipse then Viessmann):
//   php /home/pi/solarEnergy.php panelModel=Eclipse|Viessmann
// Publishes retained (QoS 0) on the .15 broker, read by EmonCMS (panel efficiency applied there):
//   Eclipse   -> emon/planetary/azimuth, altitude, smodule
//   Viessmann -> emon/planetary/azimuth2, altitude2, smodule2
// smodule = W of sunlight on the array surface; 0 at night.
// Sun position ported from http://www.stjarnhimlen.se/comp/tutorial.html (variable names as there).
// Test options: dry-run (print JSON, no MQTT), at=<date/time> (with dry-run).
// MQTT password: ~/.config/domopi/mqtt_emonpi.pass (user emonpi), never in this file.
// See backend/solar/README.md.

const LAT = 45.970730;
const LON = 12.672993;
const BROKER = '192.168.1.15';
const USER = 'emonpi';
const PASSWORD_FILE = '/home/pi/.config/domopi/mqtt_emonpi.pass';
// tilt and azimuth in degrees (azimuth from north, clockwise), surface in m2
const ARRAYS = [
    'Eclipse' => ['tilt' => 40, 'azimuth' => 142.5, 'surface' => 19.66, 'suffix' => ''],    // 12 panels 1655x990
    'Viessmann' => ['tilt' => 15, 'azimuth' => 142.5, 'surface' => 19.52, 'suffix' => '2'], // 10 panels 1722x1134
];

// Node-RED passes "panelModel=X" as the first argument; the test options follow.
parse_str($argv[1] ?? '', $arg);
$options = array_slice($argv, 2);
$dryRun = in_array('dry-run', $options, true);
$at = null;
foreach ($options as $option) {
    if (strncmp($option, 'at=', 3) === 0) $at = strtotime(substr($option, 3));
}
$model = $arg['panelModel'] ?? '';
if (!array_key_exists($model, ARRAYS)) {
    fwrite(STDERR, "solarEnergy: panelModel must be Eclipse or Viessmann\n");
    exit(1);
}
if ($at === false || ($at !== null && !$dryRun)) {
    fwrite(STDERR, "solarEnergy: at= needs a valid time and dry-run\n");
    exit(1);
}
$panel = ARRAYS[$model];
$now = $at ?? time();

getsunpos(deg2rad(LAT), deg2rad(LON), gmdate('Y', $now), gmdate('m', $now), gmdate('d', $now),
          gmdate('H', $now) + gmdate('i', $now) / 60, $azimuth, $altitude);

$rad = 4 * asin(1) / 360;
// Irradiance (kW/m2 perpendicular to the rays) only when a number is given; otherwise from the air mass:
// 1.353 kW/m2 solar constant, ~70% transmitted, 0.678 empirical fit
// (http://pveducation.org/pvcdrom/properties-of-sunlight/air-mass#formula).
if (isset($arg['Irradiance']) && is_numeric($arg['Irradiance'])) {
    $Sincident = (float)$arg['Irradiance'];
} else {
    $airmass = 1 / cos((90 - $altitude) * $rad);
    $Sincident = 1.353 * pow(0.7, pow($airmass, 0.678));
}
$fraction = cos($altitude * $rad) * sin($panel['tilt'] * $rad) * cos($azimuth * $rad - $panel['azimuth'] * $rad)
          + sin($altitude * $rad) * cos($panel['tilt'] * $rad);
$Smodule = max(0, $Sincident * $fraction * $panel['surface'] * 1000);
if ($altitude < 0) {
    $altitude = 0;
    $azimuth = 0;
    $Smodule = 0;
}

$s = $panel['suffix'];
$values = ["emon/planetary/azimuth$s" => round($azimuth, 2), "emon/planetary/altitude$s" => round($altitude, 2),
           "emon/planetary/smodule$s" => round($Smodule, 1)];
if ($dryRun) {
    echo json_encode(['model' => $model, 'utc' => gmdate('c', $now), 'values' => $values]), "\n";
    exit(0);
}

$password = @file_get_contents(PASSWORD_FILE);
if ($password === false || trim($password) === '') {
    fwrite(STDERR, "solarEnergy: missing " . PASSWORD_FILE . "\n");
    exit(1);
}
$sent = 0;
try {
    // Unique id: a fixed one would disconnect any other client using it.
    $client = new Mosquitto\Client("solarEnergy-$model-" . getmypid());
    $client->setCredentials(USER, trim($password));
    $client->onPublish(function () use (&$sent) { $sent++; });
    $client->connect(BROKER, 1883, 30);
    foreach ($values as $topic => $value) {
        $client->publish($topic, (string)$value, 0, true);
    }
    for ($end = microtime(true) + 5; $sent < count($values) && microtime(true) < $end;) {
        $client->loop(100);
    }
    $client->disconnect();
} catch (Mosquitto\Exception $e) {
    fwrite(STDERR, "solarEnergy: MQTT " . $e->getMessage() . "\n");
    exit(1);
}
if ($sent < count($values)) {
    fwrite(STDERR, "solarEnergy: published $sent of " . count($values) . "\n");
    exit(1);
}
exit(0);

function getsunpos($LAT, $LON, $year, $month, $day, $hour, &$azimuth, &$altitude) {
    // day number from 2000-01-00
    $d = 367 * $year - floor((7 * ($year + floor(($month + 9) / 12))) / 4)
       + floor((275 * $month) / 9) + $day - 730530;

    $w = 4.9382415669097640822661983551248 + .00000082193663128794959930855831205818 * $d; // longitude of perihelion
    $e = 0.016709 - .000000001151 * $d;                                                     // eccentricity
    $M = 6.2141924418482506287494932704807 + 0.017201969619332228715501852561964 * $d;     // mean anomaly
    $oblecl = 0.40909295936270689252387465029835 - .0000000062186081248557962825791102081249 * $d; // obliquity

    $L = $w + $M; // sun's mean longitude
    $E = $M + $e * sin($M) * (1 + $e * cos($M));

    $x = cos($E) - $e;
    $y = sin($E) * sqrt(1 - $e * $e);
    $r = sqrt($x * $x + $y * $y);
    $v = atan2($y, $x);
    $lon = $v + $w;

    $x = $r * cos($lon);
    $y = $r * sin($lon);
    $z = 0.0;

    $xequat = $x;
    $yequat = $y * cos($oblecl) + $z * sin($oblecl);
    $zequat = $y * sin($oblecl) + $z * cos($oblecl);

    $RA = atan2($yequat, $xequat);
    $Decl = asin($zequat / $r);
    $RAhours = r2d($RA) / 15;

    $GMST0 = r2d($L + pi()) / 15;
    $SIDTIME = $GMST0 + $hour + rad2deg($LON) / 15;
    $HA = deg2rad(($SIDTIME - $RAhours) * 15);

    $x = cos($HA) * cos($Decl);
    $y = sin($HA) * cos($Decl);
    $z = sin($Decl);

    $xhor = $x * cos(pi() / 2 - $LAT) - $z * sin(pi() / 2 - $LAT);
    $yhor = $y;
    $zhor = $x * sin(pi() / 2 - $LAT) + $z * cos(pi() / 2 - $LAT);

    $azimuth = rad2deg(atan2($yhor, $xhor) + pi());
    $altitude = rad2deg(asin($zhor));
}

function r2d($r) {
    $d = rad2deg($r);
    while ($d < 0) $d += 360;
    while ($d > 360) $d -= 360;
    return $d;
}
