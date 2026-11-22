import test from 'node:test';
import assert from 'node:assert/strict';
import ts from 'typescript';
import {readFileSync} from 'node:fs';
const source=readFileSync(new URL('../src/app/core/services/incident-file-export.ts',import.meta.url),'utf8');
const {outputText}=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2022}});
const {incidentExportName,exportIncidentBlob}=await import('data:text/javascript;base64,'+Buffer.from(outputText).toString('base64'));
const file={id:'11111111-2222-4333-8444-555555555555',contentType:'application/pdf',scanState:'CLEAN'};
const blob=new Blob(['%PDF-test'],{type:'application/pdf'}),encoded=Buffer.from('%PDF-test').toString('base64');
function ports(native=true){const calls=[];return {calls,native,readBase64:async()=>encoded,saveNative:async input=>{calls.push(['native',input]);return {saved:true};},saveWeb:(_blob,name)=>calls.push(['web',name])};}
test('trois types exacts, nom sans chemin ni saisie utilisateur',()=>{
 for(const [type,ext] of [['application/pdf','.pdf'],['image/jpeg','.jpg'],['image/png','.png']])assert.equal(incidentExportName(new Blob(['x'],{type}),{...file,contentType:type}),'incident-'+file.id+ext);
 assert.equal(incidentExportName(blob,{...file,id:file.id.toUpperCase()}),'incident-'+file.id+'.pdf');
});
test('aucun export de pièce non contrôlée, MIME incohérent ou identifiant forgé',()=>{
 for(const value of [null,{...file,scanState:'QUARANTINED'},{...file,scanState:'REJECTED'},{...file,contentType:'image/png'},{...file,contentType:'constructor'},{...file,id:'../private'},{...file,id:'not-a-uuid'}])assert.throws(()=>incidentExportName(blob,value));
 for(const size of [0,-1,NaN,1.1,10_000_001])assert.throws(()=>incidentExportName({size,type:file.contentType},file));
 assert.equal(incidentExportName({size:10_000_000,type:file.contentType},file),'incident-'+file.id+'.pdf');
});
test('web utilise le téléchargement existant et jamais le pont Android',async()=>{const p=ports(false);assert.equal(await exportIncidentBlob(blob,file,p,()=>true),'WEB_STARTED');assert.deepEqual(p.calls,[['web','incident-'+file.id+'.pdf']]);});
test('copie Android unique, confirmée seulement par le retour du sélecteur',async()=>{const p=ports();assert.equal(await exportIncidentBlob(blob,file,p,()=>true),'SAVED');assert.equal(p.calls.length,1);assert.deepEqual(p.calls[0][1],{base64:encoded,contentType:'application/pdf',filename:'incident-'+file.id+'.pdf'});});
test('annulation Android ne devient ni succès ni nouvelle tentative',async()=>{const p=ports();let count=0;p.saveNative=async()=>{count++;return {saved:false};};assert.equal(await exportIncidentBlob(blob,file,p,()=>true),'CANCELLED');assert.equal(count,1);});
test('erreur ou réponse Android incomplète sans renvoi automatique',async()=>{
 for(const result of [null,{}, {saved:'true'}]){let count=0;const p=ports();p.saveNative=async()=>{count++;return result;};await assert.rejects(exportIncidentBlob(blob,file,p,()=>true));assert.equal(count,1);}
 let count=0;const p=ports();p.saveNative=async()=>{count++;throw Error('synthetic failure');};await assert.rejects(exportIncidentBlob(blob,file,p,()=>true));assert.equal(count,1);
});
test('changement de contexte pendant la lecture empêche le sélecteur tardif',async()=>{
 const p=ports();let current=true,release;const blocked=new Promise(resolve=>release=resolve);p.readBase64=()=>blocked;
 const result=exportIncidentBlob(blob,file,p,()=>current);current=false;release(encoded);assert.equal(await result,'STALE');assert.deepEqual(p.calls,[]);
});
test('contexte déjà périmé sans accès fichier ni pont',async()=>{const p=ports();p.readBase64=()=>{throw Error('must not read');};assert.equal(await exportIncidentBlob(blob,file,p,()=>false),'STALE');assert.deepEqual(p.calls,[]);});
test('base64 invalide ou de mauvaise longueur refusé avant Android',async()=>{for(const value of ['',encoded+'AAAA','bad:payload!', ' '.repeat(encoded.length)]){const p=ports();p.readBase64=async()=>value;await assert.rejects(exportIncidentBlob(blob,file,p,()=>true));assert.deepEqual(p.calls,[]);}});
