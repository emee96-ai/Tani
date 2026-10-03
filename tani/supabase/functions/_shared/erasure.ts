import type { AdminClient } from './clients.ts';
import { errorCode } from './security.ts';
export type DeletionJob = { user_id: string; lease_id: string };
export async function eraseAccount(admin: AdminClient, job: DeletionJob): Promise<boolean> {
 const args = {p_user_id:job.user_id,p_lease_id:job.lease_id};
 try {
  const {data:objects,error} = await admin.rpc('maintenance_deletion_objects',args);
  if(error) throw error;
  const buckets = new Map<string,string[]>();
  for (const object of objects ?? []) {
   const paths=buckets.get(object.bucket_id)??[];paths.push(object.object_name);buckets.set(object.bucket_id,paths);
  }
  for (const [bucket,paths] of buckets) {
   for (let i=0;i<paths.length;i+=50) {
    const batch=paths.slice(i,i+50);
    const {error:storageError}=await admin.storage.from(bucket).remove(batch);
    if(storageError) throw storageError;
    for(const path of batch) {
     const {error:markError}=await admin.rpc('maintenance_mark_file_deleted',{...args,p_bucket_id:bucket,p_object_name:path});
     if(markError) throw markError;
    }
   }
  }
  // Auth API removes credentials/identities and refresh sessions while retaining referenced business records.
  const {error:authError}=await admin.auth.admin.deleteUser(job.user_id,true);
  if(authError && authError.status!==404) throw authError;
  const {error:finishError}=await admin.rpc('maintenance_finish_deletion',args);
  if(finishError) throw finishError;
  return true;
 } catch(error) {
  const {error:retryError}=await admin.rpc('maintenance_finish_deletion',{...args,p_error:errorCode(error)});
  if(retryError) console.error('deletion_retry_record_failed',errorCode(retryError));
  return false;
 }
}
