#!/usr/bin/env python3
"""Configure the iOS target only; never write the Android project or shared Capacitor config."""
from pathlib import Path
import argparse,hashlib,json,plistlib,re

frontend=Path(__file__).resolve().parents[2]
app=frontend/'ios/App/App'
parser=argparse.ArgumentParser()
parser.add_argument('--version',default='1.3.10')
parser.add_argument('--build',default='10310')
args=parser.parse_args()
if not re.fullmatch(r'\d+\.\d+\.\d+',args.version) or not re.fullmatch(r'[1-9]\d{0,8}',args.build):
    parser.error('Version/build invalid')
assert app.is_dir(), 'Generate the official Capacitor iOS project first'

def save_plist(name,value):
    (app/name).write_bytes(plistlib.dumps(value,sort_keys=False))

info=plistlib.loads((app/'Info.plist').read_bytes())
info.update(CFBundleDisplayName='FuturPayment Test',CFBundleDevelopmentRegion='fr',
    NSCameraUsageDescription='Prendre une photo, uniquement à votre demande, pour un justificatif ou un incident.',
    NSPhotoLibraryUsageDescription='Sélectionner, uniquement à votre demande, une image à joindre à votre dossier.',
    UIFileSharingEnabled=False,LSSupportsOpeningDocumentsInPlace=False,
    NSAppTransportSecurity={'NSAllowsArbitraryLoads':False,'NSAllowsArbitraryLoadsInWebContent':False})
info['UIRequiredDeviceCapabilities']=['arm64']
# SceneDelegate creates the single bridge window programmatically. Do not also
# instantiate a storyboard window during scene connection.
info.pop('UIMainStoryboardFile',None)
for entries in info.get('UIApplicationSceneManifest',{}).get('UISceneConfigurations',{}).values():
    for configuration in entries:configuration.pop('UISceneStoryboardFile',None)
save_plist('Info.plist',info)
save_plist('App.entitlements',{'com.apple.developer.associated-domains':['applinks:merchant.futurpayment.com']})
types=['UserID','EmailAddress','Name','PhoneNumber','PhysicalAddress','OtherFinancialInfo','PhotosorVideos','OtherUserContent','CustomerSupport']
save_plist('PrivacyInfo.xcprivacy',{
    'NSPrivacyTracking':False,'NSPrivacyTrackingDomains':[], 'NSPrivacyAccessedAPITypes':[],
    'NSPrivacyCollectedDataTypes':[{'NSPrivacyCollectedDataType':'NSPrivacyCollectedDataType'+t,
      'NSPrivacyCollectedDataTypeLinked':True,'NSPrivacyCollectedDataTypeTracking':False,
      'NSPrivacyCollectedDataTypePurposes':['NSPrivacyCollectedDataTypePurposeAppFunctionality']} for t in types]})

config_path=app/'capacitor.config.json'
config=json.loads(config_path.read_text())
config.update(appId='com.moneycore.merchantportal.test',loggingBehavior='none')
config.setdefault('server',{}).update(iosScheme='capacitor',hostname='localhost',cleartext=False)
config['server'].pop('url',None);config['server'].pop('allowNavigation',None)
config.setdefault('ios',{}).update(webContentsDebuggingEnabled=False,contentInset='never')
config.setdefault('plugins',{}).setdefault('CapacitorHttp',{})['enabled']=True
config_path.write_text(json.dumps(config,indent=2)+'\n')

sources=['MerchantOriginPolicy.swift','IncidentExportPolicy.swift','IncidentExportStorage.swift','SecureSessionPlugin.swift',
         'IncidentFileDownloadPlugin.swift','MerchantBridgeViewController.swift']
resources=['PrivacyInfo.xcprivacy']
project=frontend/'ios/App/App.xcodeproj/project.pbxproj'
text=project.read_text()
def ident(name,role):return hashlib.sha1(('futurpayment-iphone:'+role+':'+name).encode()).hexdigest()[:24].upper()
for name in sources+resources:
    assert (app/name).is_file(),name
    ref=ident(name,'ref');build=ident(name,'build');group='Sources' if name in sources else 'Resources'
    if ref not in text:
        kind='sourcecode.swift' if name.endswith('.swift') else 'text.xml'
        text=text.replace('/* Begin PBXFileReference section */','/* Begin PBXFileReference section */\n\t\t'+ref+' /* '+name+' */ = {isa = PBXFileReference; lastKnownFileType = '+kind+'; path = '+name+'; sourceTree = "<group>"; };')
        text=text.replace('/* Begin PBXBuildFile section */','/* Begin PBXBuildFile section */\n\t\t'+build+' /* '+name+' in '+group+' */ = {isa = PBXBuildFile; fileRef = '+ref+' /* '+name+' */; };')
        text=text.replace('504EC3061FED79650016851F /* App */ = {\n\t\t\tisa = PBXGroup;\n\t\t\tchildren = (',
                          '504EC3061FED79650016851F /* App */ = {\n\t\t\tisa = PBXGroup;\n\t\t\tchildren = (\n\t\t\t\t'+ref+' /* '+name+' */,')
        phase='504EC3001FED79650016851F' if name in sources else '504EC3021FED79650016851F'
        pattern='('+phase+r' /\* '+group+r' \*/ = \{.*?files = \()'
        text,count=re.subn(pattern,lambda m:m.group(1)+'\n\t\t\t\t'+build+' /* '+name+' in '+group+' */,',text,count=1,flags=re.S)
        assert count==1
text=re.sub(r'CURRENT_PROJECT_VERSION = [^;]+;',f'CURRENT_PROJECT_VERSION = {args.build};',text)
text=re.sub(r'MARKETING_VERSION = [^;]+;',f'MARKETING_VERSION = {args.version};',text)
text=re.sub(r'PRODUCT_BUNDLE_IDENTIFIER = com\.moneycore\.merchantportal(?:\.test)?;','PRODUCT_BUNDLE_IDENTIFIER = com.moneycore.merchantportal.test;',text)
text=text.replace('INFOPLIST_FILE = App/Info.plist;','INFOPLIST_FILE = App/Info.plist;\n\t\t\t\tCODE_SIGN_ENTITLEMENTS = App/App.entitlements;' if 'CODE_SIGN_ENTITLEMENTS' not in text else 'INFOPLIST_FILE = App/Info.plist;')
text=text.replace('TARGETED_DEVICE_FAMILY = "1,2";','TARGETED_DEVICE_FAMILY = 1;')
project.write_text(text)

scene=app/'SceneDelegate.swift'
scene.write_text(scene.read_text().replace('window?.rootViewController = CAPBridgeViewController()','window?.rootViewController = MerchantBridgeViewController()'))
storyboard=app/'Base.lproj/Main.storyboard'
storyboard.write_text(storyboard.read_text().replace('customClass="CAPBridgeViewController" customModule="Capacitor"','customClass="MerchantBridgeViewController" customModule="App" customModuleProvider="target"'))
spm=frontend/'ios/App/CapApp-SPM/Package.swift'
spm.write_text(spm.read_text().replace('\\','/'))

# The HTML remains bundled, HTTPS API is fixed by the signed public runtime config.
index=app/'public/index.html'
if index.is_file():
    html=index.read_text()
    html=re.sub(r'<link\b[^>]*href=["\']https://fonts\.googleapis\.com[^>]*>', '',html)
    html=html.replace('width=device-width, initial-scale=1','width=device-width, initial-scale=1, viewport-fit=cover')
    html=re.sub(r'<title>[^<]*</title>','<title>FuturPayment Merchant</title>',html)
    index.write_text(html)
print('IOS_CONFIGURATION=READY | NATIVE_BUILD=NOT_RUN | VERSION='+args.version+' | BUILD='+args.build)
