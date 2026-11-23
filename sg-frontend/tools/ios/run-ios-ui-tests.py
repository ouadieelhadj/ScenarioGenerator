#!/usr/bin/env python3
"""Real XCUITest execution on a fresh simulator. Never publish raw result bundles or credentials."""
from pathlib import Path
import datetime,json,os,plistlib,re,shutil,subprocess,sys,tempfile
F=Path(__file__).resolve().parents[2]
OUT=Path(os.environ.get('FP_IOS_OUTPUT',str(F/'ios/App/output/cloud'))).resolve()
SAFE=OUT/'preuves-publiables';SAFE.mkdir(parents=True,exist_ok=True)
authenticated=os.environ.get('FP_IOS_AUTHENTICATED_TESTS','false')=='true'
focus=os.environ.get('FP_IOS_UI_FOCUS','full')
if focus not in ('full','session_files_reinstall','authenticated','connection','incident_completion','isolation','upload','attachment','onboarding_readonly','onboarding_request','onboarding_draft','onboarding_documents','framework_validation'):raise SystemExit('Unsupported native suite')
if focus!='full' and not authenticated:raise SystemExit('The focused suite requires its authenticated fixture')
uploadSource=os.environ.get('FP_IOS_UPLOAD_SOURCE','photos')
if uploadSource not in ('photos','files'):raise SystemExit('Unsupported upload source')
expectedCases=3 if focus in ('onboarding_documents','framework_validation') else 1 if focus in ('onboarding_request','onboarding_draft') else 2 if focus=='onboarding_readonly' else (2 if uploadSource=='files' else 1) if focus=='attachment' else (5 if uploadSource=='files' else 4) if focus=='upload' else 3 if focus=='isolation' else 4 if focus=='incident_completion' else 2 if focus=='connection' else 5 if focus=='session_files_reinstall' else 7 if focus=='authenticated' else 14
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
    uploadFixture=None
    if authenticated and focus in ('full','upload','attachment'):
        import importlib.util
        uploadSpec=importlib.util.spec_from_file_location('native_upload_fixture',F/'tools/ios/prepare-native-upload-fixture.py')
        uploadModule=importlib.util.module_from_spec(uploadSpec);uploadSpec.loader.exec_module(uploadModule)
        uploadFixture=uploadModule.prepare(SAFE/'native-upload-fixture.json',password,chosen['udid'],OUT/'private-synthetic-media')
        report['nativeUploadFixturePrepared']=True
        report['nativeUploadSource']=uploadSource
    supportFixture=None
    if authenticated and focus in ('full','authenticated'):
        import importlib.util
        spec=importlib.util.spec_from_file_location('native_support_fixtures',F/'tools/ios/prepare-native-support-fixtures.py')
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        supportFixture=module.prepare(SAFE/'native-support-fixtures.json',password)
        report['supportFixturesPrepared']=True
    draftContext=None
    if focus=='onboarding_draft':
        import importlib.util
        spec=importlib.util.spec_from_file_location('native_dossier_draft',F/'tools/ios/prepare-native-onboarding-draft.py')
        draftModule=importlib.util.module_from_spec(spec);spec.loader.exec_module(draftModule)
        draftContext=draftModule.prepare(SAFE/'native-dossier-preflight.json',password)
        report['nativeDossierPreflightPassed']=True
        report['nativeDossierExpectedFields']=len(draftContext['fixture']['companyFields'])+len(draftContext['fixture']['outletFields'])
    kycContext=None
    if focus=='onboarding_documents':
        import importlib.util
        kycSpec=importlib.util.spec_from_file_location('native_dossier_kyc',F/'tools/ios/prepare-native-onboarding-documents.py')
        kycModule=importlib.util.module_from_spec(kycSpec);kycSpec.loader.exec_module(kycModule)
        kycContext=kycModule.prepare(SAFE/'native-kyc-preflight.json',password)
        report['nativeKycPreflightPassed']=True
        report['nativeKycExpectedUploads']=len(kycContext['documents'])
    frameworkContext=None
    if focus=='framework_validation':
        import importlib.util
        moduleSpec=importlib.util.spec_from_file_location('framework_saved_dossier',F/'tools/ios/prepare-native-onboarding-draft.py')
        frameworkModule=importlib.util.module_from_spec(moduleSpec);moduleSpec.loader.exec_module(frameworkModule)
        fixture=frameworkModule.load_fixture();token=frameworkModule.authenticated(fixture,password)
        own=frameworkModule.request('/api-merchant-portal/api/merchant-onboarding/v1/dossiers/verified-mine',token)
        full=frameworkModule.request('/api-merchant-portal/api/merchant-onboarding/v2/dossiers/'+fixture['id'],token)
        assert own['id']==fixture['id'] and own['acquirerId']=='DAMANCASH' and full['legalName']==fixture['legalName'] and full['version']>=1
        frameworkContext={'login':fixture['login'],'reference':fixture['reference']}
        frameworkVersionBefore=full['version'];token=''
        report['frameworkSavedDossierVersionBefore']=frameworkVersionBefore
        report['frameworkWebExecutionStarted']=True
        web=subprocess.run(['node',str(F/'tools/ios/test-framework-web.cjs')],cwd=F,capture_output=True,text=True,timeout=900)
        webResult=SAFE/'framework-web/resultats.json'
        report['frameworkWebChecksPassed']=web.returncode==0 and webResult.is_file() and json.loads(webResult.read_text()).get('allExecutedChecksPassed') is True
        if not report['frameworkWebChecksPassed']:raise RuntimeError('Candidate browser regression failed; inspect protected framework-web report')

    publicOnboarding=None
    if focus=='onboarding_request':
        import urllib.request
        email=os.environ.get('FP_IOS_ONBOARDING_EMAIL','')
        if not re.fullmatch(r'[a-zA-Z0-9._-]+\+fpios-[a-z0-9-]+@gmail\.com',email):
            raise RuntimeError('Controlled public-registration test address missing; no registration sent')
        request=urllib.request.Request('https://merchant.futurpayment.com/api-merchant-portal/api/merchant-onboarding/public/v1/acquirers',headers={'Accept':'application/json','User-Agent':'FuturPayment-Recipe/1.0 (authorized demo health check)'})
        with urllib.request.urlopen(request,timeout=30) as response:
            if response.status!=200:raise RuntimeError('Public acquirer catalogue unavailable')
            catalogue=json.loads(response.read())
        acquirer=next((a for a in catalogue if a.get('code')=='DAMANCASH'),None)
        if not acquirer or not isinstance(acquirer.get('name'),str) or not acquirer['name'].strip():raise RuntimeError('Required public acquirer not confirmed')
        publicOnboarding={'email':email,'acquirerName':acquirer['name']}
        report['publicRegistrationIsNotActivation']=True
    for target in targets:
        if kycContext:target.setdefault('EnvironmentVariables',{}).update(FP_IOS_KYC_FIXTURE=json.dumps(kycContext))
        if frameworkContext:target.setdefault('EnvironmentVariables',{}).update(FP_IOS_KYC_FIXTURE=json.dumps(frameworkContext))
        if draftContext:target.setdefault('EnvironmentVariables',{}).update(FP_IOS_DRAFT_FIXTURE=json.dumps(draftContext['fixture']))
        if publicOnboarding:target.setdefault('EnvironmentVariables',{}).update(FP_IOS_ONBOARDING_EMAIL=publicOnboarding['email'],FP_IOS_ONBOARDING_ACQUIRER_NAME=publicOnboarding['acquirerName'])
        if uploadFixture:target.setdefault('EnvironmentVariables',{}).update(FP_IOS_UPLOAD_REFERENCE=uploadFixture['fixture']['reference'],FP_IOS_UPLOAD_SOURCE=uploadSource)
        if supportFixture:
            target.setdefault('EnvironmentVariables',{}).update(
                FP_IOS_REPLY_REFERENCE=supportFixture['fixtures']['reply']['reference'],
                FP_IOS_CONFIRM_REFERENCE=supportFixture['fixtures']['confirm']['reference'],
                FP_IOS_REOPEN_REFERENCE=supportFixture['fixtures']['reopen']['reference'],
                FP_IOS_PUBLIC_MESSAGE=supportFixture['publicMarker'],
                FP_IOS_INTERNAL_MESSAGE=supportFixture['internalMarker'],
                FP_IOS_REPLY_MESSAGE=supportFixture['nativeReply'])
        target.setdefault('EnvironmentVariables',{}).update(FP_IOS_AUTHENTICATED_TESTS='true' if authenticated else 'false',FP_IOS_TEST_USER=os.environ.get('FP_IOS_TEST_USER','bim.amina'),FP_IOS_TEST_PASSWORD=password)
    with tempfile.NamedTemporaryFile(prefix='fp-ui-',suffix='.xctestrun',dir=OUT/'DerivedData/Build/Products',delete=False) as file:
        private=Path(file.name);file.write(plistlib.dumps(data))
    private.chmod(0o600)
    bundle=OUT/'native-results.xcresult'
    try:
        report['nativeUIExecutionStarted']=True
        selector='MerchantUITests/MerchantUITests/'
        base=['xcodebuild','test-without-building','-xctestrun',str(private),'-destination',destination,'-parallel-testing-enabled','NO']
        exclusions=['-skip-testing:'+selector+name for name in ['test08FreshInstallationRequiresLogin','test11PersonalDossierReadOnly','test12CommercialOnboardingReadOnly','test13PublicPersonalRegistrationRequest','test14PersonalLegalDossierNativeSave','test14bSavedDossierKeyboardLayout','test15PersonalKycDocumentsNativeUpload']]
        if not authenticated:exclusions+=['-skip-testing:'+selector+'test10SeedSessionForReinstall']
        if focus=='session_files_reinstall':
            exclusions=['-only-testing:'+selector+name for name in ['test04MerchantSessionRestoreAndLogout','test05MerchantIncidentsEntry','test09CancelNativeAttachmentCopy','test10SeedSessionForReinstall']]
        if focus=='authenticated':
            exclusions=['-only-testing:'+selector+name for name in ['test04MerchantSessionRestoreAndLogout','test05MerchantIncidentsEntry','test06NativeIncidentDraftCreation','test07NativeSupportReplyAndResolution','test09CancelNativeAttachmentCopy','test10SeedSessionForReinstall']]
        if focus=='framework_validation':
            exclusions=['-only-testing:'+selector+name for name in ['test04MerchantSessionRestoreAndLogout','test05MerchantIncidentsEntry','test14bSavedDossierKeyboardLayout']]
            report['reinstallationRequested']=False
        if focus=='connection':
            exclusions=['-only-testing:'+selector+name for name in ['test04MerchantSessionRestoreAndLogout','test05MerchantIncidentsEntry']]
        if focus=='incident_completion':
            exclusions=['-only-testing:'+selector+name for name in ['test06bNativeIncidentSubmission','test09bSaveNativeAttachmentCopy','test10SeedSessionForReinstall']]
        if focus=='isolation':
            exclusions=['-only-testing:'+selector+name for name in ['test09cNativeMerchantIsolation','test10SeedSessionForReinstall']]
        if focus=='upload':
            exclusions=['-only-testing:'+selector+name for name in ((['test09bSaveNativeAttachmentCopy'] if uploadSource=='files' else [])+['test09cNativeMerchantIsolation','test09dNativeImageAttachmentUpload','test10SeedSessionForReinstall'])]
        if focus=='onboarding_documents':
            exclusions=['-only-testing:'+selector+name for name in ['test09bSaveNativeAttachmentCopy','test14bSavedDossierKeyboardLayout','test15PersonalKycDocumentsNativeUpload']]
            report['nativeKycUploadOnly']=True
            report['reinstallationRequested']=False
        if focus=='onboarding_draft':
            exclusions=['-only-testing:'+selector+'test14PersonalLegalDossierNativeSave']
            report['nativeDossierSaveOnly']=True
            report['reinstallationRequested']=False
        if focus=='onboarding_request':
            exclusions=['-only-testing:'+selector+'test13PublicPersonalRegistrationRequest']
            report['publicRegistrationCountertestOnly']=True
            report['reinstallationRequested']=False
        if focus=='onboarding_readonly':
            exclusions=['-only-testing:'+selector+name for name in ['test11PersonalDossierReadOnly','test12CommercialOnboardingReadOnly']]
            report['onboardingReadOnlyCountertest']=True
            report['reinstallationRequested']=False
        if focus=='attachment':
            exclusions=['-only-testing:'+selector+name for name in ((['test09bSaveNativeAttachmentCopy'] if uploadSource=='files' else [])+['test09dNativeImageAttachmentUpload'])]
            report['attachmentCountertestOnly']=True
            report['reinstallationRequested']=False
        with raw.open('a') as log:
            test=subprocess.run(base+exclusions+['-resultBundlePath',str(bundle)],cwd=F,stdout=log,stderr=subprocess.STDOUT,timeout=2100 if focus=='full' else 1800 if focus in ('onboarding_draft','onboarding_documents','framework_validation') else 720)
        summary=json.loads(run(['xcrun','xcresulttool','get','test-results','summary','--path',str(bundle)]))
        passed=int(summary.get('passedTests',0));failed=int(summary.get('failedTests',0));skipped=int(summary.get('skippedTests',0))
        if draftContext:
            reconciliation=draftModule.verify(draftContext,password,SAFE/'native-dossier-reconciliation.json')
            report['nativeDossierServerReconciliationPassed']=reconciliation['allExecutedChecksPassed']
            report['nativeDossierServerAssertionCount']=len(reconciliation['checks'])
        if focus=='framework_validation':
            token=frameworkModule.authenticated(fixture,password)
            after=frameworkModule.request('/api-merchant-portal/api/merchant-onboarding/v2/dossiers/'+fixture['id'],token);token=''
            report['frameworkSavedDossierVersionPreserved']=after['version']==frameworkVersionBefore
        if kycContext:
            reconciliation=kycModule.verify(kycContext,password,SAFE/'native-kyc-reconciliation.json')
            report['nativeKycServerReconciliationPassed']=reconciliation['allExecutedChecksPassed']
            report['nativeKycServerAssertionCount']=len(reconciliation['checks'])
        if supportFixture:
            reconciliation=module.verify(supportFixture,password,SAFE/'native-support-reconciliation.json')
            report['supportServerReconciliationPassed']=reconciliation['allExecutedChecksPassed']
            report['supportServerReconciliationAssertions']=len(reconciliation['checks'])
        if uploadFixture:
            uploadVerification=uploadModule.verify(uploadFixture,password,SAFE/'native-upload-reconciliation.json')
            report['nativeUploadServerReconciliationPassed']=uploadVerification['allExecutedChecksPassed']
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
        report.update(passed=passed,failed=failed,skipped=skipped,allExecutedPassed=test.returncode==0 and failed==0 and (passed==expectedCases if authenticated else passed>=3) and report.get('nativeExportTemporaryCleanupPassed',True) and report.get('supportServerReconciliationPassed',True),authenticatedCasesPassed=authenticated and test.returncode==0 and passed==expectedCases and skipped==0 and (focus in ('connection','attachment','onboarding_readonly','onboarding_request','onboarding_draft','onboarding_documents','framework_validation') or (report.get('reinstallationPassed',False) and report.get('nativeExportTemporaryCleanupPassed',False))) and report.get('supportServerReconciliationPassed',True))
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
        report['nativeKeyboardLayoutPassed']='FP_NATIVE_LAYOUT focusStable=true horizontalClipping=false valuePreserved=true businessMutation=false' in journal
        report['nativeKycUploadMarkers']=len(re.findall(r'FP_NATIVE_KYC_UPLOADED index=\d+ pending=true',journal))
        report['nativeKycUploadCompleted']=bool(re.search(r'FP_NATIVE_KYC completed=true uploads=\d+ authenticityClaimed=false',journal))
        report['nativeLegalDossierSaveObserved']='FP_NATIVE_ONBOARDING legalDossierSaved=true dedicatedAccount=true explicitSaveCount=1' in journal
        report['nativeDossierFieldConfirmations']=len(re.findall(r'FP_NATIVE_DRAFT_FIELD index=\d+ confirmed=true',journal))
        report['nativePublicRegistrationAcknowledged']='FP_NATIVE_ONBOARDING publicRegistrationAcknowledged=true mailReceiptNotInferred=true' in journal
        report['nativeExistingPersonalDossierReadOnlyObserved']='FP_NATIVE_ONBOARDING existingPersonalDossierRead=true approved=true businessMutation=false' in journal
        report['nativeCommercialOnboardingReadOnlyObserved']='FP_NATIVE_ONBOARDING commercialEntryRead=true commercialListLoaded=true businessMutation=false' in journal
        report['nativeImageUploadObserved']='FP_NATIVE_UPLOAD pickerSelected=true serverAcknowledged=true' in journal
        report['nativeMerchantSwitchIsolationObserved']='FP_NATIVE_ISOLATION switched=true ownRead=true foreignReferenceAbsent=true' in journal
        report['nativeSubmittedSubjects']=sorted(set(re.findall(r'FP_NATIVE_SUBMISSION subject=(iOS depot [a-f0-9]{8})',journal)))
        report['nativeDraftSubjects']=sorted(set(re.findall(r'FP_NATIVE_DRAFT subject=(iOS natif [a-f0-9]{8})',journal)))
        report['loginInputLengthMatches']=[value=='true' for value in re.findall(r'FP_LOGIN_INPUT lengthMatches=(true|false)',journal)]
        report['loginFailureUiCategories']=[int(value) for value in re.findall(r'FP_LOGIN_UI_FAILURE status=(\d+)',journal)]
        report['initialLoginSeconds']=[int(value) for value in re.findall(r'FP_INITIAL_LOGIN seconds=(\d+)',journal)]
        report['reinstallationMarkers']={'sessionSeeded':bool(re.search(r'FP_REINSTALL_SEED restored=true',journal)),'freshLoginRequired':bool(re.search(r'FP_REINSTALL_RESULT loginRequired=true',journal))}
        report['nativeExportCancellationObserved']=bool(re.search(r'FP_NATIVE_EXPORT cancelled=true',journal))
        report['nativePickerBackNavigations']=[int(value) for value in re.findall(r'FP_PICKER_BACK count=(\d+)',journal)]
        report['nativeSupportUiMarkers']={name:bool(re.search('FP_NATIVE_SUPPORT '+name+'=true',journal)) for name in ['reply','confirm','reopen']}
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
    try:
        diagnostic=subprocess.run(['xcrun','simctl','spawn',chosen['udid'],'log','show','--last','30m','--style','compact','--predicate','eventMessage BEGINSWITH "FP_AUTH_DIAGNOSTIC"'],capture_output=True,text=True,timeout=45)
        if diagnostic.returncode==0:
            report['nativeAuthDiagnostic']=[{'phase':phase,'status':int(status),'destination':destination} for phase,status,destination in re.findall(r'FP_AUTH_DIAGNOSTIC phase=(NETWORK|HTTP|SESSION|CLIENT) status=(-?\d+) destination=(HTTPS|BUNDLED|OTHER|NONE)',diagnostic.stdout)]
        network=subprocess.run(['xcrun','simctl','spawn',chosen['udid'],'log','show','--last','30m','--style','compact','--predicate','process == "App" AND subsystem == "com.apple.CFNetwork" AND messageType == error'],capture_output=True,text=True,timeout=45)
        if network.returncode==0:
            codes=re.findall(r'(?:NSURLErrorDomain Code=|completed with error \[)(-?\d+)',network.stdout)
            report['nativeNetworkErrorCodesOnly']=sorted(set(int(code) for code in codes))
            report['nativeNetworkLogRawExported']=False
        tls=subprocess.run(['xcrun','simctl','spawn',chosen['udid'],'log','show','--last','30m','--style','compact','--predicate','process == "App" AND messageType == error AND (eventMessage CONTAINS[c] "SSL" OR eventMessage CONTAINS[c] "TLS" OR eventMessage CONTAINS "CFStream")'],capture_output=True,text=True,timeout=45)
        report['nativeTLSNumericDiagnosticCollectionStatus']=tls.returncode
        if tls.returncode==0:
            details=[]
            for line in tls.stdout.splitlines():
                # Only known numeric status labels. No raw line, address, request,
                # identifier, header, credential, body or certificate is exported.
                for code in re.findall(r'(?:_kCFStreamErrorCodeKey|_kCFNetworkCFStreamSSLErrorOriginalValue|SSL [Ee]rror|OSStatus)[= :]+(-?\d+)',line):
                    details.append({'kind':'SSL_STATUS','code':int(code)})
                for code,domain,underlying in re.findall(r'error code:\s*(-?\d+)(?:\[(\d+):(-?\d+)\])?',line):
                    row={'kind':'NETWORK_STATUS','code':int(code)}
                    if domain:row.update(streamDomain=int(domain),streamCode=int(underlying))
                    details.append(row)
            report['nativeTLSNumericDetails']=[json.loads(value) for value in sorted(set(json.dumps(row,sort_keys=True) for row in details))]
            report['nativeTLSRawLogsExported']=False

    except subprocess.TimeoutExpired:
        report['nativeAuthDiagnosticCollectionTimedOut']=True
    try:
        copies=subprocess.run(['xcrun','simctl','spawn',chosen['udid'],'log','show','--last','30m','--style','compact','--predicate','eventMessage BEGINSWITH "FP_EXPORT_SAVED"'],capture_output=True,text=True,timeout=45)
        if copies.returncode==0:
            report['nativeSavedCopyChecks']=[{'externalToPrivateTemporaryStore':True,'bytes':int(size),'sha256':digest} for size,digest in re.findall(r'FP_EXPORT_SAVED verified=true external=true bytes=(\d+) sha256=([a-f0-9]{64})',copies.stdout)]
            report['nativeSavedCopyHashMatched']=any(row['bytes']==1000000 and row['sha256']=='a1107345e9538212373dafae0074a987f92f716656340be824e8944dcea0c9d6' for row in report['nativeSavedCopyChecks'])
    except subprocess.TimeoutExpired:report['nativeSavedCopyDiagnosticTimedOut']=True
    if authenticated and (focus in ('full','incident_completion') or (focus in ('upload','attachment') and uploadSource=='files')) and not report.get('nativeSavedCopyHashMatched',False):
        report.update(allExecutedPassed=False,authenticatedCasesPassed=False);rc=1
    if authenticated and focus in ('full','isolation','upload') and not report.get('nativeMerchantSwitchIsolationObserved',False):
        report.update(allExecutedPassed=False,authenticatedCasesPassed=False);rc=1
    if authenticated and focus in ('full','upload','attachment') and not (report.get('nativeImageUploadObserved',False) and report.get('nativeUploadServerReconciliationPassed',False)):
        report.update(allExecutedPassed=False,authenticatedCasesPassed=False);rc=1
    if focus=='onboarding_request' and not report.get('nativePublicRegistrationAcknowledged'):
        report.update(allExecutedPassed=False,authenticatedCasesPassed=False);rc=1
    if focus=='onboarding_readonly' and not (report.get('nativeExistingPersonalDossierReadOnlyObserved') and report.get('nativeCommercialOnboardingReadOnlyObserved')):
        report.update(allExecutedPassed=False,authenticatedCasesPassed=False);rc=1
    if focus=='onboarding_draft' and not (report.get('nativeLegalDossierSaveObserved') and report.get('nativeDossierServerReconciliationPassed') and report.get('nativeDossierFieldConfirmations')==report.get('nativeDossierExpectedFields')):
        report.update(allExecutedPassed=False,authenticatedCasesPassed=False);rc=1
    if focus=='onboarding_documents' and not (report.get('nativeKeyboardLayoutPassed') and report.get('nativeKycUploadCompleted') and report.get('nativeKycServerReconciliationPassed') and report.get('nativeKycUploadMarkers')==report.get('nativeKycExpectedUploads') and report.get('nativeSavedCopyHashMatched')):
        report.update(allExecutedPassed=False,authenticatedCasesPassed=False);rc=1
    if focus=='framework_validation' and not (report.get('frameworkWebChecksPassed') and report.get('nativeKeyboardLayoutPassed') and report.get('frameworkSavedDossierVersionPreserved')):
        report.update(allExecutedPassed=False,authenticatedCasesPassed=False);rc=1
    (SAFE/'resultats-ui.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report));raise SystemExit(rc)
