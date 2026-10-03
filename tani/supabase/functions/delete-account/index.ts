import { adminClient, userClient } from '../_shared/clients.ts';
import { errorCode,json } from '../_shared/security.ts';
import { eraseAccount,type DeletionJob } from '../_shared/erasure.ts';
Deno.serve(async(req:Request)=>{
 if(req.method!=='POST')return json({error:'method_not_allowed'},405);
 const jwt=req.headers.get('Authorization')?.replace(/^Bearer\s+/i,'');
 if(!jwt)return json({error:'unauthorized'},401);
 const client=userClient(jwt);
 const {data:{user},error:authError}=await client.auth.getUser(jwt);
 if(authError||!user)return json({error:'unauthorized'},401);
 const {data:deleted,error}=await client.rpc('soft_delete_my_account');
 if(error||deleted!==true)return json({error:error?.code==='23514'?'last_admin_cannot_delete':'deletion_failed'},error?.code==='23514'?409:400);
 const admin=adminClient();
 // Revocation is best effort here; queued Auth erasure also invalidates refresh sessions.
 const {error:signoutError}=await admin.auth.admin.signOut(jwt,'global');
 if(signoutError)console.error('global_signout_failed',errorCode(signoutError));
 const {data:jobs,error:claimError}=await admin.rpc('maintenance_claim_deletions',{p_user_id:user.id});
 let cleanupPending=!!claimError;
 if(!claimError)for(const job of (jobs??[]) as DeletionJob[]) cleanupPending=!(await eraseAccount(admin,job))||cleanupPending;
 const {data:pending,error:statusError}=await admin.rpc('maintenance_deletion_pending',{p_user_id:user.id});
 return json({deleted:true,cleanup_pending:cleanupPending||!!statusError||pending===true});
});
