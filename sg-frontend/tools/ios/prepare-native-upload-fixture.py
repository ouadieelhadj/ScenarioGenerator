#!/usr/bin/env python3
"""One synthetic attachment fixture; never upload a file through the API for the UI test."""
from pathlib import Path
import datetime,hashlib,json,os,struct,subprocess,time,urllib.request,urllib.error,uuid,zlib
ORIGIN='https://merchant.futurpayment.com'
BASE='/api-merchant-portal/api/merchant-onboarding/v1/acquirers/DAMANCASH/incidents'
NODE='6d1fc470-9e01-42de-849f-0842fe70b89c'

def synthetic_png():
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    rows=b''.join(b'\0'+bytes(v for x in range(64) for v in ((x*17+y*3)%256,(x*5+y*19)%256,(x*11+y*7)%256)) for y in range(64))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',64,64,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(rows))+chunk(b'IEND',b'')

def request(path,token=None,body=None,binary=False):
    headers={'Accept':'application/json','User-Agent':'FuturPayment-iPhone-Upload-Check/1'}
    if token:headers['Authorization']='Bearer '+token
    data=None if body is None else json.dumps(body).encode()
    if data is not None:headers['Content-Type']='application/json'
    req=urllib.request.Request(ORIGIN+path,data=data,headers=headers,method='GET' if data is None else 'POST')
    with urllib.request.urlopen(req,timeout=30) as response:
        return response.read() if binary else json.load(response)

def prepare(output:Path,password:str,udid:str,private_dir:Path):
    if not password:raise ValueError('Authenticated fixture secret required')
    output=Path(output);token=''
    report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'createdThroughNativeUi':False,'attachmentUploadedByApi':False,'accountsOrRightsChanged':False,'ready':False}
    try:
        token=request('/api-merchant-portal/auth/login',body={'login':'bim.amina','password':password})['token']
        contexts=request(BASE+'/contexts',token)
        context=next((row['context'] for row in contexts if row.get('context',{}).get('nodeId')==NODE),None)
        if not context:raise RuntimeError('BIM fixture context unavailable')
        source_mode=os.environ.get('FP_IOS_UPLOAD_SOURCE','photos')
        if source_mode not in ('photos','files'):raise ValueError('Unsupported native upload source')
        report['sourceMode']=source_mode
        policy=request(BASE+'/attachment-policy',token);png=synthetic_png()
        if source_mode=='files':
            source_path=BASE+'/90b54e47-6fec-4f03-b47d-69cd0ceb1aed/attachments'
            files=request(source_path,token)
            source_file=next((f for f in files if f.get('id')=='d41ce29b-7cfd-439f-bf38-77032b23d605'),None)
            if not source_file or source_file.get('scanState')!='CLEAN':raise RuntimeError('Existing synthetic saved-copy source unavailable')
            png=request(source_path+'/'+source_file['id'],token,binary=True)
            if len(png)!=1000000 or hashlib.sha256(png).hexdigest()!='a1107345e9538212373dafae0074a987f92f716656340be824e8944dcea0c9d6':raise RuntimeError('Synthetic saved-copy source fingerprint differs')
            report['savedCopySource']={'incidentId':'90b54e47-6fec-4f03-b47d-69cd0ceb1aed','attachmentId':source_file['id'],'createdThroughNativeSaveTest':False,'nativeSaveRequiredBeforeSelection':True}
        report['policy']=policy
        if 'image/png' not in policy.get('contentTypes',[]) or policy.get('maxFiles',0)<1 or policy.get('maxBytes',0)<len(png):raise RuntimeError('Existing attachment policy does not allow the synthetic PNG')
        reuse=os.environ.get('FP_IOS_UPLOAD_REUSE_ID','').strip()
        if reuse:
            if str(uuid.UUID(reuse))!=reuse:raise ValueError('Invalid reusable fixture identifier')
            ticket=request(BASE+'/'+reuse,token)
            if not (ticket.get('status')=='DRAFT' and ticket.get('version')==0 and ticket.get('author')=='232' and ticket.get('channel')=='IOS' and ticket.get('subject','').startswith('iOS upload ') and ticket.get('context',{}).get('nodeId')==NODE):raise RuntimeError('Reusable fixture outside the authorized synthetic scope')
            report.update(fixtureReused=True,newDraftCreated=False,creationConfirmed=True)
        else:
            payload={'requestId':str(uuid.uuid4()),'context':context,'category':'OTHER','subject':'iOS upload '+uuid.uuid4().hex[:8],'description':'Dossier fictif réservé au dépôt depuis le sélecteur natif iPhone. Aucun traitement financier.','impact':'LIMITED','fields':{},'draft':True}
            report['createRequest']={key:payload[key] for key in ['requestId','subject']}
            report['creationConfirmed']=False;output.write_text(json.dumps(report,indent=2)+'\n')
            ticket=request(BASE+'/ios',token,payload)
            report.update(creationConfirmed=True,fixtureReused=False,newDraftCreated=True)
        report['fixture']={key:ticket[key] for key in ['id','reference','subject','status','version']}
        output.write_text(json.dumps(report,indent=2)+'\n')
        if ticket.get('status')!='DRAFT' or ticket.get('channel')!='IOS':raise RuntimeError('Unexpected fixture channel or status')
        if request(BASE+'/'+ticket['id']+'/attachments',token)!=[]:raise RuntimeError('Fixture is not empty')
        private_dir.mkdir(parents=True,exist_ok=True)
        source=private_dir/'FP-IOS-SYNTHETIC-UPLOAD.png';source.write_bytes(png)
        report['expectedFile']={'bytes':len(png),'sha256':hashlib.sha256(png).hexdigest(),'contentType':'image/png','synthetic':True}
        if source_mode=='files':
            # Nothing is inserted into the simulator filesystem or uploaded by API.
            # test09b must create the user copy through the real Save UI first.
            report.update(ready=True,simulatorMediaPrepared=False,nativeFileSaveRequired=True)
            return report
        # The hosted runner is disposable; this adds only a generated test pattern.
        report['mediaPreparation']={'phase':'WARM_PHOTOS','importTimeoutSeconds':120}
        output.write_text(json.dumps(report,indent=2)+'\n')
        warmed=subprocess.run(['xcrun','simctl','launch',udid,'com.apple.mobileslideshow'],capture_output=True,timeout=45)
        report['mediaPreparation']['photosLaunchReturnCode']=warmed.returncode
        if warmed.returncode:raise RuntimeError('Simulator Photos warm-up not confirmed')
        report['mediaPreparation']['phase']='IMPORT_SYNTHETIC_IMAGE'
        output.write_text(json.dumps(report,indent=2)+'\n')
        started=time.monotonic()
        try:result=subprocess.run(['xcrun','simctl','addmedia',udid,str(source)],capture_output=True,timeout=120)
        finally:report['mediaPreparation']['elapsedImportSeconds']=round(time.monotonic()-started,2)
        report['mediaPreparation']['importReturnCode']=result.returncode
        if result.returncode:raise RuntimeError('Synthetic simulator media not imported')
        report.update(ready=True,simulatorMediaPrepared=True)
        return report
    except Exception as error:
        report.update(failure='Native upload fixture preparation not confirmed.',failureType=type(error).__name__,http=getattr(error,'code',None));raise
    finally:
        token='';password='';output.write_text(json.dumps(report,indent=2)+'\n')

def verify(fixture,password,output):
    checks=[];result={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'readOnlyReconciliation':True,'nativeUiPassNotInferred':True,'checks':checks,'allExecutedChecksPassed':False}
    token=foreign=''
    def check(name,value):checks.append({'name':name,'passed':bool(value)})
    try:
        token=request('/api-merchant-portal/auth/login',body={'login':'bim.amina','password':password})['token']
        foreign=request('/api-merchant-portal/auth/login',body={'login':'carrefour.leila','password':password})['token']
        path=BASE+'/'+fixture['fixture']['id'];ticket=request(path,token)
        check('Exact prepared fixture retained',ticket.get('subject')==fixture['fixture']['subject'] and ticket.get('id')==fixture['fixture']['id'])
        check('IOS merchant author retained',ticket.get('channel')=='IOS' and ticket.get('author')=='232' and ticket.get('status')=='DRAFT')
        files=request(path+'/attachments',token)
        check('Exactly one file uploaded through the native UI',len(files)==1)
        if len(files)==1:
            file=files[0]
            for _ in range(20):
                if file.get('scanState')!='QUARANTINED':break
                time.sleep(2);files=request(path+'/attachments',token)
                file=next(row for row in files if row['id']==file['id'])
            result['attachment']={key:file.get(key) for key in ['id','contentType','sizeBytes','scanState']}
            check('Real attachment scanner accepted the file',file.get('scanState')=='CLEAN')
            if file.get('scanState')=='CLEAN':
                content=request(path+'/attachments/'+file['id'],token,binary=True)
                digest=hashlib.sha256(content).hexdigest();result['download']={'bytes':len(content),'sha256':digest}
                check('Downloaded bytes match the seeded synthetic PNG',len(content)==fixture['expectedFile']['bytes'] and digest==fixture['expectedFile']['sha256'])
            try:request(path+'/attachments/'+file['id'],foreign,binary=True);denied=False
            except urllib.error.HTTPError as error:denied=error.code==403
            check('Foreign merchant cannot download this new attachment',denied)
        result['allExecutedChecksPassed']=bool(checks) and all(row['passed'] for row in checks)
    except Exception as error:result.update(failure='Read-only upload reconciliation not confirmed.',failureType=type(error).__name__,http=getattr(error,'code',None))
    finally:
        token=foreign='';password='';Path(output).write_text(json.dumps(result,indent=2)+'\n')
    return result
