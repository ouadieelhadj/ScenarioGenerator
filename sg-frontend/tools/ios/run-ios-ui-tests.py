#!/usr/bin/env python3
"""Real XCUITest execution on a fresh simulator. Never publish raw result bundles or credentials."""
from pathlib import Path
import datetime,json,os,plistlib,re,shutil,subprocess,sys,tempfile
F=Path(__file__).resolve().parents[2]
OUT=Path(os.environ.get('FP_IOS_OUTPUT',str(F/'ios/App/output/cloud'))).resolve()
SAFE=OUT/'preuves-publiables';SAFE.mkdir(parents=True,exist_ok=True)
authenticated=os.environ.get('FP_IOS_AUTHENTICATED_TESTS','false')=='true'
password=os.environ.get('FP_IOS_TEST_PASSWORD','')
if authenticated and not password:raise SystemExit('Secret GitHub FP_IOS_TEST_PASSWORD requis pour les tests authentifiés.')
def run(args,timeout=180):return subprocess.check_output(args,text=True,timeout=timeout)
def redacted(value):
    if password:value=value.replace(password,'[REDACTED]')
    value=re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[JWT_REDACTED]',value)
    return re.sub(r'([?#&](?:token|code)=)[^\s"<>]+',r'\1[REDACTED]',value,flags=re.I)
listing=json.loads(run(['xcrun','simctl','list','devices','available','--json']))
requested=json.loads((OUT/'simulator.json').read_text()) if (OUT/'simulator.json').is_file() else None
chosen=next((d for runtime,devices in listing['devices'].items() if 'iOS' in runtime for d in devices if d.get('isAvailable') and d['name'].startswith('iPhone') and (not requested or d['udid']==requested['udid'])),None)
if not chosen:raise SystemExit('Aucun simulateur iPhone utilisable sur ce runner.')
destination='platform=iOS Simulator,id='+chosen['udid']
common=['xcodebuild','-project',str(F/'ios/App/App.xcodeproj'),'-scheme','App','-configuration','Release','-derivedDataPath',str(OUT/'DerivedData'),'-destination',destination,'CODE_SIGNING_ALLOWED=NO','-parallel-testing-enabled','NO']
report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'nativeUIExecutionStarted':False,'authenticatedRequested':authenticated,'simulatorName':chosen['name'],'realIPhoneDeviceTested':False,'fullAcceptanceCompleted':False,'pentestExecuted':False}
raw=OUT/'xcode-ui-private.log'
bundle=OUT/'native-results.xcresult'
rc=1
try:
    with raw.open('w') as log:
        boot=subprocess.run(['xcrun','simctl','bootstatus',chosen['udid'],'-b'],stdout=log,stderr=subprocess.STDOUT,timeout=480)
        if boot.returncode:raise RuntimeError('Simulator boot not confirmed')
        report['simulatorBootConfirmed']=True
        build=subprocess.run(common+['build-for-testing'],cwd=F,stdout=log,stderr=subprocess.STDOUT,timeout=600)
    if build.returncode:raise RuntimeError('Build-for-testing failed')
    # Xcode builds above are unsigned and cannot access Keychain. Sign only the
    # simulator app with its own access group, without an Apple distribution identity.
    appBundle=OUT/'DerivedData/Build/Products/Release-iphonesimulator/App.app'
    signatureReport=OUT/'simulator-signature.json'
    signature=subprocess.run([sys.executable,str(F/'tools/ios/sign-simulator-app.py'),str(appBundle),'--report',str(signatureReport)],capture_output=True)
    if signature.returncode:
        report['simulatorSignatureFailure']=json.loads(signatureReport.read_text()) if signatureReport.exists() else {'message':'Signature tool did not return a report'}
        raise RuntimeError('Simulator signing failed; see structured signature diagnostic')
    report['simulatorSignature']=json.loads(signatureReport.read_text())
    runs=list((OUT/'DerivedData/Build/Products').glob('*.xctestrun'))
    if len(runs)!=1:raise RuntimeError('No unique xctestrun file')
    data=plistlib.loads(runs[0].read_bytes());targets=[]
    for key,value in data.items():
        if isinstance(value,dict) and 'MerchantUITests' in key:targets.append(value)
    for configuration in data.get('TestConfigurations',[]):targets.extend(t for t in configuration.get('TestTargets',[]) if t.get('BlueprintName')=='MerchantUITests')
    if not targets:raise RuntimeError('UI test target missing from xctestrun')
    for target in targets:
        target.setdefault('EnvironmentVariables',{}).update(FP_IOS_AUTHENTICATED_TESTS='true' if authenticated else 'false',FP_IOS_TEST_USER=os.environ.get('FP_IOS_TEST_USER','bim.amina'),FP_IOS_TEST_PASSWORD=password)
    with tempfile.NamedTemporaryFile(prefix='fp-ui-',suffix='.xctestrun',dir=OUT/'DerivedData/Build/Products',delete=False) as file:
        private=Path(file.name);file.write(plistlib.dumps(data))
    private.chmod(0o600)
    bundle=OUT/'native-results.xcresult'
    try:
        report['nativeUIExecutionStarted']=True
        with raw.open('a') as log:
            test=subprocess.run(['xcodebuild','test-without-building','-xctestrun',str(private),'-destination',destination,'-parallel-testing-enabled','NO','-resultBundlePath',str(bundle)],cwd=F,stdout=log,stderr=subprocess.STDOUT,timeout=600)
        summary=json.loads(run(['xcrun','xcresulttool','get','test-results','summary','--path',str(bundle)]))
        passed=int(summary.get('passedTests',0));failed=int(summary.get('failedTests',0));skipped=int(summary.get('skippedTests',0))
        report.update(passed=passed,failed=failed,skipped=skipped,allExecutedPassed=test.returncode==0 and failed==0 and passed>=3,authenticatedCasesPassed=authenticated and test.returncode==0 and passed==5 and skipped==0)
        rc=0 if report['allExecutedPassed'] and (not authenticated or report['authenticatedCasesPassed']) else 1
        # Curated attachments only; raw XCTest result bundles may contain input activity.
        attachments=OUT/'private-attachments'
        subprocess.run(['xcrun','xcresulttool','export','attachments','--path',str(bundle),'--output-path',str(attachments)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=120)
        # Export only attachments explicitly created after password reset or on public screens.
        # Never export framework failure attachments or input-activity result bundles.
        images=[]
        def inspect(value):
            if isinstance(value,dict):
                strings=[s for s in value.values() if isinstance(s,str)]
                if any('preuve-' in s for s in strings):
                    for candidate in strings:
                        if candidate.lower().endswith(('.png','.jpg','.jpeg')) and Path(candidate).name==candidate:
                            image=attachments/candidate
                            if image.is_file():
                                target=SAFE/'captures'/candidate;target.parent.mkdir(exist_ok=True)
                                shutil.copy2(image,target);images.append(candidate)
                for child in value.values():inspect(child)
            elif isinstance(value,list):
                for child in value:inspect(child)
        for manifest in attachments.glob('*.json'):
            try:inspect(json.loads(manifest.read_text()))
            except ValueError:pass
        report.update(screenshotsAwaitingReview=True,curatedScreenshotFiles=sorted(set(images)))
    finally:private.unlink(missing_ok=True)
except Exception as error:
    report['failure']=redacted(str(error))
finally:
    if raw.is_file():
        journal=redacted(raw.read_text(errors='replace'))
        (SAFE/'journal-expurge.txt').write_text(journal,encoding='utf8')
        nativeCases=[{'name':name,'passed':state=='passed'} for name,state in re.findall(r"Test Case '-\[MerchantUITests\.MerchantUITests ([^]]+)\]' (passed|failed) \(",journal)]
        if nativeCases:
            report['nativeCaseResultsFromJournal']=nativeCases
            report['passedFromJournal']=sum(case['passed'] for case in nativeCases)
            report['failedFromJournal']=sum(not case['passed'] for case in nativeCases)
            report['nativeJournalIsNotFinalResultBundle']=True
        if bundle.is_dir():
            recovery=OUT/'curated-attachments-recovery'
            subprocess.run(['xcrun','xcresulttool','export','attachments','--path',str(bundle),'--output-path',str(recovery)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=120)
            for metadata in recovery.glob('*.json'):
                try: document=json.loads(metadata.read_text())
                except ValueError:continue
                def recover(value):
                    if isinstance(value,dict):
                        strings=[v for v in value.values() if isinstance(v,str)]
                        if any('preuve-' in v for v in strings):
                            for candidate in strings:
                                if candidate.lower().endswith(('.png','.jpg','.jpeg')) and Path(candidate).name==candidate:
                                    source=recovery/candidate
                                    if source.is_file():
                                        destination=SAFE/'captures'/candidate;destination.parent.mkdir(exist_ok=True)
                                        shutil.copy2(source,destination)
                        for child in value.values():recover(child)
                    elif isinstance(value,list):
                        for child in value:recover(child)
                recover(document)
            report['curatedScreenshotFiles']=sorted(p.name for p in (SAFE/'captures').glob('*') if p.is_file())
            report['screenshotsAwaitingReview']=bool(report['curatedScreenshotFiles'])
    try:
        diagnostic = subprocess.run(['xcrun','simctl','spawn',chosen['udid'],'log','show','--last','20m','--style','compact','--predicate','eventMessage BEGINSWITH "FP_SESSION_DIAGNOSTIC"'],capture_output=True,text=True,timeout=60)
        if diagnostic.returncode == 0:
            codes = re.findall(r'FP_SESSION_DIAGNOSTIC operation=([a-zA-Z]+) code=(-?\d+)',diagnostic.stdout)
            report['nativeSessionDiagnostic']=[{'operation':name,'code':int(code)} for name,code in codes]
    except subprocess.TimeoutExpired:
        report['nativeSessionDiagnosticUnavailable']='log timeout; native test verdict preserved'
    (SAFE/'resultats-ui.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report));raise SystemExit(rc)
