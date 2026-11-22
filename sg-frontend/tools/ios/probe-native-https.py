from pathlib import Path
import json,os,subprocess,sys
if sys.platform!='darwin':raise SystemExit('Native Apple HTTPS diagnostic requires macOS')
root=Path(__file__).resolve().parents[2]
out=Path(os.environ['FP_IOS_OUTPUT']);out.mkdir(parents=True,exist_ok=True)
binary=out/'probe-native-https'
build=subprocess.run(['xcrun','swiftc','-swift-version','5',str(root/'tools/ios/probe-native-https.swift'),'-o',str(binary)],capture_output=True,timeout=90)
if build.returncode:raise SystemExit('Native HTTPS probe compilation failed; no secret used')
result=subprocess.run([str(binary)],capture_output=True,text=True,timeout=150)
if result.returncode:raise SystemExit('Native HTTPS probe failed; no authentication performed')
data=json.loads(result.stdout)
(out/'diagnostic-https-apple.json').write_text(json.dumps(data,indent=2)+'\n')
print(json.dumps(data))
