// Candidate assets in the browser; real HTTPS APIs and current CSP preserved.
// No business writes: only the four normal authentication submissions are allowed.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process');
const {chromium}=require('@playwright/test');
const F=process.cwd(),OUT=path.join(process.env.FP_IOS_OUTPUT,'preuves-publiables','framework-web');fs.mkdirSync(OUT,{recursive:true});
const report={utc:new Date().toISOString(),candidateAssetsSubstitutedInBrowser:true,actuallyDeployed:false,realPublicApi:true,
 currentDocumentSecurityHeadersPreserved:true,businessWritesAllowed:false,checks:[],routes:[],authAttempts:[],blockedMutations:[],javascriptErrors:0};
let password=process.env.FP_IOS_TEST_PASSWORD, browser;
const save=()=>fs.writeFileSync(path.join(OUT,'resultats.json'),JSON.stringify(report,null,2));
function check(name,value){report.checks.push({name,passed:!!value});save();if(!value)throw Error(name);}
const accounts=[
 ['bim.amina',[['/merchant/dashboard','app-merchant-dashboard'],['/merchant/hierarchy','app-merchant-hierarchy'],['/merchant/requests','app-merchant-operations'],['/merchant/chargebacks','app-merchant-chargebacks'],['/merchant/incidents','app-merchant-incidents']]],
 ['damancash.karim',[['/commercial/dossiers','app-merchant-commercial-queue'],['/commercial/prospects/new','app-merchant-prospect']]],
 ['damancash.salma',[['/backoffice/onboarding','app-merchant-review']]],
 ['admin',[['/administration/roles','app-roles']]],
];
const origin='https://merchant.futurpayment.com';
(async()=>{try{
 check('Existing test credential available',typeof password==='string'&&password.length>0);
 const installed=JSON.parse(fs.readFileSync(path.join(F,'node_modules/@angular/core/package.json'),'utf8'));
 report.angular=installed.version;report.lockSha256=crypto.createHash('sha256').update(fs.readFileSync(path.join(F,'package-lock.json'))).digest('hex');
 check('Actual compiled framework is Angular21',installed.version==='21.2.26');
 cp.execFileSync(process.execPath,['node_modules/@angular/cli/bin/ng.js','build','merchant-portal-web','--configuration','production','--source-map=false'],{cwd:F,stdio:'pipe',timeout:480000,maxBuffer:4*1024*1024});
 const root=path.resolve(F,'dist/merchant-portal-web/browser');check('New portal asset build present',fs.existsSync(path.join(root,'index.html')));
 const chrome='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
 if(!fs.existsSync(chrome))cp.execFileSync(process.execPath,['node_modules/playwright/cli.js','install','chromium'],{cwd:F,stdio:'pipe',timeout:300000,maxBuffer:4*1024*1024});
 browser=await chromium.launch({headless:true,...(fs.existsSync(chrome)?{executablePath:chrome}:{})});
 const baseline=await (await browser.newContext()).request.get(origin+'/login');check('Current public login has security policy',baseline.ok()&&!!baseline.headers()['content-security-policy']);
 const security={};for(const key of ['content-security-policy','x-content-type-options','referrer-policy','x-frame-options'])if(baseline.headers()[key])security[key]=baseline.headers()[key];
 for(const [account,pages] of accounts){
  const context=await browser.newContext({viewport:{width:1365,height:900}});let goodApi=0;
  await context.route(origin+'/**',async route=>{
   const request=route.request(),url=new URL(request.url());
   if(url.pathname.startsWith('/api-merchant-portal/')){
    if(!['GET','HEAD'].includes(request.method())&&!url.pathname.endsWith('/auth/login')){
     report.blockedMutations.push({account,path:url.pathname,method:request.method()});save();return route.abort();
    }
    return route.continue();
   }
   if(request.method()!=='GET')return route.continue();
   const decoded=decodeURIComponent(url.pathname),candidate=path.resolve(root,'.'+decoded);
   if(!candidate.startsWith(root+path.sep)&&candidate!==root)return route.abort();
   const file=fs.existsSync(candidate)&&fs.statSync(candidate).isFile()?candidate:path.join(root,'index.html');
   const ext=path.extname(file);const types={'.html':'text/html; charset=utf-8','.js':'application/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.woff2':'font/woff2'};
   return route.fulfill({status:200,headers:{...security,'cache-control':'no-store','content-type':types[ext]||'application/octet-stream'},body:fs.readFileSync(file)});
  });
  const page=await context.newPage();page.on('pageerror',()=>{report.javascriptErrors++;});
  page.on('response',r=>{if(new URL(r.url()).pathname.startsWith('/api-merchant-portal/')&&r.ok())goodApi++;});
  await page.goto(origin+'/login',{waitUntil:'domcontentloaded'});
  await page.locator('input[name=login]').fill(account);await page.locator('input[name=password]').fill(password);
  const auth=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/auth/login'),{timeout:45000});
  await page.locator('button[type=submit]').click();const response=await auth;report.authAttempts.push({account,http:response.status()});check(account+' authenticates once',response.ok());
  for(const [url,selector] of pages){
   const priorErrors=report.javascriptErrors;
   await page.goto(origin+url,{waitUntil:'domcontentloaded'});
   await page.locator(selector).waitFor({state:'visible',timeout:45000});
   check(account+' renders '+url,new URL(page.url()).pathname===url);
   check(account+' has no runtime error on '+url,report.javascriptErrors===priorErrors);
   if(url==='/merchant/dashboard')check('Merchant account identity retained',(await page.locator(selector).innerText()).includes(account));
   if(url==='/commercial/prospects/new')check('Empty prospect cannot be submitted',await page.getByRole('button',{name:'Créer et inviter',exact:true}).isDisabled());
   if(url==='/merchant/requests')check('RIB and TPE request screen available',/RIB/.test(await page.locator(selector).innerText())&&/TPE/.test(await page.locator(selector).innerText()));
   report.routes.push({account,path:url,component:selector});save();
   await page.screenshot({path:path.join(OUT,'page-'+report.routes.length+'.png'),mask:[page.locator('input')],fullPage:false});
  }
  check(account+' reads actual API data',goodApi>1);await context.close();
 }
 check('No unexpected business mutation attempted',report.blockedMutations.length===0);
 check('Exactly four successful authentications',report.authAttempts.length===4&&report.authAttempts.every(x=>x.http===200));
 check('All nine planned module screens inspected',report.routes.length===9);
 report.allExecutedChecksPassed=report.checks.every(x=>x.passed);report.screenshotsAwaitingReview=true;
}catch(e){report.failureType=e.name;report.allExecutedChecksPassed=false;process.exitCode=1;}
finally{password='';if(browser)await browser.close();save();console.log(JSON.stringify({allExecutedChecksPassed:report.allExecutedChecksPassed,checks:report.checks.length,routes:report.routes.length,lastCheck:report.checks.at(-1)}));}})();
