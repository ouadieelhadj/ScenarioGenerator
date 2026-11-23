"""Read-only binding before/after native dossier save. No API business write or fabricated identity."""
from pathlib import Path
import datetime,json,os,re,urllib.request,urllib.error,uuid
ORIGIN='https://merchant.futurpayment.com';PREFIX='/api-merchant-portal/api/merchant-onboarding'
def request(path,token=None,body=None):
 headers={'Accept':'application/json','User-Agent':'FuturPayment-Recipe/1.0 (authorized demo health check)'}
 if token:headers['Authorization']='Bearer '+token
 data=None if body is None else json.dumps(body).encode()
 if data is not None:headers['Content-Type']='application/json'
 with urllib.request.urlopen(urllib.request.Request(ORIGIN+path,data=data,headers=headers),timeout=30) as response:return json.load(response)
def authenticated(fixture,password):
 return request('/api-merchant-portal/auth/login',body={'login':fixture['login'],'password':password})['token']
def load_fixture():
 d=json.loads(os.environ.get('FP_IOS_DRAFT_FIXTURE','{}'))
 assert re.fullmatch(r'fp-u\d{6,}',d['login']) and re.fullmatch(r'ONB-[A-F0-9]{8}',d['reference'])
 uuid.UUID(d['id']);assert d['expectedVersion']==0 and d['legalName'].startswith('RECETTE IPHONE')
 assert re.fullmatch(r'[a-zA-Z0-9._-]+\+fpios-[a-z0-9-]+@gmail\.com',d['email'])
 assert len(d['companyFields'])>=25 and len(d['outletFields'])>=15
 assert all(set(row)<=set(['label','value','index','prefix']) and isinstance(row['label'],str) and isinstance(row['value'],str) for row in d['companyFields']+d['outletFields'])
 return d
def prepare(output,password):
 d=load_fixture();token=authenticated(d,password)
 try:
  own=request(PREFIX+'/v1/dossiers/verified-mine',token)
  assert own['id']==d['id'] and own['reference']==d['reference'] and own['status']=='DRAFT' and own['acquirerId']=='DAMANCASH'
  full=request(PREFIX+'/v2/dossiers/'+d['id'],token)
  assert full['version']==d['expectedVersion'] and not full['outlets'] and not full['legalName'],'Dossier changed: inspect instead of resaving'
  workspace=request(PREFIX+'/v1/dossiers/'+d['id']+'/commercial/workspace',token)
  assert 'EDIT' in workspace['actions'] and 'SUBMIT' in workspace['actions'],'Use existing supported rights, never widen them for the test'
  rules=request(PREFIX+'/v2/dossiers/'+d['id']+'/morocco-rules',token)
  checklist=request(PREFIX+'/v2/dossiers/'+d['id']+'/kyc-requirements?merchantType=PM&country=MA',token)
  report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'id':d['id'],'reference':d['reference'],'account':d['login'],'readOnlyPreflight':True,'ownDraftBound':True,'expectedVersion':0,'emptyOutletsConfirmed':True,'editAndSubmitAuthorized':True,'moroccoRules':rules,'requiredDocumentTypes':[r['type'] for r in checklist['documents']],'businessWritesByHelper':False}
  Path(output).write_text(json.dumps(report,indent=2)+'\n');return {'fixture':d,'preflight':report}
 finally:token=''
def verify(context,password,output):
 d=context['fixture'];token=authenticated(d,password);checks=[]
 def check(name,value):checks.append({'name':name,'passed':bool(value)})
 try:
  own=request(PREFIX+'/v1/dossiers/verified-mine',token);full=request(PREFIX+'/v2/dossiers/'+d['id'],token)
  check('Own dedicated dossier retained',own['id']==d['id'] and own['reference']==d['reference'])
  check('DAMANCASH and DRAFT retained',own['acquirerId']=='DAMANCASH' and own['status']=='DRAFT')
  check('Exactly one native dossier save',full['version']==1)
  check('Legal identity saved',full['merchantType']=='PM' and full['legalName']==d['legalName'])
  check('Explicit destination currency and channel saved',full['provisioningDestination']=='FUTURPAYMENT' and full['settlementCurrency']=='504' and full['acceptanceChannel']=='TPE')
  check('One principal outlet saved',len(full['outlets'])==1 and full['outlets'][0]['principal'])
  if full['outlets']:
   outlet=full['outlets'][0];check('Dedicated outlet saved',outlet['code']==d['outletCode'] and outlet['name']=='PDV FICTIF IPHONE')
   check('Outlet responsible identity retained',outlet['responsible']['idNumber']=='TESTIOSRESP10' and outlet['responsible']['nationality']=='MA')
  check('Moroccan administrative address retained',full['headquartersAddress']['province']=='Casablanca' and full['headquartersAddress']['commune']=='Maarif')
  check('Contact and fiscal coordinates retained',full['primaryEmail']==d['email'] and full['ice']=='002026112300010')
  company={'Raison sociale':'legalName','Nom commercial':'tradingName','Numéro du registre de commerce':'registrationNumber','Tribunal du registre de commerce':'registrationCourt','Taxe professionnelle':'professionalTaxNumber','Identifiant fiscal':'taxIdentifier','ICE':'ice','Forme juridique':'legalForm','Activite':'businessActivity','Telephone principal':'primaryPhone','E-mail principal':'primaryEmail','MCC':'mcc','Devise de règlement':'settlementCurrency','RIB':'rib','Adresse':'headquartersAddress.line1','Quartier':'headquartersAddress.district','Province / Préfecture':'headquartersAddress.province','Commune':'headquartersAddress.commune','Code postal':'headquartersAddress.postalCode','Ville':'headquartersAddress.city','Région de l\'adresse':'headquartersAddress.region','Pays ISO-2':'headquartersAddress.country','Prenom':'representative.firstName','Nom':'representative.lastName','Telephone':'representative.phone','E-mail':'representative.email','Type d\'identite':'representative.idType','Numero d\'identite':'representative.idNumber','Pays de residence':'representative.residenceCountry','Nationalite':'representative.nationality'}
  outletPaths={'Code':'code','Nom':'name','Adresse':'address.line1','Quartier':'address.district','Province / Préfecture':'address.province','Commune':'address.commune','Code postal':'address.postalCode','Ville':'address.city','Région de l\'adresse':'address.region','Pays':'address.country','Telephone contact':'contactPhone','E-mail contact':'contactEmail','Responsable - prenom':'responsible.firstName','Responsable - nom':'responsible.lastName','Responsable - telephone':'responsible.phone','Responsable - e-mail':'responsible.email','Responsable - type d\'identité':'responsible.idType','Responsable - numéro d\'identité':'responsible.idNumber','Responsable - pays de résidence':'responsible.residenceCountry','Responsable - nationalité':'responsible.nationality'}
  def read(value,path):
   for part in path.split('.'):value=value.get(part) if isinstance(value,dict) else None
   return value
  for group,rows,paths,value in [('Company',d['companyFields'],company,full),('Outlet',d['outletFields'],outletPaths,full['outlets'][0] if full['outlets'] else {})]:
   for row in rows:
    path=paths[row['label']];expected=row['value']
    if path.endswith(('Phone','.phone')) and expected.startswith('06'):expected='+212'+expected[1:]
    check(group+' field retained: '+row['label'],read(value,path)==expected)
  report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'readOnlyReconciliation':True,'id':d['id'],'reference':d['reference'],'version':full['version'],'checks':checks,'allExecutedChecksPassed':all(c['passed'] for c in checks),'businessWritesByHelper':False,'nativeUiPassNotInferred':True}
  Path(output).write_text(json.dumps(report,indent=2)+'\n');return report
 finally:token=''
