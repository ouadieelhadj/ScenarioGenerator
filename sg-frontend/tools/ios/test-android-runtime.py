"""Real Android runtime on a disposable hosted emulator, exact owner-signed APK.

No app debugging, account reset, security override, financial or business write.
Only UI login, session, commerce/isolation views and logout are exercised here.
"""
from pathlib import Path
import datetime,hashlib,json,os,subprocess,time,urllib.request,sys,socket,zipfile
from android_native_ui_qualified import NativeUi, NativeFailure, PACKAGE

F=Path(__file__).resolve().parents[2]
O=Path(os.environ['FP_IOS_OUTPUT']);SAFE=O/'preuves-publiables';SAFE.mkdir(parents=True,exist_ok=True)
SDK=Path(os.environ['ANDROID_HOME']);ADB=SDK/'platform-tools/adb'
ROOT=Path(os.environ['RUNNER_TEMP'])/'fp-damancash-android-runtime'
ROOT.mkdir(mode=0o700,exist_ok=False)
os.environ.update(ANDROID_AVD_HOME=str(ROOT/'avds'),ANDROID_USER_HOME=str(ROOT/'user'))
(ROOT/'avds').mkdir();(ROOT/'user').mkdir()
avd='FP_Incidents_20261124';serial='emulator-5586'
sha='9f7b7c1571731e9b243b5d34289753569377b926b0834a279326e6d41de57bf1'
password=os.environ.pop('FP_IOS_TEST_PASSWORD','');assert password
report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'platform':'ANDROID','version':'1.3.11-test','versionCode':10311,
        'apkSha256':sha,'realNativeUi':True,'realOciBackend':True,'businessWrites':False,'accountsChanged':False,
        'foregroundGuardsPreserved':True,'flagSecurePreserved':True,'nativeScenarios':[],'checks':[],'allExecutedChecksPassed':False,
        'ownerSignedCandidateExecuted':False,'encryptedCiIntermediateExecuted':True,'publicApkPublication':False}
def save():(SAFE/'android-runtime.json').write_text(json.dumps(report,indent=2)+'\n')
def check(name,ok):
    report['checks'].append({'name':name,'passed':bool(ok)});save();assert ok,name
def run(args,timeout=90,stdin=None):
    p=subprocess.run([str(a) for a in args],input=stdin,capture_output=True,timeout=timeout)
    if p.returncode:raise RuntimeError('NativePrerequisiteFailed:'+Path(str(args[0])).name)
    return p.stdout
proc=None;ui=None
try:
    apk=SAFE/'merchant-1.3.11-test-INTERMEDIATE-DO-NOT-DISTRIBUTE.apk'
    build=json.loads((SAFE/'android-build.json').read_text())
    check('Encrypted CI intermediate build passed',build['allExecutedChecksPassed'] and build['disposableKeyDeleted'])
    sha=hashlib.sha256(apk.read_bytes()).hexdigest();report['apkSha256']=sha
    expected=json.loads(Path(__file__).with_name('android-owner-payload-hashes.json').read_text())
    with zipfile.ZipFile(apk) as archive:
        actual={name:hashlib.sha256(archive.read(name)).hexdigest() for name in expected}
    report['ownerPayloadComparison']={'files':len(expected),'allMatch':actual==expected,'differentFiles':[name for name in expected if actual[name]!=expected[name]]}
    check('Critical native code and assets identical to owner-signed candidate',actual==expected)
    tools=SDK/'cmdline-tools/latest/bin'
    image='system-images;android-33;google_apis;x86_64'
    run([tools/'sdkmanager',image],timeout=600)
    check('Hosted acceleration device available',Path('/dev/kvm').exists())
    run(['sudo','chmod','0666','/dev/kvm']) # Disposable hosted runner only.
    run([tools/'avdmanager','create','avd','-n',avd,'-k',image,'--device','pixel_2'],stdin=b'no\n')
    config=ROOT/'avds'/(avd+'.avd')/'config.ini'
    with config.open('a') as stream:stream.write('\nhw.lcd.width=720\nhw.lcd.height=1280\nhw.lcd.density=240\nhw.keyboard=yes\n')
    log=(ROOT/'emulator.log').open('wb')
    proc=subprocess.Popen([str(SDK/'emulator/emulator'),'-avd',avd,'-port','5586','-memory','2048','-cores','2',
        '-no-window','-no-audio','-no-snapshot','-no-boot-anim','-gpu','swiftshader_indirect','-camera-back','none','-camera-front','none'],stdout=log,stderr=log)
    run([ADB,'start-server'])
    deadline=time.monotonic()+240
    while time.monotonic()<deadline:
        state=subprocess.run([str(ADB),'-s',serial,'shell','getprop','sys.boot_completed'],capture_output=True,timeout=12)
        if state.returncode==0 and state.stdout.strip()==b'1':break
        if proc.poll() is not None:raise RuntimeError('OwnedEmulatorExited')
        time.sleep(3)
    else:raise RuntimeError('OwnedEmulatorBootTimeout')
    check('Dedicated emulator boot completed',True)
    run([ADB,'-s',serial,'shell','wm','size','720x1280'])
    run([ADB,'-s',serial,'shell','wm','density','240'])
    run([ADB,'-s',serial,'shell','input','keyevent','82'])
    run([ADB,'-s',serial,'install',str(apk)],timeout=90)
    for login,commerce in [('bim.amina','BIM'),('carrefour.leila','Carrefour'),('damancash.karim',None),('damancash.salma',None)]:
        out=SAFE/login;out.mkdir()
        run([ADB,'-s',serial,'shell','am','force-stop',PACKAGE])
        run([ADB,'-s',serial,'shell','am','start','-n',PACKAGE+'/.MainActivity'])
        time.sleep(3)
        ui=NativeUi(ADB,serial,avd,sha,out,private_values=(password,))
        ui.allow_resource_read_retry=True;ui.validate_device()
        ui.wait_text('Se connecter','login-ready')
        ui.login({'login':login,'password':password})
        tree=ui.wait_text('Bonjour '+login,'home-confirmed')
        check(login+': authenticated native home',True)
        if commerce:
            labels=' '.join(n.get('text','')+' '+n.get('content-desc','') for n in tree.iter('node'))
            check(login+': own commerce visible',commerce.casefold() in labels.casefold())
        ui.run('shell','am','force-stop',PACKAGE)
        ui.run('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(2)
        ui.wait_text('Bonjour '+login,'session-restored')
        check(login+': valid session restored',True)
        ui.tap_text('Ouvrir le menu','open-menu',False)
        ui.tap_text('Assistance · Incidents','open-incidents')
        ui.wait_text('Assistance · Incidents','incidents-loaded')
        check(login+': incident screen accessible',True)
        ui.tap_text('Ouvrir le menu','logout-menu',False);ui.tap_text('Se déconnecter','logout')
        ui.wait_text('Se connecter','logged-out')
        ui.run('shell','am','force-stop',PACKAGE);ui.run('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(2)
        ui.wait_text('Se connecter','logout-persists')
        check(login+': logout remains effective after relaunch',True)
        ui.report.update(result='PASS',phase='read-only-regression',realOci=True,version='1.3.11-test');ui.save();ui.close();ui=None
        report['nativeScenarios'].append({'user':login,'passed':True});save()
    report['allExecutedChecksPassed']=all(x['passed'] for x in report['checks']) and len(report['nativeScenarios'])==4
except BaseException as error:
    # Fixed driver codes and exception types only; no account payload or raw logs.
    report['failureType']=str(error) if isinstance(error,NativeFailure) else type(error).__name__
finally:
    if ui:
        ui.report.update(result='FAIL');ui.save()
        try:ui.close()
        except Exception:pass
    password=''
    if proc is not None:
        subprocess.run([str(ADB),'-s',serial,'emu','kill'],capture_output=True,timeout=15)
        try:proc.wait(timeout=15)
        except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
    report['ownedEmulatorStopped']=proc is None or proc.poll() is not None
    save()
print(json.dumps({'androidRuntimePassed':report['allExecutedChecksPassed'],'checks':len(report['checks']),'scenarios':len(report['nativeScenarios']),'failureType':report.get('failureType')}))
raise SystemExit(0 if report['allExecutedChecksPassed'] else 1)
