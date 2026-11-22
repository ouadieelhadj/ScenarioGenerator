#!/usr/bin/env python3
"""Real XCUITest execution on a fresh simulator. Never publish raw result bundles or credentials."""
from pathlib import Path
import datetime,json,os,plistlib,re,shutil,subprocess,sys,tempfile
F=Path(__file__).resolve().parents[2]
OUT=Path(os.environ.get('FP_IOS_OUTPUT',str(F/'ios/App/output/cloud'))).resolve()
SAFE=OUT/'preuves-publiables';SAFE.mkdir(parents=True,exist_ok=True)
authenticated=os.environ.get('FP_IOS_AUTHENTICATED_TESTS','false')=='true'
focus=os.environ.get('FP_IOS_UI_FOCUS','full')
if focus not in ('full','session_files_reinstall'):raise SystemExit('Unsupported native suite')
if focus!='full' and not authenticated:raise SystemExit('The focused suite requires its authenticated fixture')
expectedCases=5 if focus=='session_files_reinstall' else 9
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
common=['xcodebuild','-project',str(F/'ios/App/App.xcodeproj'),'-scheme','App','-configuration','Release','-derivedDataPath',str(OUT/'DerivedData'),'-destination',destination,'ONLY_ACTIVE_ARCH=YES','CODE_SIGNING_ALLOWED=YES','CODE_SIGN_IDENTITY=-','-parallel-testing-enabled','NO']
report={'suite':focus,'expectedNativeCases':expectedCases,'curatedScreenshotAssociations':[],'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'nativeUIExecutionStarted':False,'authenticatedRequested':authenticated,'simulatorName':chosen['name'],'realIPhoneDeviceTested':False,'fullAcceptanceCompleted':False,'pentestExecuted':False}
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
    # Xcode embeds simulated entitlements in Mach-O and signs to run locally.
    # Never insert iOS restricted entitlements into a macOS code signature:
    # that made launchd reject the preceding simulator candidate (POSIX162).
    appBundle=OUT/'DerivedData/Build/Products/Release-iphonesimulator/App.app'
    signatureReport=OUT/'simulator-signature.json'
    verification=subprocess.run([sys.executable,str(F/'tools/ios/verify-simulator-app.py'),str(appBundle),'--report',str(signatureReport)],capture_output=True)
    if verification.returncode:
        report['simulatorSignatureFailure']=json.loads(signatureReport.read_text()) if signatureReport.exists() else {'message':'Verifier returned no report'}
        raise RuntimeError('Xcode simulator identity not verified; see structured diagnostic')
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
        selector='MerchantUITests/MerchantUITests/'
        base=['xcodebuild','test-without-building','-xctestrun',str(private),'-destination',destination,'-parallel-testing-enabled','NO']
        exclusions=['-skip-testing:'+selector+'test08FreshInstallationRequiresLogin']
        if not authenticated:exclusions+=['-skip-testing:'+selector+'test10SeedSessionForReinstall']
        if focus=='session_files_reinstall':
            exclusions=['-only-testing:'+selector+name for name in ['test04MerchantSessionRestoreAndLogout','test05MerchantIncidentsEntry','test09CancelNativeAttachmentCopy','test10SeedSessionForReinstall']]
        with raw.open('a') as log:
            test=subprocess.run(base+exclusions+['-resultBundlePath',str(bundle)],cwd=F,stdout=log,stderr=subprocess.STDOUT,timeout=720)
        summary=json.loads(run(['xcrun','xcresulttool','get','test-results','summary','--path',str(bundle)]))
        passed=int(summary.get('passedTests',0));failed=int(summary.get('failedTests',0));skipped=int(summary.get('skippedTests',0))
        firstPhaseJournal=raw.read_text(errors='replace')
        seeded=bool(re.search(r"test10SeedSessionForReinstall\]' passed",firstPhaseJournal)) and 'FP_REINSTALL_SEED restored=true' in firstPhaseJournal
        # The target reinstall check is independent of an unrelated first-phase
        # failure; the original failures still count in the final global verdict.
        if authenticated and seeded:
            identifier=report['simulatorSignature']['bundleIdentifier']
            # Inspect only the dedicated candidate's own temporary export directory.
            container=Path(run(['xcrun','simctl','get_app_container',chosen['udid'],identifier,'data']).strip())
            exports=container/'tmp/merchant-incident-exports'
            remaining=sum(1 for item in exports.rglob('*') if item.is_file()) if exports.exists() else 0
            report['nativeExportTemporaryFilesRemaining']=remaining
            report['nativeExportTemporaryCleanupPassed']=remaining==0
            # Only this candidate bundle is uninstalled on the dedicated disposable simulator.
            run(['xcrun','simctl','uninstall',chosen['udid'],identifier])
            run(['xcrun','simctl','install',chosen['udid'],str(appBundle)])
            report['reinstallationPerformed']={'bundle':identifier,'sameCompiledApp':True,'dedicatedSimulatorOnly':True,'physicalDeviceModified':False}
            reinstallBundle=OUT/'reinstallation-results.xcresult'
            with raw.open('a') as log:
                fresh=subprocess.run(base+['-only-testing:'+selector+'test08FreshInstallationRequiresLogin','-resultBundlePath',str(reinstallBundle)],cwd=F,stdout=log,stderr=subprocess.STDOUT,timeout=240)
            second=json.loads(run(['xcrun','xcresulttool','get','test-results','summary','--path',str(reinstallBundle)]))
            passed+=int(second.get('passedTests',0));failed+=int(second.get('failedTests',0));skipped+=int(second.get('skippedTests',0))
            report['reinstallationPassed']=fresh.returncode==0 and int(second.get('passedTests',0))==1 and int(second.get('failedTests',0))==0
            if fresh.returncode:test=fresh
        report.update(passed=passed,failed=failed,skipped=skipped,allExecutedPassed=test.returncode==0 and failed==0 and passed>=3 and report.get('nativeExportTemporaryCleanupPassed',True),authenticatedCasesPassed=authenticated and test.returncode==0 and passed==expectedCases and skipped==0 and report.get('reinstallationPassed',False) and report.get('nativeExportTemporaryCleanupPassed',False))
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
                                labels=[m.group(0) for value in strings for m in [re.search(r'preuve-[a-z0-9-]+',value)] if m]
                                report['curatedScreenshotAssociations'].append({'file':candidate,'labels':sorted(set(labels))})
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
        report['nativeDraftSubjects']=sorted(set(re.findall(r'FP_NATIVE_DRAFT subject=(iOS natif [a-f0-9]{8})',journal)))
        report['loginInputLengthMatches']=[value=='true' for value in re.findall(r'FP_LOGIN_INPUT lengthMatches=(true|false)',journal)]
        report['loginFailureUiCategories']=[int(value) for value in re.findall(r'FP_LOGIN_UI_FAILURE status=(\d+)',journal)]
        report['initialLoginSeconds']=[int(value) for value in re.findall(r'FP_INITIAL_LOGIN seconds=(\d+)',journal)]
        report['reinstallationMarkers']={'sessionSeeded':bool(re.search(r'FP_REINSTALL_SEED restored=true',journal)),'freshLoginRequired':bool(re.search(r'FP_REINSTALL_RESULT loginRequired=true',journal))}
        report['nativeExportCancellationObserved']=bool(re.search(r'FP_NATIVE_EXPORT cancelled=true',journal))
        report['nativePickerControls']=[{'label':label,'inApp':app=='true','inSystemUi':system=='true'} for label,app,system in re.findall(r'FP_PICKER_CONTROL label=(Annuler|Cancel|Enregistrer|Save|OK|Done) app=(true|false) system=(true|false)',journal)]
        if nativeCases:
            report['nativeCaseResultsFromJournal']=nativeCases
            report['passedFromJournal']=sum(case['passed'] for case in nativeCases)
            report['failedFromJournal']=sum(not case['passed'] for case in nativeCases)
            report['nativeJournalIsNotFinalResultBundle']=True
        for evidenceBundle in [bundle,OUT/'reinstallation-results.xcresult']:
            if not evidenceBundle.is_dir():continue
            recovery=OUT/('curated-attachments-recovery-'+evidenceBundle.stem)
            subprocess.run(['xcrun','xcresulttool','export','attachments','--path',str(evidenceBundle),'--output-path',str(recovery)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=120)
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
                                        labels=[m.group(0) for value in strings for m in [re.search(r'preuve-[a-z0-9-]+',value)] if m]
                                        report['curatedScreenshotAssociations'].append({'file':candidate,'labels':sorted(set(labels))})
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
