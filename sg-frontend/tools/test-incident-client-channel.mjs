import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import ts from 'typescript';
const source=readFileSync(new URL('../src/app/core/services/incident-client-channel.ts',import.meta.url),'utf8');
const {outputText}=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2022}});
const {incidentCreationSuffix}=await import('data:text/javascript;base64,'+Buffer.from(outputText).toString('base64'));
test('native iPhone declares iOS rather than Android',()=>assert.equal(incidentCreationSuffix(true,'ios'),'/ios'));
test('native Android keeps the established endpoint',()=>assert.equal(incidentCreationSuffix(true,'android'),'/android'));
test('desktop web does not gain a native channel from its platform string',()=>{
 for(const platform of ['web','ios','android'])assert.equal(incidentCreationSuffix(false,platform),'');
});
test('legacy mobile web remains compatible and a platform cannot become an arbitrary path',()=>{
 for(const platform of ['web','unknown','../admin','https://foreign.invalid'])assert.equal(incidentCreationSuffix(true,platform),'/android');
});
