"""One unauthenticated connectivity probe, no real account/password or bearer."""
from pathlib import Path
import datetime,json,os,urllib.error,urllib.request,uuid
O=Path(os.environ['FP_IOS_OUTPUT']);O.mkdir(parents=True,exist_ok=True)
url='https://merchant.futurpayment.com/api-merchant-portal/auth/login'
body=json.dumps({'login':'__fp_ios_connectivity_'+uuid.uuid4().hex,'password':'synthetic-non-account-value'}).encode()
req=urllib.request.Request(url,data=body,headers={'Content-Type':'application/json','Accept':'application/json','User-Agent':'FuturPayment-iPhone-Connectivity-Check/1'})
try:
    with urllib.request.urlopen(req,timeout=25) as response:status=response.status;headers=dict(response.headers)
except urllib.error.HTTPError as error:status=error.code;headers=dict(error.headers)
except Exception as error:
    report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'tlsAndNetworkConfirmed':False,'failureType':type(error).__name__,'realAccountUsed':False,'secretsUsed':False}
else:pass
if 'status' in locals():
    h={key.lower():value for key,value in headers.items()}
    report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'tlsAndNetworkConfirmed':True,'http':status,'contentType':h.get('content-type'),'edgeChallenge':h.get('cf-mitigated'),'expectedAnonymousRefusal':status==401,'realAccountUsed':False,'secretsUsed':False,'doesNotProveNativeURLSessionResult':True}
(O/'connectivite-api.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
