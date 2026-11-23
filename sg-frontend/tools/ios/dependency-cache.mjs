import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';

const critical=['@capacitor/core','@capacitor/ios','@capacitor/app','@capacitor/cli','@angular/core','@angular/cli','@angular/compiler','typescript','rxjs'];
const hash=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
export function fingerprint(root,runtime={node:process.versions.node,platform:process.platform,arch:process.arch}) {
  const modules=path.join(root,'node_modules');
  if(!fs.statSync(modules).isDirectory()||fs.lstatSync(modules).isSymbolicLink())throw Error('Dependency directory not a regular owned directory');
  return {format:1,runtime,packageJson:hash(path.join(root,'package.json')),lock:hash(path.join(root,'package-lock.json')),
    installedLock:hash(path.join(modules,'.package-lock.json')),
    packages:Object.fromEntries(critical.map(name=>[name,hash(path.join(modules,name,'package.json'))]))};
}
export function record(root,runtime) {
  const value=fingerprint(root,runtime);
  fs.writeFileSync(path.join(root,'node_modules/.futurpayment-install-receipt.json'),JSON.stringify(value)+'\n');
  return value;
}
export function verify(root,runtime) {
  try {
    const file=path.join(root,'node_modules/.futurpayment-install-receipt.json');
    if(fs.lstatSync(file).isSymbolicLink())return false;
    const saved=JSON.parse(fs.readFileSync(file,'utf8'));
    return JSON.stringify(saved)===JSON.stringify(fingerprint(root,runtime));
  }catch{return false;}
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const command=process.argv[2];
  if(command==='metadata') {
    if(!process.env.GITHUB_OUTPUT)throw Error('GitHub output destination missing');
    fs.appendFileSync(process.env.GITHUB_OUTPUT,'node_version='+process.versions.node+'\n');
    console.log('NODE_TOOLCHAIN_IDENTIFIED');
  }else {
    const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
    if(command==='record') {record(root);console.log('PUBLIC_DEPENDENCY_INSTALL_RECORDED_BEFORE_ACCOUNT_TESTS');}
    else if(command==='verify') {const valid=verify(root);console.log('DEPENDENCY_INSTALL_RECEIPT_VALID='+valid);process.exitCode=valid?0:1;}
    else throw Error('Unsupported dependency cache command');
  }
}
