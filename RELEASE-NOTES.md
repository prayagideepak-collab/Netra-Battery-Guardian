# Netra Battery Guardian 1.0.1

Guardian-only device-data correction after the new dashboard update. Package
`com.aistudio.netrabattery.kxmzqr`, versionCode 2, Android 7.0 (API 24) or newer.

- Unsupported numeric readings are blank or omitted. Real zero remains zero.
- No fixed 4500 mAh fallback, Doze savings formula, battery-health score, inferred
  wear/design capacity, or invented storage-category percentages.
- Counter-derived capacity appears only with observed charge counter and battery
  percentage, labeled approximate. It is not rated or design capacity.
- Signed battery current and derived battery-side power retain the sensor sign.
  They do not measure wall-charger input power.
- Thermal alerts/classifications use Android-reported thermal or overheat states,
  not a claimed universal 45/46-degree OEM limit. Numeric temperature remains a
  sensor observation; charts do not invent device safety bands.
- Storage totals come from filesystem observations. Only Netra cache is attributed;
  remaining used bytes are unattributed.
- Session gain updates and database upgrade schema are corrected. Reset clears
  app-private reports and cancels queued CSV work. CSV text is escaped against
  spreadsheet formulas. Legacy guessed health fields are not re-exported as facts.
- Existing night-silent and screen-off voice policy is retained.

## Verification and limits

The signed candidate must be read back before publication. Test renders use
controlled fixtures, not this phone's readings. Physical-phone install/upgrade,
launcher/widget resizing, background speech routing and document-picker export
acceptance are NOT RUN. API support varies by phone. No guaranteed cooling,
battery savings, zero battery impact or Android OS security-patch claim is made.

Uses the established Netra release signing certificate. A prior AI Studio build
may have a different certificate; Android can require uninstalling it first,
which can remove local data. No cross-app history import is claimed.
