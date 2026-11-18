"""Static privacy guard. No network calls or credential values in its output."""
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
a = '{http://schemas.android.com/apk/res/android}'
m = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
assert all(p.get(a + 'name') != 'android.permission.INTERNET' for p in m.findall('uses-permission'))
app = m.find('application')
assert app.get(a + 'allowBackup') == 'false'
assert app.get(a + 'usesCleartextTraffic') == 'false'
assert json.loads((root / 'metadata.json').read_text())['majorCapabilities'] == []
for filename, paths in [('backup_rules.xml', ['.']), ('data_extraction_rules.xml', ['cloud-backup', 'device-transfer'])]:
    tree = ET.parse(root / 'app/src/main/res/xml' / filename).getroot()
    for path in paths:
        element = tree if path == '.' else tree.find(path)
        excludes = {e.get('domain') for e in element.findall('exclude') if e.get('path') == '.'}
        assert {'root', 'file', 'database', 'sharedpref', 'external'} <= excludes
patterns = [r'AIza[0-9A-Za-z_-]{35}', r'gh[pousr]_[A-Za-z0-9]{20,}', r'AKIA[0-9A-Z]{16}', r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----']
for path in list((root / 'app/src').rglob('*')) + [root / 'metadata.json']:
    if path.is_file():
        text = path.read_bytes().decode('utf-8', errors='ignore')
        assert not any(re.search(pattern, text) for pattern in patterns), f'Token-like content in {path.relative_to(root)}'
print('PASS: offline manifest, backup exclusions, no AI capability, no known credential patterns in source')
