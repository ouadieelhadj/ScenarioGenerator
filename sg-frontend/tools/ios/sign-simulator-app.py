#!/usr/bin/env python3
"""Ad-hoc signing for this simulator bundle only; never an Apple distribution identity."""
from pathlib import Path
import argparse,hashlib,json,plistlib,subprocess,sys,tempfile

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
        subprocess.run(['codesign','--force','--sign','-',str(component)],capture_output=True,check=True)
    # An unsigned simulator application has no access-group identity and Security
    # returns errSecMissingEntitlement (-34018). Use only this bundle's own group.
    # There is no invented Apple Team ID, shared group, certificate or device profile.
    entitlements={'application-identifier':identifier,'keychain-access-groups':[identifier]}
    with tempfile.TemporaryDirectory(prefix='fp-simulator-sign-') as directory:
        profile=Path(directory)/'simulator.plist';profile.write_bytes(plistlib.dumps(entitlements))
        subprocess.run(['codesign','--force','--sign','-','--entitlements',str(profile),'--generate-entitlement-der',str(app)],capture_output=True,check=True)
    subprocess.run(['codesign','--verify','--deep','--strict',str(app)],capture_output=True,check=True)
    actual=subprocess.run(['codesign','--display','--entitlements','-',str(app)],capture_output=True,check=True).stdout
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
    args=parser.parse_args();print(json.dumps(sign(args.app,args.report)))
