#!/usr/bin/env python3
import argparse,json,re
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--team-id',required=True)
p.add_argument('--output',required=True,type=Path)
a=p.parse_args()
if not re.fullmatch('[A-Z0-9]{10}',a.team_id):p.error('Actual Apple Team ID required')
app_id=a.team_id+'.com.moneycore.merchantportal.test'
data={'applinks':{'details':[{'appIDs':[app_id],'components':[{'/':path} for path in ['/activation','/account/recovery','/account/verify-email','/merchant/dossier/*']]}]}}
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps(data,indent=2)+'\n')
print('ASSOCIATION_FILE_PREPARED|Not deployed. Merge with any existing Apple association before publication.')
