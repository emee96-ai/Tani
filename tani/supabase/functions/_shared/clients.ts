import { createClient, type SupabaseClient } from 'npm:@supabase/supabase-js@2.117.2';
export type AdminClient = SupabaseClient;
export function adminClient(): AdminClient {
 return createClient(Deno.env.get('SUPABASE_URL')!,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,{
  auth:{persistSession:false,autoRefreshToken:false},
  global:{fetch:(input,init)=>fetch(input,{...init,signal:AbortSignal.timeout(15_000)})}
 });
}
export function userClient(jwt: string): AdminClient {
 return createClient(Deno.env.get('SUPABASE_URL')!,Deno.env.get('SUPABASE_ANON_KEY')!,{
  auth:{persistSession:false,autoRefreshToken:false},global:{headers:{Authorization:`Bearer ${jwt}`},
   fetch:(input,init)=>fetch(input,{...init,signal:AbortSignal.timeout(15_000)})}
 });
}
