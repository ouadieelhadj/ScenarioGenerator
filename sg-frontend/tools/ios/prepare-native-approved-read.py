"""Read-only binding of the already approved personal case; no new dossier/save/upload."""
from pathlib import Path
import datetime,json
from importlib.util import spec_from_file_location,module_from_spec
spec=spec_from_file_location('draft_source',Path(__file__).with_name('prepare-native-onboarding-draft.py'))
source=module_from_spec(spec);spec.loader.exec_module(source)
def snapshot(fixture,password):
    token=source.authenticated(fixture,password)
    try:
        own=source.request(source.PREFIX+'/v1/dossiers/verified-mine',token)
        full=source.request(source.PREFIX+'/v2/dossiers/'+fixture['id'],token)
        docs=source.request(source.PREFIX+'/v1/dossiers/'+fixture['id']+'/documents',token)
        assert own['id']==fixture['id'] and own['reference']==fixture['reference'] and own['acquirerId']=='DAMANCASH'
        assert full['status']=='APPROVED' and full['kycStatus']=='VALIDATED' and full['legalName']==fixture['legalName']
        assert len(docs)==6 and all(d['reviewStatus']=='ACCEPTED' for d in docs)
        return {'id':fixture['id'],'reference':fixture['reference'],'version':full['version'],'status':full['status'],'kycStatus':full['kycStatus'],'documents':sorted([{'id':d['id'],'type':d['type'],'version':d['version'],'sha256':d['sha256'],'reviewStatus':d['reviewStatus']} for d in docs],key=lambda d:d['id'])}
    finally:token=''
def prepare(output,password):
    fixture=source.load_fixture();before=snapshot(fixture,password)
    Path(output).write_text(json.dumps({'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'readOnly':True,'before':before},indent=2)+'\n')
    return {'fixture':{'login':fixture['login'],'reference':fixture['reference']},'binding':fixture,'before':before}
def verify(context,password,output):
    after=snapshot(context['binding'],password)
    checks=[{'name':'Same approved case and six accepted documents retained after native read','passed':after==context['before']}]
    report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'readOnly':True,'businessWrites':False,'checks':checks,'allExecutedChecksPassed':all(c['passed'] for c in checks),'after':after}
    Path(output).write_text(json.dumps(report,indent=2)+'\n');return report
