"""Last-resort delivery of the SAME encrypted curated evidence through job logs.

No raw screenshots, application package, credentials or private key are printed.
This changes proof delivery only; it cannot turn a failed native test into PASS.
"""
from pathlib import Path
import base64,hashlib,json,os,sys
root=Path(os.environ['FP_IOS_OUTPUT']).resolve();assert root.name=='preuves-mac'
safe=root/'preuves-publiables';assert safe.is_dir()
for p in safe.rglob('*'):
    if p.is_file():assert p.suffix.lower() in {'.json','.txt','.png','.jpg','.log'},'Binary or key material cannot enter this fallback.'
capsule=root/'preuves-iphone-chiffrees.cms';manifest=json.loads((root/'preuves-chiffrees-manifeste.json').read_text())
content=capsule.read_bytes();assert 0<len(content)<=16*1024*1024
assert manifest['encrypted'] is True and manifest['rawXCTestResultsIncluded'] is False
assert manifest['sha256']==hashlib.sha256(content).hexdigest()
encoded=base64.b64encode(content).decode('ascii');chunks=[encoded[i:i+2048] for i in range(0,len(encoded),2048)]
header={'version':1,'encrypted':True,'bytes':len(content),'sha256':manifest['sha256'],'chunks':len(chunks),'rawXCTestResultsIncluded':False,'applicationPackageIncluded':False}
print('FP_ENCRYPTED_EVIDENCE_BEGIN '+json.dumps(header),flush=True)
for i,chunk in enumerate(chunks):print('FP_ENCRYPTED_EVIDENCE_CHUNK '+str(i)+' '+chunk)
print('FP_ENCRYPTED_EVIDENCE_END '+manifest['sha256'],flush=True)
