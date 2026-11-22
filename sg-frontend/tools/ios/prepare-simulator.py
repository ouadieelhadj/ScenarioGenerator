"""Boot the newest available iPhone while the independent client compilation runs."""
from pathlib import Path
import datetime,json,os,re,subprocess
O=Path(os.environ['FP_IOS_OUTPUT']);O.mkdir(parents=True,exist_ok=True)
listing=json.loads(subprocess.check_output(['xcrun','simctl','list','devices','available','--json'],text=True))
candidates=[]
for runtime,devices in listing['devices'].items():
    if '.iOS-' not in runtime:continue
    version=tuple(int(x) for x in runtime.rsplit('.iOS-',1)[1].split('-'))
    for device in devices:
        if device.get('isAvailable') and device['name'].startswith('iPhone'):candidates.append((version,device['name'],runtime,device))
if not candidates:raise SystemExit('NO_AVAILABLE_IPHONE_SIMULATOR')
_,_,runtime,chosen=max(candidates,key=lambda item:(item[0],item[1]))
subprocess.run(['defaults','write','com.apple.iphonesimulator','ConnectHardwareKeyboard','-bool','false'],check=True)
if chosen['state']!='Booted':subprocess.run(['xcrun','simctl','boot',chosen['udid']],check=True)
report={'udid':chosen['udid'],'name':chosen['name'],'runtime':runtime,'bootRequestedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'bootCompleted':chosen['state']=='Booted'}
(O/'simulator.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
