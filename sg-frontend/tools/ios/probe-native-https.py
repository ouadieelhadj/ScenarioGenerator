from pathlib import Path
import argparse,json,os,platform,subprocess,sys
if sys.platform!='darwin':raise SystemExit('Native Apple HTTPS diagnostic requires macOS')
parser=argparse.ArgumentParser();parser.add_argument('--simulator',action='store_true');args=parser.parse_args()
root=Path(__file__).resolve().parents[2]
out=Path(os.environ['FP_IOS_OUTPUT']);out.mkdir(parents=True,exist_ok=True)
binary=out/'probe-native-https'
command=['xcrun','swiftc','-swift-version','5']
if args.simulator:
 sdk=subprocess.check_output(['xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True).strip()
 arch=platform.machine();assert arch in ('arm64','x86_64')
 command+=['-target',arch+'-apple-ios16.0-simulator','-sdk',sdk]
command+=[str(root/'tools/ios/probe-native-https.swift'),'-o',str(binary)]
build=subprocess.run(command,capture_output=True,timeout=90)
if build.returncode:raise SystemExit('Native HTTPS probe compilation failed; no secret used')
launch=[str(binary)]
if args.simulator:
 simulator=json.loads((out/'simulator.json').read_text())
 subprocess.run(['codesign','--force','--sign','-',str(binary)],check=True,capture_output=True,timeout=30)
 launch=['xcrun','simctl','spawn',simulator['udid'],str(binary)]
result=subprocess.run(launch,capture_output=True,text=True,timeout=160)
if result.returncode:raise SystemExit('Native HTTPS probe failed; no authentication performed')
data=json.loads(result.stdout)
assert data['nativeIPhoneSimulatorURLSession']==args.simulator
name='diagnostic-https-apple-simulateur.json' if args.simulator else 'diagnostic-https-apple.json'
(out/name).write_text(json.dumps(data,indent=2)+'\n')
print(json.dumps(data))
