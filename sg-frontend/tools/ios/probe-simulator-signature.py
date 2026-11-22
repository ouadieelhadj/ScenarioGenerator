#!/usr/bin/env python3
"""Check Mach-O reader and local signing tools; never claim this is a runtime test."""
from pathlib import Path
import importlib.util,json,os,platform,plistlib,subprocess,tempfile
F=Path(__file__).resolve().parents[2];OUT=Path(os.environ['FP_IOS_OUTPUT']);OUT.mkdir(exist_ok=True)
report={'toolProtocolOnly':True,'productAppTested':False,'keychainRuntimeTested':False,'deviceInstallation':False,'appleDistributionSignature':False}
spec=importlib.util.spec_from_file_location('fp_simulator_verify',F/'tools/ios/verify-simulator-app.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
try:
 with tempfile.TemporaryDirectory(prefix='fp-xcode-tool-preflight-') as directory:
  root=Path(directory);source=root/'probe.c';source.write_text('int main(void) { return 0; }\n')
  profile=root/'simulated.plist';profile.write_bytes(plistlib.dumps({'application-identifier':'com.moneycore.merchantportal.test'}))
  sdk=subprocess.check_output(['xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True).strip()
  arch=platform.machine();assert arch in {'arm64','x86_64'}
  executable=root/'Probe'
  subprocess.run(['xcrun','--sdk','iphonesimulator','clang','-target',arch+'-apple-ios15.0-simulator','-isysroot',sdk,str(source),'-Wl,-sectcreate,__TEXT,__entitlements,'+str(profile),'-o',str(executable)],check=True,capture_output=True)
  subprocess.run(['codesign','--force','--sign','-',str(executable)],check=True,capture_output=True)
  subprocess.run(['codesign','--verify','--strict',str(executable)],check=True,capture_output=True)
  assert module.simulated_entitlements(executable)=={'application-identifier':'com.moneycore.merchantportal.test'}
  report.update(passed=True,simulatedEntitlementReaderVerified=True,restrictedEntitlementsInsertedInCodeSignature=False)
except Exception as error:
 report.update(passed=False,errorType=type(error).__name__,message=str(error))
 if getattr(error,'stderr',None): report['toolDiagnostic']=error.stderr.decode('utf8','replace')[:2000]
(OUT/'simulator-tool-preflight.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report));raise SystemExit(0 if report.get('passed') else 1)
