"""Compile the actual iPhone SDK target, without provisioning or distribution claims."""
from pathlib import Path
import datetime,hashlib,json,os,plistlib,subprocess,sys
assert sys.platform=='darwin','macOS and Xcode required'
F=Path(__file__).resolve().parents[2]
O=Path(os.environ['FP_IOS_OUTPUT']);O.mkdir(parents=True,exist_ok=True)
assert (F/'ios/App/App/public/index.html').is_file(),'Run the normal prepare step first'
report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sdk':'iphoneos','signingAllowed':False,'appleProvisioningUsed':False,'signedIpaProduced':False,'installedOnDevice':False,'physicalIphoneTested':False}
command=['xcodebuild','-project',str(F/'ios/App/App.xcodeproj'),'-scheme','App','-configuration','Release','-derivedDataPath',str(O/'DeviceDerivedData'),'-destination','generic/platform=iOS','CODE_SIGNING_ALLOWED=NO','CODE_SIGNING_REQUIRED=NO','build']
with (O/'device-sdk-build.log').open('w') as log:r=subprocess.run(command,cwd=F,stdout=log,stderr=subprocess.STDOUT,timeout=900)
report['buildExitCode']=r.returncode
if r.returncode==0:
 app=O/'DeviceDerivedData/Build/Products/Release-iphoneos/App.app'
 info=plistlib.loads((app/'Info.plist').read_bytes())
 assert info['CFBundleSupportedPlatforms']==['iPhoneOS']
 assert info['CFBundleIdentifier']=='com.moneycore.merchantportal.test'
 binary=app/info['CFBundleExecutable']
 architectures=subprocess.check_output(['xcrun','lipo','-archs',str(binary)],text=True).strip().split()
 assert architectures==['arm64']
 report.update(compiledForPhysicalDeviceSdk=True,bundleIdentifier=info['CFBundleIdentifier'],version=info['CFBundleShortVersionString'],build=info['CFBundleVersion'],architectures=architectures,executableSha256=hashlib.sha256(binary.read_bytes()).hexdigest(),installableOnIphone=False)
else:report['compiledForPhysicalDeviceSdk']=False
(O/'device-sdk-check.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report));raise SystemExit(r.returncode)
