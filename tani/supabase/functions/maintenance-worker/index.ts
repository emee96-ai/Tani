import { adminClient } from '../_shared/clients.ts';
import { constantTimeEqual, errorCode, json } from '../_shared/security.ts';
import { eraseAccount, type DeletionJob } from '../_shared/erasure.ts';
import { firebaseAccount, firebaseAccessToken, sendPush, type PushJob } from '../_shared/fcm.ts';
Deno.serve(async (req:Request)=>{
 if(req.method!=='POST')return json({error:'method_not_allowed'},405);
 const supplied=req.headers.get('X-Tani-Worker-Key')??'';
 if(supplied.length!==64)return json({error:'unauthorized'},401);
 const admin=adminClient();
 const {data:key,error:keyError}=await admin.rpc('maintenance_worker_key');
 if(keyError||typeof key!=='string'||!constantTimeEqual(supplied,key))return json({error:'unauthorized'},401);
 try {
  const {data:deletions,error}=await admin.rpc('maintenance_claim_deletions');
  if(error)throw error;
  let erased=0;
  for(const job of (deletions??[]) as DeletionJob[]) if(await eraseAccount(admin,job))erased++;
  const raw=Deno.env.get('FIREBASE_SERVICE_ACCOUNT');
  if(!raw) {
   await admin.rpc('maintenance_record_health',{p_push_configured:false,p_erased:erased});
   return json({erased,push:'configuration_required'});
  }
  const account=firebaseAccount(raw);
  // Obtain OAuth credentials before claiming jobs; missing credentials never consume retry attempts.
  const access=await firebaseAccessToken(account);
  const {data:jobs,error:claimError}=await admin.rpc('maintenance_claim_push_deliveries');
  if(claimError)throw claimError;
  let sent=0;
  const pending=(jobs??[]) as PushJob[];
  for(let i=0;i<pending.length;i+=5)await Promise.all(pending.slice(i,i+5).map(async job=>{
   let result:{error:string|null;invalid:boolean};
   try{result=await sendPush(account,access,job);}catch(e){result={error:errorCode(e),invalid:false};}
   const {error:ackError}=await admin.rpc('maintenance_ack_push_delivery',{p_id:job.id,p_lease_id:job.lease_id,p_error:result.error,p_invalid_token:result.invalid});
   if(ackError)throw ackError;
   if(!result.error)sent++;
  }));
  await admin.rpc('maintenance_record_health',{p_push_configured:true,p_erased:erased,p_sent:sent});
  return json({erased,sent});
 }catch(error){await admin.rpc('maintenance_record_health',{p_push_configured:false,p_error:errorCode(error)});console.error('maintenance_failed',errorCode(error));return json({error:'maintenance_failed'},503);}
});
