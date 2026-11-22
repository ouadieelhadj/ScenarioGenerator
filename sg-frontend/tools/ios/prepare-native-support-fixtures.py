#!/usr/bin/env python3
"""Bounded real-OCI fixtures for native support actions; no accounts or rights changed."""
from pathlib import Path
import datetime,json,os,urllib.request,uuid

ORIGIN='https://merchant.futurpayment.com'
BASE='/api-merchant-portal/api/merchant-onboarding/v1/acquirers/DAMANCASH/incidents'
NODE='6d1fc470-9e01-42de-849f-0842fe70b89c'

def prepare(output:Path,password:str)->dict:
    if not password:raise ValueError('Authenticated fixture secret required')
    output=Path(output)
    report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'realOci':True,'createdThroughNativeUi':False,'rolesOrPasswordsChanged':False,'fixtures':{},'ready':False}
    tokens={}
    def save():output.write_text(json.dumps(report,indent=2)+'\n')
    def call(method,path,user=None,body=None):
        headers={'Accept':'application/json'}
        if user:headers['Authorization']='Bearer '+tokens[user]
        data=None
        if body is not None:data=json.dumps(body).encode();headers['Content-Type']='application/json'
        request=urllib.request.Request(ORIGIN+path,data=data,headers=headers,method=method)
        # No retries of uncertain creates/commands. Preserve known references.
        with urllib.request.urlopen(request,timeout=35) as response:
            if response.status!=200:raise RuntimeError('Unexpected fixture HTTP status')
            return json.load(response)
    def action(ticket,name,text):
        return call('POST',BASE+'/'+ticket['id']+'/actions/'+name,'damancash.salma',{'requestId':str(uuid.uuid4()),'expectedVersion':ticket['version'],'text':text})
    try:
        for user in ['bim.amina','damancash.salma']:
            result=call('POST','/api-merchant-portal/auth/login',body={'login':user,'password':password})
            token=result.get('token')
            if not isinstance(token,str) or len(token)<20:raise RuntimeError('Fixture authentication not confirmed')
            tokens[user]=token
        contexts=call('GET',BASE+'/contexts','bim.amina')
        context=next((row['context'] for row in contexts if row.get('context',{}).get('nodeId')==NODE),None)
        if not context:raise RuntimeError('Existing BIM context unavailable')
        suffix=uuid.uuid4().hex[:8]
        for kind in ['reply','confirm','reopen']:
            subject='iOS support '+kind+' '+suffix
            ticket=call('POST',BASE+'/ios','bim.amina',{'requestId':str(uuid.uuid4()),'context':context,'category':'OTHER','subject':subject,'description':'Fixture fictive du parcours iPhone. Aucun traitement financier.','impact':'LIMITED','fields':{},'draft':False})
            if ticket.get('status')!='OPEN':raise RuntimeError('Fixture not OPEN')
            report['fixtures'][kind]={'id':ticket['id'],'reference':ticket['reference'],'subject':subject,'status':ticket['status'],'version':ticket['version']};save()
            ticket=action(ticket,'TAKE','Préparation du seul dossier fictif iPhone pour le support.')
            if kind=='reply':
                ticket=action(ticket,'INTERNAL_NOTE','FP-IOS-INTERNAL-'+suffix)
                ticket=action(ticket,'WAIT_MERCHANT','FP-IOS-PUBLIC-'+suffix)
                messages=call('GET',BASE+'/'+ticket['id']+'/messages','bim.amina')
                internal=call('GET',BASE+'/'+ticket['id']+'/messages','damancash.salma')
                if any(m.get('internal') for m in messages) or not any(m.get('internal') for m in internal):raise RuntimeError('Internal-note fixture isolation not confirmed')
            else:ticket=action(ticket,'RESOLVE','Résolution fictive soumise au commerçant pour le test iPhone.')
            expected='WAITING_MERCHANT' if kind=='reply' else 'RESOLVED'
            if ticket.get('status')!=expected:raise RuntimeError('Unexpected prepared fixture state')
            report['fixtures'][kind].update(status=ticket['status'],version=ticket['version']);save()
        report.update(ready=True,internalMarker='FP-IOS-INTERNAL-'+suffix,publicMarker='FP-IOS-PUBLIC-'+suffix,nativeReply='FP-IOS-REPLY-'+suffix)
        save();return report
    except Exception:
        report['failure']='Fixture preparation not confirmed; inspect known references without repeating uncertain writes.';save();raise
    finally:tokens.clear();password=''

def verify(report:dict,password:str,output:Path)->dict:
    checks=[]
    def record(name,value):checks.append({'name':name,'passed':bool(value)})
    def call(path,token=None,body=None):
        headers={'Accept':'application/json'}
        if token:headers['Authorization']='Bearer '+token
        data=None
        if body is not None:data=json.dumps(body).encode();headers['Content-Type']='application/json'
        request=urllib.request.Request(ORIGIN+path,data=data,headers=headers,method='POST' if body is not None else 'GET')
        with urllib.request.urlopen(request,timeout=35) as response:return json.load(response)
    result={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'readOnlyReconciliation':True,'nativeUiPassNotInferred':True,'checks':checks,'allExecutedChecksPassed':False}
    token=''
    try:
        token=call('/api-merchant-portal/auth/login',body={'login':'bim.amina','password':password})['token']
        for kind,expected,action in [('reply','IN_PROGRESS','REPLY'),('confirm','CLOSED','CONFIRM'),('reopen','IN_PROGRESS','REOPEN')]:
            fixture=report['fixtures'][kind]
            ticket=call(BASE+'/'+fixture['id'],token)
            record(kind+' exact fixture retained',ticket.get('id')==fixture['id'] and ticket.get('subject')==fixture['subject'])
            record(kind+' expected server status',ticket.get('status')==expected)
            history=call(BASE+'/'+fixture['id']+'/history',token)
            record(kind+' native author recorded',any(e.get('action')==action and e.get('actor')=='232' for e in history))
            messages=call(BASE+'/'+fixture['id']+'/messages',token)
            record(kind+' internal notes excluded',not any(m.get('internal') for m in messages))
            if kind=='reply':record('native reply text and author persisted',any(m.get('text')==report['nativeReply'] and m.get('author')=='232' for m in messages))
        result['allExecutedChecksPassed']=all(check['passed'] for check in checks)
    except Exception:result['failure']='Read-only native support reconciliation not confirmed.'
    finally:
        token='';password=''
        Path(output).write_text(json.dumps(result,indent=2)+'\n')
    return result

if __name__=='__main__':
    out=Path(os.environ['FP_IOS_OUTPUT'])/'preuves-publiables/native-support-fixtures.json'
    out.parent.mkdir(parents=True,exist_ok=True)
    result=prepare(out,os.environ.get('FP_IOS_TEST_PASSWORD',''))
    print(json.dumps({'nativeSupportFixturesReady':result['ready'],'count':len(result['fixtures']),'accountsChanged':False}))
