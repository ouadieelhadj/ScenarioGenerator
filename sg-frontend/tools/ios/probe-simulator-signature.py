#!/usr/bin/env python3
"""Validate the macOS signing-tool protocol before building the product. No installation or session."""
from pathlib import Path
import importlib.util,json,os,platform,plistlib,subprocess,sys,tempfile
F=Path(__file__).resolve().parents[2];OUT=Path(os.environ['FP_IOS_OUTPUT']);OUT.mkdir(exist_ok=True)
report={'toolProtocolOnly':True,'productAppTested':False,'keychainRuntimeTested':False,'deviceInstallation':False,'appleDistributionSignature':False}
spec=importlib.util.spec_from_file_location('fp_simulator_sign',F/'tools/ios/sign-simulator-app.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
try:
 with tempfile.TemporaryDirectory(prefix='fp-signing-tool-preflight-') as directory:
  root=Path(directory);app=root/'Probe.app';app.mkdir()
  source=root/'probe.c';source.write_text('int main(void) { return 0; }\n')
  sdk=subprocess.check_output(['xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True).strip()
  arch=platform.machine();assert arch in {'arm64','x86_64'}
  subprocess.run(['xcrun','--sdk','iphonesimulator','clang','-target',arch+'-apple-ios15.0-simulator','-isysroot',sdk,str(source),'-o',str(app/'Probe')],check=True,capture_output=True)
  (app/'Info.plist').write_bytes(plistlib.dumps({'CFBundleIdentifier':'com.moneycore.merchantportal.test','CFBundleExecutable':'Probe','CFBundlePackageType':'APPL','CFBundleSupportedPlatforms':['iPhoneSimulator']}))
  checked=module.sign(app,root/'signature.json')
  assert checked['adHocSignatureVerified'] and not checked['appleTeamIdentifierInvented']
  report.update(passed=True,expectedOwnBundleKeychainGroupVerified=True)
except Exception as error:
 report.update(passed=False,phase=module.PHASE,errorType=type(error).__name__,message=str(error))
 if getattr(error,'stderr',None):report['toolDiagnostic']=error.stderr.decode('utf8','replace')[:2000]
(OUT/'simulator-tool-preflight.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report));raise SystemExit(0 if report.get('passed') else 1)
