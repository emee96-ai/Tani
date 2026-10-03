export type FirebaseAccount = { project_id:string; client_email:string; private_key:string };
function base64url(bytes:Uint8Array):string {
 let raw='';for(const byte of bytes)raw+=String.fromCharCode(byte);
 return btoa(raw).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
}
const encoder=new TextEncoder();
export function firebaseAccount(raw:string):FirebaseAccount {
 const value=JSON.parse(raw) as FirebaseAccount;
 if(!/^[a-z][a-z0-9-]{4,61}[a-z0-9]$/.test(value.project_id)||!value.client_email?.endsWith('.iam.gserviceaccount.com')||!value.private_key?.includes('BEGIN PRIVATE KEY')) throw new Error('firebase_configuration_invalid');
 return value;
}
export async function firebaseAccessToken(account:FirebaseAccount):Promise<string> {
 const now=Math.floor(Date.now()/1000);
 const unsigned=base64url(encoder.encode(JSON.stringify({alg:'RS256',typ:'JWT'})))+'.'+base64url(encoder.encode(JSON.stringify({iss:account.client_email,scope:'https://www.googleapis.com/auth/firebase.messaging',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600})));
 const der=Uint8Array.from(atob(account.private_key.replace(/-----[^-]+-----/g,'').replace(/\s/g,'')),c=>c.charCodeAt(0));
 const key=await crypto.subtle.importKey('pkcs8',der,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
 const signature=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key,encoder.encode(unsigned));
 const response=await fetch('https://oauth2.googleapis.com/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion:unsigned+'.'+base64url(new Uint8Array(signature))}),signal:AbortSignal.timeout(15_000)});
 const result=await response.json();
 if(!response.ok||typeof result.access_token!=='string')throw new Error('firebase_oauth_failed');
 return result.access_token;
}
export type PushJob={id:string;lease_id:string;token:string;user_id:string;notification_id:string};
export function pushPayload(job:PushJob) {
 // Data-only: the client checks the current account before displaying anything.
 return {message:{token:job.token,data:{user_id:job.user_id,notification_id:job.notification_id},android:{priority:'high',ttl:'86400s',collapse_key:job.notification_id}}};
}
export async function sendPush(account:FirebaseAccount,access:string,job:PushJob):Promise<{error:string|null;invalid:boolean}> {
 const response=await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,{method:'POST',headers:{Authorization:`Bearer ${access}`,'Content-Type':'application/json'},body:JSON.stringify(pushPayload(job)),signal:AbortSignal.timeout(15_000)});
 if(response.ok)return{error:null,invalid:false};
 const body=await response.json().catch(()=>({}));
 const code=body.error?.details?.find((x:{errorCode?:string})=>x.errorCode)?.errorCode;
 return{error:String(code??`fcm_http_${response.status}`).slice(0,80),invalid:code==='UNREGISTERED'};
}
