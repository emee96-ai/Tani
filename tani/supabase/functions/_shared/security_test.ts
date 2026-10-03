import { constantTimeEqual, errorCode, json } from './security.ts';
import { firebaseAccessToken, pushPayload } from './fcm.ts';
function assert(value:unknown,message='assertion failed'):asserts value { if(!value)throw new Error(message); }
Deno.test('worker authentication rejects different and prefix credentials',()=>{
 assert(!constantTimeEqual('','worker'));assert(!constantTimeEqual('a'.repeat(63),'a'.repeat(64)));
 assert(!constantTimeEqual('a'.repeat(63)+'b','a'.repeat(64)));assert(constantTimeEqual('a'.repeat(64),'a'.repeat(64)));
});
Deno.test('push payload contains only owner and notification identifiers',()=>{
 const payload=pushPayload({id:'job',lease_id:'lease',token:'token',user_id:'owner',notification_id:'notification'});
 assert(JSON.stringify(payload.message.data)==JSON.stringify({user_id:'owner',notification_id:'notification'}));
 assert(!('notification' in payload.message));assert(payload.message.android.ttl==='86400s');
});
Deno.test('error responses never include private exception messages',async()=>{
 const result=json({error:errorCode(new Error('private credential'))},503);
 assert(result.status===503);assert(result.headers.get('Cache-Control')==='no-store');
 assert(!(await result.text()).includes('private credential'));
});
Deno.test('OAuth uses a verifiable RS256 signature and messaging-only scope',async()=>{
 const pair=await crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
 const der=new Uint8Array(await crypto.subtle.exportKey('pkcs8',pair.privateKey));
 const pem=btoa(String.fromCharCode(...der)).match(/.{1,64}/g)!.join('\n');
 const previous=globalThis.fetch;
 const decode=(s:string)=>Uint8Array.from(atob(s.replace(/-/g,'+').replace(/_/g,'/')),x=>x.charCodeAt(0));
 let checked=false;
 globalThis.fetch=async(input,init)=>{
  assert(String(input)==='https://oauth2.googleapis.com/token');
  const form=new URLSearchParams(String(init?.body));
  const parts=form.get('assertion')!.split('.');
  assert(JSON.parse(new TextDecoder().decode(decode(parts[0]))).alg==='RS256');
  const claims=JSON.parse(new TextDecoder().decode(decode(parts[1])));
  assert(claims.scope==='https://www.googleapis.com/auth/firebase.messaging');assert(claims.exp-claims.iat===3600);
  assert(await crypto.subtle.verify('RSASSA-PKCS1-v1_5',pair.publicKey,decode(parts[2]),new TextEncoder().encode(parts.slice(0,2).join('.'))));
  checked=true;return new Response(JSON.stringify({access_token:'test-token'}),{status:200,headers:{'Content-Type':'application/json'}});
 };
 try {
  const token=await firebaseAccessToken({project_id:'tani-qa',client_email:'qa@tani-qa.iam.gserviceaccount.com',private_key:`-----BEGIN PRIVATE KEY-----\n${pem}\n-----END PRIVATE KEY-----`});
  assert(token==='test-token'&&checked);
 }finally{globalThis.fetch=previous;}
});
