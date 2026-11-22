"""Encrypt curated UI evidence before uploading it from a public GitHub repository."""
from pathlib import Path
import hashlib,json,os,subprocess,tarfile,tempfile
F=Path(__file__).resolve().parents[2]
O=Path(os.environ['FP_IOS_OUTPUT']).resolve()
safe=O/'preuves-publiables'
if not safe.is_dir():
    print('NO_UI_EVIDENCE_TO_ENCRYPT');raise SystemExit(0)
certificate=F.parent/'.github/iphone-evidence-public.pem'
with tempfile.NamedTemporaryFile(prefix='fp-evidence-',suffix='.tar.gz',dir=O,delete=False) as stream:
    archive=Path(stream.name)
try:
    with tarfile.open(archive,'w:gz') as tar:
        for path in sorted(safe.rglob('*')):
            if path.is_file() and not path.is_symlink():tar.add(path,arcname=str(path.relative_to(safe)),recursive=False)
    target=O/'preuves-iphone-chiffrees.cms'
    subprocess.run(['openssl','cms','-encrypt','-binary','-aes256','-in',str(archive),'-outform','DER','-out',str(target),str(certificate)],check=True)
    (O/'preuves-chiffrees-manifeste.json').write_text(json.dumps({'file':target.name,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'encrypted':True,'rawXCTestResultsIncluded':False,'privateDecryptionKeyInRepository':False},indent=2)+'\n')
    print('CURATED_UI_EVIDENCE_ENCRYPTED')
finally:archive.unlink(missing_ok=True)
