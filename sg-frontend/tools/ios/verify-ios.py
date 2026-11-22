#!/usr/bin/env python3
"""Preparation checks only, never claim an Xcode build or a native acceptance."""
from pathlib import Path
import argparse,datetime,json,plistlib,re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--report',type=Path);args=parser.parse_args()
app=root/'ios/App/App';checks=[]
def check(label,condition):
    checks.append({'name':label,'passed':bool(condition)})
    if not condition:raise RuntimeError(label)
info=plistlib.loads((app/'Info.plist').read_bytes())
check('Camera permission description present',bool(info.get('NSCameraUsageDescription')))
check('Photo selection description present',bool(info.get('NSPhotoLibraryUsageDescription')))
check('No global TLS exception',info.get('NSAppTransportSecurity',{}).get('NSAllowsArbitraryLoads') is False)
check('No broad web content TLS exception',info['NSAppTransportSecurity'].get('NSAllowsArbitraryLoadsInWebContent') is False)
check('App documents not exposed globally',info.get('UIFileSharingEnabled') is False)
config=json.loads((app/'capacitor.config.json').read_text())
check('Dedicated test bundle',config['appId']=='com.moneycore.merchantportal.test')
check('Bundled app; no remote WebView',not config.get('server',{}).get('url'))
check('No extra native navigation domains',not config.get('server',{}).get('allowNavigation'))
check('HTTPS required',config['server']['cleartext'] is False)
check('iOS native HTTP enabled',config['plugins']['CapacitorHttp']['enabled'] is True)
check('WebView debugging disabled',config['ios']['webContentsDebuggingEnabled'] is False)
runtime=json.loads((app/'public/assets/merchant-mobile-runtime.json').read_text())
check('Runtime targets actual OCI gateway',runtime=={'version':1,'gatewayUrl':'https://merchant.futurpayment.com/api-merchant-portal'})
privacy=plistlib.loads((app/'PrivacyInfo.xcprivacy').read_bytes())
check('No advertising tracking',privacy['NSPrivacyTracking'] is False and privacy['NSPrivacyTrackingDomains']==[])
check('Privacy records include support and user content',{'NSPrivacyCollectedDataTypeCustomerSupport','NSPrivacyCollectedDataTypeOtherUserContent'}<=set(r['NSPrivacyCollectedDataType'] for r in privacy['NSPrivacyCollectedDataTypes']))
entitlements=plistlib.loads((app/'App.entitlements').read_bytes())
check('Single real associated domain',entitlements['com.apple.developer.associated-domains']==['applinks:merchant.futurpayment.com'])
project=(root/'ios/App/App.xcodeproj/project.pbxproj').read_text()
names=['MerchantOriginPolicy.swift','IncidentExportPolicy.swift','SecureSessionPlugin.swift','IncidentFileDownloadPlugin.swift','MerchantBridgeViewController.swift']
for name in names:check('Xcode source reference '+name,(app/name).is_file() and name+' in Sources' in project)
check('Privacy manifest in Xcode resources','PrivacyInfo.xcprivacy in Resources' in project)
check('No invented Apple Team ID',not re.search(r'DEVELOPMENT_TEAM\s*=\s*[A-Z0-9]{10}',project))
check('Project uses test bundle',project.count('PRODUCT_BUNDLE_IDENTIFIER = com.moneycore.merchantportal.test;')==2)
spm=(root/'ios/App/CapApp-SPM/Package.swift').read_text()
check('Swift package paths portable', '\\' not in spm and 'exact: "8.5.1"' in spm)
for p in (app/'Base.lproj').glob('*.storyboard'):ET.fromstring(p.read_text())
check('Storyboard XML parsed',True)
check('No source maps in bundled release',not list((app/'public').rglob('*.map')))
report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'checks':checks,'preparationPassed':True,'xcodeBuildExecuted':False,'signedIpaProduced':False,'iPhoneRuntimeTested':False,'pentestExecuted':False}
if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
