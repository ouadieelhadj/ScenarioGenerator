#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,re,urllib.request,zipfile
ROOT=Path('D:/FuturPayment/tools/github-cli');ROOT.mkdir(parents=True,exist_ok=True)
def fetch(url):
    req=urllib.request.Request(url,headers={'User-Agent':'FuturPayment-iPhone-Preparation','Accept':'application/vnd.github+json' if 'api.github.com' in url else '*/*'})
    with urllib.request.urlopen(req,timeout=60) as response:return response.read()
release=json.loads(fetch('https://api.github.com/repos/cli/cli/releases/latest'))
tag=release['tag_name'];assert re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+',tag)
asset=next(a for a in release['assets'] if a['name'].endswith('_windows_amd64.zip'))
checksums=next(a for a in release['assets'] if a['name'].endswith('_checksums.txt'))
assert all(a['browser_download_url'].startswith('https://github.com/cli/cli/releases/download/') for a in [asset,checksums])
checksum_text=fetch(checksums['browser_download_url']).decode()
expected=next(line.split()[0] for line in checksum_text.splitlines() if line.split()[-1]==asset['name'])
folder=ROOT/tag;folder.mkdir(exist_ok=True)
archive=folder/asset['name']
if not archive.exists():archive.write_bytes(fetch(asset['browser_download_url']))
assert hashlib.sha256(archive.read_bytes()).hexdigest()==expected,'Official archive checksum mismatch'
with zipfile.ZipFile(archive) as zipped:
    for member in zipped.infolist():assert (folder/member.filename).resolve().is_relative_to(folder.resolve())
    zipped.extractall(folder)
executables=list(folder.rglob('gh.exe'));assert len(executables)==1
metadata={'version':tag,'officialRelease':release['html_url'],'archiveSha256':expected,'verifiedAgainstOfficialChecksums':True}
(folder/'integrite.json').write_text(json.dumps(metadata,indent=2)+'\n')
print(executables[0])
