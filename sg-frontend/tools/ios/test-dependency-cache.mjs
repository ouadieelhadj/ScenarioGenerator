import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {record,verify} from './dependency-cache.mjs';
const names=['@capacitor/core','@capacitor/ios','@capacitor/app','@capacitor/cli','@angular/core','@angular/cli','@angular/compiler','typescript','rxjs'];
const runtime={node:'24.fixture',platform:'darwin',arch:'arm64'};
function fixture() {
 const requestedBase=path.resolve(process.env.FP_TEST_TMP||os.tmpdir());fs.mkdirSync(requestedBase,{recursive:true});const base=fs.realpathSync(requestedBase);
 const root=fs.mkdtempSync(path.join(base,'fp-depcache-'));
 fs.mkdirSync(path.join(root,'node_modules'));
 for(const file of ['package.json','package-lock.json','node_modules/.package-lock.json'])fs.writeFileSync(path.join(root,file),'{}');
 for(const name of names){const folder=path.join(root,'node_modules',name);fs.mkdirSync(folder,{recursive:true});fs.writeFileSync(path.join(folder,'package.json'),JSON.stringify({name,version:'1.0.0-fixture'}));}
 return {root,dispose(){const target=fs.realpathSync(root),relative=path.relative(base,target);assert(relative&&!relative.startsWith('..')&&!path.isAbsolute(relative)&&path.basename(target).startsWith('fp-depcache-'));fs.rmSync(target,{recursive:true});}};
}
for(const [label,change] of [
 ['lock modification',f=>fs.appendFileSync(path.join(f.root,'package-lock.json'),' ')],
 ['installed lock modification',f=>fs.appendFileSync(path.join(f.root,'node_modules/.package-lock.json'),' ')],
 ['critical package metadata modification',f=>fs.appendFileSync(path.join(f.root,'node_modules/@capacitor/ios/package.json'),' ')],
 ['receipt corruption',f=>fs.writeFileSync(path.join(f.root,'node_modules/.futurpayment-install-receipt.json'),'invalid')],
])test('rejects '+label,()=>{const f=fixture();try{record(f.root,runtime);assert(verify(f.root,runtime));change(f);assert.equal(verify(f.root,runtime),false);}finally{f.dispose();}});
test('accepts exact toolchain and install, rejects another Node version',()=>{const f=fixture();try{record(f.root,runtime);assert(verify(f.root,runtime));assert.equal(verify(f.root,{...runtime,node:'other'}),false);}finally{f.dispose();}});
test('missing receipt never skips a clean installation',()=>{const f=fixture();try{assert.equal(verify(f.root,runtime),false);}finally{f.dispose();}});
