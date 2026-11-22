#!/usr/bin/env python3
"""Ad-hoc signing for this simulator bundle only; never an Apple distribution identity."""
from pathlib import Path
import argparse,hashlib,json,plistlib,subprocess,sys,tempfile
PHASE='preflight'
def command(label,args):
    global PHASE
    PHASE=label
    return subprocess.run(args,capture_output=True,check=True)

def sign(app,output):
    if sys.platform!='darwin':raise RuntimeError('A macOS simulator build is required')
    app=app.resolve(strict=True)
    info=plistlib.loads((app/'Info.plist').read_bytes())
    if info.get('CFBundleSupportedPlatforms')!=['iPhoneSimulator']:
        raise RuntimeError('Refusing to modify a physical-device or distribution build')
    identifier=info.get('CFBundleIdentifier')
    if identifier!='com.moneycore.merchantportal.test':raise RuntimeError('Unexpected test bundle')
    if list(app.glob('PlugIns/*.appex')):
        raise RuntimeError('Unexpected app extension: review its signing separately')
    nested=list(app.glob('Frameworks/*.framework'))+list(app.glob('Frameworks/*.dylib'))
    for component in nested:
        if not component.resolve().is_relative_to(app):
            raise RuntimeError('Embedded simulator component escapes this bundle')
        # Libraries have no session entitlement: grant only the executable app access.
        command('sign embedded library '+component.name,['codesign','--force','--sign','-',str(component)])
    # An unsigned simulator application has no access-group identity and Security
    # returns errSecMissingEntitlement (-34018). Use only this bundle's own group.
    # There is no invented Apple Team ID, shared group, certificate or device profile.
    entitlements={'application-identifier':identifier,'keychain-access-groups':[identifier]}
    with tempfile.TemporaryDirectory(prefix='fp-simulator-sign-') as directory:
        profile=Path(directory)/'simulator.plist';profile.write_bytes(plistlib.dumps(entitlements))
        command('sign simulator executable',['codesign','--force','--sign','-','--entitlements',str(profile),'--generate-entitlement-der',str(app)])
    command('verify simulator bundle',['codesign','--verify','--deep','--strict',str(app)])
    actual=command('read signed simulator entitlements',['codesign','--display','--entitlements','-','--xml',str(app)]).stdout
    signed=plistlib.loads(actual)
    if signed!=entitlements:raise RuntimeError('Simulator entitlements do not match the isolated bundle')
    result={'simulatorOnly':True,'adHocSignatureVerified':True,'appleDistributionSignature':False,
            'physicalDeviceProfileChanged':False,'bundleIdentifier':identifier,
            'keychainGroups':[identifier],'appleTeamIdentifierInvented':False,
            'entitlementsSha256':hashlib.sha256(plistlib.dumps(entitlements)).hexdigest()}
    output.write_text(json.dumps(result,indent=2)+'\n')
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('app',type=Path);parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    try:print(json.dumps(sign(args.app,args.report)))
    except Exception as error:
        detail=getattr(error,'stderr',b'') or b''
        if isinstance(detail,bytes):detail=detail.decode('utf8','replace')
        report={'simulatorOnly':True,'adHocSignatureVerified':False,'appleDistributionSignature':False,
                'phase':PHASE,'errorType':type(error).__name__,'message':str(error),
                'toolDiagnostic':detail[:3000],'credentialsLogged':False}
        args.report.write_text(json.dumps(report,indent=2)+'\n')
        print(json.dumps(report));raise SystemExit(1)
