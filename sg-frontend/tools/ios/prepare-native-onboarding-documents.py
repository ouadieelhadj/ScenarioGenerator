"""Read-only KYC preflight/reconciliation. Only the native UI saves/uploads files."""
from pathlib import Path
import datetime, hashlib, importlib.util, json, urllib.request

PREFIX='/api-merchant-portal/api/merchant-onboarding'
SYNTHETIC_HASH='a1107345e9538212373dafae0074a987f92f716656340be824e8944dcea0c9d6'
SOURCE_INCIDENT='90b54e47-6fec-4f03-b47d-69cd0ceb1aed'
SOURCE_ATTACHMENT='d41ce29b-7cfd-439f-bf38-77032b23d605'

def api():
    spec=importlib.util.spec_from_file_location('native_draft',Path(__file__).with_name('prepare-native-onboarding-draft.py'))
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module

def prepare(output,password):
    m=api();fixture=m.load_fixture();token=m.authenticated(fixture,password)
    try:
        own=m.request(PREFIX+'/v1/dossiers/verified-mine',token)
        full=m.request(PREFIX+'/v2/dossiers/'+fixture['id'],token)
        assert own['id']==fixture['id'] and full['reference']==fixture['reference'] and own['acquirerId']=='DAMANCASH'
        assert full['version']==1 and full['status']=='DRAFT' and full['merchantType']=='PM' and full['legalName']==fixture['legalName']
        workspace=m.request(PREFIX+'/v1/dossiers/'+fixture['id']+'/commercial/workspace',token)
        assert 'EDIT' in workspace['actions']
        documents=m.request(PREFIX+'/v1/dossiers/'+fixture['id']+'/documents',token)
        assert documents==[], 'Existing uploads must be reconciled before a distinct recovery, never duplicated'
        checklist=m.request(PREFIX+'/v2/dossiers/'+fixture['id']+'/kyc-requirements',token)
        assert not checklist['selectionRequired'] and checklist['merchantType']=='PM'
        required=checklist['documents'];assert 1<=len(required)<=8
        assert len({d['type'] for d in required})==len(required)
        # test09b must save the existing synthetic image through the real Files UI.
        # Do not expose private Documents, change File Sharing or seed a hidden provider.
        other=m.request('/api-merchant-portal/auth/login',body={'login':'bim.amina','password':password})['token']
        try:
            rows=m.request(PREFIX+'/v1/acquirers/DAMANCASH/incidents/'+SOURCE_INCIDENT+'/attachments',other)
            source=next(r for r in rows if r['id']==SOURCE_ATTACHMENT)
            # Incident.Attachment exposes id/contentType/scanState/createdAt, not size.
            # Exact size and hash are checked on the authorized download below.
            assert source['scanState']=='CLEAN' and source['contentType']=='image/png'
            req=urllib.request.Request(m.ORIGIN+PREFIX+'/v1/acquirers/DAMANCASH/incidents/'+SOURCE_INCIDENT+'/attachments/'+SOURCE_ATTACHMENT,headers={'Authorization':'Bearer '+other,'Accept':'image/png','User-Agent':'FuturPayment-Recipe/1.0 (authorized demo health check)'})
            with urllib.request.urlopen(req,timeout=30) as response:content=response.read(1000001)
            assert len(content)==1000000 and hashlib.sha256(content).hexdigest()==SYNTHETIC_HASH
        finally:other=''
        expected=[{'index':n,'type':r['type'],'label':r['label'],'contentType':'image/png','bytes':1000000,'sha256':SYNTHETIC_HASH} for n,r in enumerate(required,1)]
        context={'id':fixture['id'],'reference':fixture['reference'],'login':fixture['login'],'versionBefore':1,'documents':expected}
        report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'reference':fixture['reference'],'account':fixture['login'],
                'readOnlyApiPreflight':True,'versionBefore':1,'status':'DRAFT','documentsBefore':0,'requiredDocumentTypes':[r['type'] for r in required],
                'nativeFilesSaveRequiredBeforeSelection':True,'syntheticImageReusedForRequiredTypes':True,'documentsUploadedByHelper':False,
                'businessWritesByHelper':False,'fileSharingOrSecurityChanged':False,'expectedFiles':expected}
        Path(output).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');return context
    finally:token=''

def verify(context,password,output):
    m=api();fixture=m.load_fixture();token=m.authenticated(fixture,password);checks=[]
    def check(name,value):checks.append({'name':name,'passed':bool(value)})
    try:
        full=m.request(PREFIX+'/v2/dossiers/'+context['id'],token)
        own=m.request(PREFIX+'/v1/dossiers/verified-mine',token)
        check('Same personal dossier retained',own['id']==context['id'] and full['reference']==context['reference'])
        check('Draft remains pending human review',full['status']=='DRAFT' and own['kycStatus']!='VALIDATED')
        # addDocument() notes each change and flushes @Version once per upload.
        check('One document change per upload',full['version']==context['versionBefore']+len(context['documents']))
        check('Legal profile unchanged by uploads',full['legalName']==fixture['legalName'] and len(full['outlets'])==1 and full['outlets'][0]['code']==fixture['outletCode'])
        rows=m.request(PREFIX+'/v1/dossiers/'+context['id']+'/documents',token)
        check('Exactly one upload for each required type',len(rows)==len(context['documents']) and len({r['type'] for r in rows})==len(rows))
        for expected in context['documents']:
            actual=[r for r in rows if r['type']==expected['type']]
            check(expected['type']+' present exactly once',len(actual)==1)
            if len(actual)!=1:continue
            row=actual[0]
            check(expected['type']+' exact synthetic image',row['caseId']==context['id'] and row['contentType']=='image/png' and row['contentLength']==expected['bytes'] and row['sha256']==expected['sha256'])
            check(expected['type']+' pending human review',row['reviewStatus']=='PENDING' and row.get('reviewedBy') is None)
        report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'readOnlyReconciliation':True,'reference':context['reference'],'version':full['version'],
                'documents':[{k:r.get(k) for k in ['id','type','version','contentType','contentLength','sha256','reviewStatus','uploadedBy']} for r in rows],
                'checks':checks,'allExecutedChecksPassed':all(c['passed'] for c in checks),'businessWritesByHelper':False,'nativeUiSuccessNotInferred':True,
                'syntheticImageReusedForRequiredTypes':True,'documentAuthenticityTested':False}
        Path(output).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');return report
    finally:token=''


if __name__=='__main__':
    import os,sys,urllib.error,urllib.parse
    assert sys.argv[1:]==['--check-only']
    root=Path(os.environ['FP_IOS_OUTPUT'])/'preuves-publiables';root.mkdir(parents=True,exist_ok=True)
    try:
        context=prepare(root/'native-kyc-preflight.json',os.environ['FP_IOS_TEST_PASSWORD'])
        result={'readOnlyPreflightPassed':True,'nativeUIExecutionStarted':False,'businessWrites':False,'requiredDocuments':len(context['documents'])}
    except urllib.error.HTTPError as error:
        raw=error.read(2048)
        result={'readOnlyPreflightPassed':False,'nativeUIExecutionStarted':False,'businessWrites':False,'http':error.code,
                'path':urllib.parse.urlsplit(error.url).path,'responseContentType':error.headers.get('Content-Type'),
                'responseServer':error.headers.get('Server'),'cfMitigated':error.headers.get('cf-mitigated'),
                'responseBodySha256':hashlib.sha256(raw).hexdigest(),'bodyStored':False,'cloudflareMentionInBody':b'cloudflare' in raw.lower()}
    except Exception as error:
        result={'readOnlyPreflightPassed':False,'nativeUIExecutionStarted':False,'businessWrites':False,'failureType':type(error).__name__}
    (root/'native-kyc-preflight-result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result));raise SystemExit(0 if result['readOnlyPreflightPassed'] else 1)
