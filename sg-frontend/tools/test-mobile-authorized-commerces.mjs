import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),ts=require('typescript');
const source=readFileSync(new URL('../src/mobile/services/mobile-authorized-commerces.ts',import.meta.url),'utf8');
const js=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2022}}).outputText;
const {mobileAuthorizedCommerces:select}=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'));
const row=(extra={})=>({id:'association',hierarchyNodeId:'merchant',hierarchyLabel:'Server commerce',acquirerCode:'DEMO',state:'ACTIVE',role:'CHECKER',...extra});
test('display the active association and exclude revoked/pending states',()=>{
 const active=row();assert.deepEqual(select([row({id:'old',state:'REVOKED'}),active,row({state:'PENDING'})]),[active]);
});
test('deduplicate within the same acquirer without merging distinct merchants or acquirers',()=>{
 const first=row(),second=row({id:'second',hierarchyNodeId:'other'}),third=row({id:'third',acquirerCode:'OTHER'});
 assert.deepEqual(select([first,row({id:'duplicate'}),second,third]),[first,second,third]);
});
test('keep authoritative labels and never manufacture missing company names',()=>{
 const real=row({hierarchyLabel:'Name from server'});
 assert.deepEqual(select([row({hierarchyLabel:'  '}),row({hierarchyNodeId:''}),row({acquirerCode:''}),real]),[real]);
});
test('filter without modifying the source associations',()=>{
 const active=Object.freeze(row()),revoked=Object.freeze(row({state:'REVOKED'})),all=Object.freeze([revoked,active]);
 assert.deepEqual(select(all),[active]);assert.equal(all.length,2);assert.equal(all[0].state,'REVOKED');
});
