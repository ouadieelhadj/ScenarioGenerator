"""Read a previously received synthetic native file; never create/upload/submit."""
from pathlib import Path
import datetime,hashlib,importlib.util,json,os,re,uuid
spec=importlib.util.spec_from_file_location('upload_source',Path(__file__).with_name('prepare-native-upload-fixture.py'))
source=importlib.util.module_from_spec(spec);spec.loader.exec_module(source)
EXPECTED='a1107345e9538212373dafae0074a987f92f716656340be824e8944dcea0c9d6'
def snapshot(identifier,password):
    token=source.request('/api-merchant-portal/auth/login',body={'login':'bim.amina','password':password})['token']
    try:
        path=source.BASE+'/'+identifier;ticket=source.request(path,token);files=source.request(path+'/attachments',token)
        assert ticket['id']==identifier and ticket['channel']=='IOS' and ticket['author']=='232' and ticket['status']=='DRAFT'
        assert re.fullmatch('iOS upload [a-f0-9]{8}',ticket['subject'])
        assert len(files)==1 and files[0]['scanState']=='CLEAN' and files[0]['contentType']=='image/png'
        content=source.request(path+'/attachments/'+files[0]['id'],token,binary=True)
        assert len(content)==1000000 and hashlib.sha256(content).hexdigest()==EXPECTED
        return {'id':identifier,'reference':ticket['reference'],'version':ticket['version'],'attachmentId':files[0]['id'],'scanState':'CLEAN','bytes':len(content),'sha256':EXPECTED}
    finally:token=''
def prepare(output,password):
    identifier=os.environ.get('FP_IOS_UPLOAD_REUSE_ID','');assert str(uuid.UUID(identifier))==identifier
    before=snapshot(identifier,password)
    Path(output).write_text(json.dumps({'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'readOnly':True,'noUpload':True,'before':before},indent=2)+'\n')
    return {'before':before}
def verify(context,password,output):
    after=snapshot(context['before']['id'],password)
    checks=[{'name':'Same already received file, CLEAN state, exact bytes and incident version after native read','passed':after==context['before']}]
    result={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'readOnly':True,'noUpload':True,'businessWrites':False,'checks':checks,'allExecutedChecksPassed':all(c['passed'] for c in checks),'after':after}
    Path(output).write_text(json.dumps(result,indent=2)+'\n');return result
