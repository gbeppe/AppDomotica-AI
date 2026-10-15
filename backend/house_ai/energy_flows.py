"""Deterministic energy flows from the four live powers (no meter measures them directly).

Signs as in the Digital Twin: grid > 0 import, battery > 0 discharge. Solar feeds the house
first, then the charging battery, then the grid; the house is then fed by the discharging
battery and finally by the grid; leftovers cover the rare grid-charges-battery and
battery-to-grid cases. Meters never balance exactly: within the tolerance the sources are
scaled to the measured sinks, so the flows match the consumption, charge and export just
spoken; above it nothing is adjusted and the imbalance is reported.
"""

IDLE_W = 50.0
# Sources and sinks differ by meter skew; beyond this the data are not mutually consistent.
TOLERANCE_W = 150.0
TOLERANCE_RATIO = 0.05
FLOWS = ("solar_to_home", "solar_to_battery", "solar_to_grid", "battery_to_home",
         "battery_to_grid", "grid_to_home", "grid_to_battery")


def active(watts):
    return watts if watts >= IDLE_W else 0.0


def allocate(solar, battery, grid, home):
    solar, home = active(max(0.0, solar)), active(max(0.0, home))
    charge, discharge = active(max(0.0, -battery)), active(max(0.0, battery))
    export, imported = active(max(0.0, -grid)), active(max(0.0, grid))
    sources, sinks = solar + discharge + imported, home + charge + export
    imbalance = sources - sinks
    consistent = abs(imbalance) <= max(TOLERANCE_W, TOLERANCE_RATIO * max(sources, sinks))
    if consistent and sources > 0:
        factor = sinks / sources
        solar, discharge, imported = solar * factor, discharge * factor, imported * factor
    flows = dict.fromkeys(FLOWS, 0.0)
    flows["solar_to_home"] = min(solar, home)
    flows["solar_to_battery"] = min(solar - flows["solar_to_home"], charge)
    flows["solar_to_grid"] = min(solar - flows["solar_to_home"] - flows["solar_to_battery"], export)
    home_left = home - flows["solar_to_home"]
    flows["battery_to_home"] = min(discharge, home_left)
    flows["grid_to_home"] = min(imported, home_left - flows["battery_to_home"])
    flows["grid_to_battery"] = min(imported - flows["grid_to_home"], charge - flows["solar_to_battery"])
    flows["battery_to_grid"] = min(discharge - flows["battery_to_home"], export - flows["solar_to_grid"])
    return {"flows": {k: round(v) for k, v in flows.items() if v >= IDLE_W},
            "imbalance_w": round(imbalance), "consistent": consistent}
