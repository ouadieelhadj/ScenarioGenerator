"""Build an encrypted intermediate test APK. Final signing stays on the owner PC.

No user credentials, existing private keys or account actions are used here.
The disposable CI certificate must never be published as the final test APK.
"""
from pathlib import Path
import datetime,hashlib,json,os,secrets,subprocess,tempfile,xml.etree.ElementTree as ET

F=Path(__file__).resolve().parents[2]
O=Path(os.environ['FP_IOS_OUTPUT']).resolve();safe=O/'preuves-publiables'
safe.mkdir(parents=True,exist_ok=True)
report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'platform':'ANDROID',
        'version':'1.3.11-test','versionCode':10311,'finalReleaseSigningPerformed':False,
        'disposableCiCertificate':True,'existingPrivateKeyExported':False,
        'businessRequestsSent':False,'apkPublished':False,'nativeRuntimeTested':False,'steps':[]}
def save():(safe/'android-build.json').write_text(json.dumps(report,indent=2)+'\n')
def run(name,args,cwd=F,env=None,timeout=600):
    log=safe/(name+'.log')
    with log.open('w') as output:
        p=subprocess.run(args,cwd=cwd,env=env,stdout=output,stderr=subprocess.STDOUT,timeout=timeout)
    report['steps'].append({'name':name,'exitCode':p.returncode,'passed':p.returncode==0});save()
    assert p.returncode==0,'Failed build stage: '+name
    return log

sdk=Path(os.environ['ANDROID_HOME']);java=Path(os.environ['JAVA_HOME_21_X64'])
env=os.environ.copy();env.update(JAVA_HOME=str(java),PATH=str(java/'bin')+os.pathsep+env['PATH'],
                               NG_BUILD_MAX_WORKERS='2',NODE_OPTIONS='--max-old-space-size=2048')
temp=None
try:
    assert os.name=='posix' and (sdk/'platforms/android-36/android.jar').is_file()
    buildtools=sdk/'build-tools/36.0.0';assert (buildtools/'apksigner').is_file()
    assert hashlib.sha256((F/'android/gradle/wrapper/gradle-wrapper.jar').read_bytes()).hexdigest()=='7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172'
    props=(F/'android/gradle/wrapper/gradle-wrapper.properties').read_text()
    assert 'distributionSha256Sum=ed1a8d686605fd7c23bdf62c7fc7add1c5b23b2bbc3721e661934ef4a4911d7c' in props
    report['sourceCommit']=subprocess.check_output(['git','rev-parse','HEAD'],cwd=F,text=True).strip();save()
    run('npm-install',['npm','ci','--ignore-scripts','--prefer-offline','--no-audit','--no-fund'],env=env)
    versions={name:json.loads((F/'node_modules'/name/'package.json').read_text())['version'] for name in ['@angular/core','@capacitor/core','@capacitor/android']}
    assert versions=={'@angular/core':'21.2.26','@capacitor/core':'8.5.1','@capacitor/android':'8.5.1'}
    report['compiledDependencies']=versions
    audit=run('production-dependency-audit',['npm','audit','--omit=dev','--json'],env=env)
    assert json.loads(audit.read_text())['metadata']['vulnerabilities']['total']==0
    run('mobile-web-build',['npm','run','build:merchant-mobile','--','--configuration','production','--source-map=false'],env=env)
    run('capacitor-sync',['node','node_modules/@capacitor/cli/bin/capacitor','sync','android'],env=env)
    assets=F/'android/app/src/main/assets/public';assert (assets/'index.html').is_file()
    # A fresh, disposable signing input satisfies the standard Gradle guard.
    # It is used solely for the encrypted intermediate, then destroyed.
    temp=Path(tempfile.mkdtemp(prefix='fp-ci-android-',dir=os.environ['RUNNER_TEMP']))
    os.chmod(temp,0o700);secret=secrets.token_urlsafe(40)
    password=temp/'password.secret';password.write_text(secret+'\n');os.chmod(password,0o600)
    keystore=temp/'disposable.p12'
    keyenv=env.copy();keyenv['FP_CI_DISPOSABLE_PASSWORD']=secret
    run('generate-disposable-ci-certificate',[str(java/'bin/keytool'),'-genkeypair','-alias','ci-intermediate-only',
        '-keyalg','RSA','-keysize','2048','-validity','2','-dname','CN=Disposable CI Intermediate, O=FuturPayment Test',
        '-keystore',str(keystore),'-storetype','PKCS12','-storepass:env','FP_CI_DISPOSABLE_PASSWORD',
        '-keypass:env','FP_CI_DISPOSABLE_PASSWORD','-noprompt'],env=keyenv)
    keyenv.pop('FP_CI_DISPOSABLE_PASSWORD');secret=''
    env.update(FP_ANDROID_VERSION_CODE='10311',FP_ANDROID_VERSION_NAME='1.3.11',FP_ANDROID_TEST_VARIANT='true',
               FP_ANDROID_REQUIRE_SIGNING='true',FP_ANDROID_KEYSTORE_FILE=str(keystore),
               FP_ANDROID_KEY_ALIAS='ci-intermediate-only',FP_ANDROID_STORE_PASSWORD_FILE=str(password),
               FP_ANDROID_KEY_PASSWORD_FILE=str(password))
    run('gradle-build-and-policy-tests',['bash','gradlew','--no-daemon','--max-workers=2',
        '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m',':app:testReleaseUnitTest',':app:assembleRelease'],cwd=F/'android',env=env,timeout=1200)
    original=F/'android/app/build/outputs/apk/release/app-release.apk';assert original.is_file()
    apk=safe/'merchant-1.3.11-test-INTERMEDIATE-DO-NOT-DISTRIBUTE.apk';apk.write_bytes(original.read_bytes())
    run('intermediate-signature-verification',[str(buildtools/'apksigner'),'verify','--verbose','--print-certs',str(apk)],env=env)
    badging=run('apk-metadata',[str(buildtools/'aapt'),'dump','badging',str(apk)],env=env).read_text()
    assert "name='com.moneycore.merchantportal.test'" in badging and "versionCode='10311'" in badging and "versionName='1.3.11-test'" in badging
    assert "targetSdkVersion:'36'" in badging and "sdkVersion:'24'" in badging
    suites=[ET.parse(p).getroot() for p in (F/'android/app/build/test-results/testReleaseUnitTest').glob('TEST-*.xml')]
    assert suites and all(int(s.attrib.get('failures',0))==0 and int(s.attrib.get('errors',0))==0 for s in suites)
    report['unitTests']=sum(int(s.attrib.get('tests',0)) for s in suites)
    report['apkBytes']=apk.stat().st_size;report['intermediateApkSha256']=hashlib.sha256(apk.read_bytes()).hexdigest()
    report['allExecutedChecksPassed']=True
except Exception as error:
    report.update(allExecutedChecksPassed=False,failureType=type(error).__name__)
finally:
    # Only files in the exact ephemeral directory created by this process.
    if temp is not None:
        assert temp.resolve().parent==Path(os.environ['RUNNER_TEMP']).resolve()
        for p in temp.iterdir():assert p.is_file() and not p.is_symlink();p.unlink()
        temp.rmdir()
    report['disposableKeyDeleted']=temp is not None and not temp.exists();save()
print(json.dumps({'androidBuildPassed':report.get('allExecutedChecksPassed',False),'finalReleaseSigningPerformed':False,'nativeRuntimeTested':False,'lastStep':report['steps'][-1] if report['steps'] else None}))
raise SystemExit(0 if report.get('allExecutedChecksPassed') else 1)
