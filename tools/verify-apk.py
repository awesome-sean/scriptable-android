"""Verify the delivered APK contains the exact final bundled UI/runtime assets."""
import hashlib
import json
from pathlib import Path
import zipfile
import re
import argparse

project = Path(__file__).resolve().parent.parent
build_config=(project/'app/build.gradle').read_text(encoding='utf-8')
version = re.search(r"versionName\s+'([^']+)'", build_config).group(1)
application_id = re.search(r"applicationId\s+'([^']+)'", build_config).group(1)
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk',type=Path,default=project/f'dist/Scriptable-Community-{version}-release.apk')
parser.add_argument('--report',type=Path,default=project/'dist/build-manifest.json')
arguments=parser.parse_args()
apk = arguments.apk.resolve()
assets = project / 'app/src/main/assets'
with zipfile.ZipFile(apk) as archive:
    for asset in assets.rglob('*'):
        if asset.is_file():
            assert archive.read('assets/' + str(asset.relative_to(assets)).replace('\\','/')) == asset.read_bytes(), f'APK asset differs: {asset.name}'
    assert 'classes.dex' in archive.namelist()
    assert 'AndroidManifest.xml' in archive.namelist()
    assert not any(name.endswith(('.jks', '.keystore', 'signing.properties')) for name in archive.namelist())
files = sorted((project / 'app/src/main').rglob('*'))
manifest = {
    'version': version,
    'applicationId': application_id,
    'minimumAndroid': '8.0 (API 26)',
    'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
    'apkBytes': apk.stat().st_size,
    'assetsVerifiedAgainstSource': True,
    'sourceSha256': {str(file.relative_to(project)).replace('\\', '/'): hashlib.sha256(file.read_bytes()).hexdigest() for file in files if file.is_file()},
}
arguments.report.parent.mkdir(parents=True,exist_ok=True)
arguments.report.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
print('Verified final APK assets, manifest, compiled code and absence of signing secrets.')
print('APK SHA256:', manifest['apkSha256'])
