const SUPABASE_URL = "https://sihttimibjzoahvwuwbm.supabase.co";
const PUBLISHABLE_KEY = "sb_publishable_9syQMNqMr0q9V0_Z4W-jvA_OQjhHj1Y";

let session = JSON.parse(sessionStorage.getItem("tani_admin_session") || "null");
let currentRole = null;

const $ = id => document.getElementById(id);
const headers = (extra={}) => ({
  apikey: PUBLISHABLE_KEY,
  Authorization: `Bearer ${session?.access_token || PUBLISHABLE_KEY}`,
  "Content-Type": "application/json",
  ...extra
});

async function authFetch(path, options={}) {
  let response = await fetch(`${SUPABASE_URL}${path}`, {...options, headers: headers(options.headers)});
  if (response.status === 401 && session?.refresh_token) {
    const ok = await refreshSession();
    if (ok) response = await fetch(`${SUPABASE_URL}${path}`, {...options, headers: headers(options.headers)});
  }
  return response;
}

async function refreshSession() {
  try {
    const response = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=refresh_token`, {
      method: "POST",
      headers: {apikey:PUBLISHABLE_KEY,"Content-Type":"application/json"},
      body: JSON.stringify({refresh_token: session.refresh_token})
    });
    if (!response.ok) return false;
    const data = await response.json();
    session = {access_token:data.access_token, refresh_token:data.refresh_token || session.refresh_token, user:data.user};
    sessionStorage.setItem("tani_admin_session", JSON.stringify(session));
    return true;
  } catch { return false; }
}

async function login() {
  $("loginStatus").textContent = "";
  $("loginBtn").disabled = true;
  try {
    const response = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
      method:"POST",
      headers:{apikey:PUBLISHABLE_KEY,"Content-Type":"application/json"},
      body:JSON.stringify({email:$("email").value.trim(),password:$("password").value})
    });
    if (!response.ok) throw new Error("تعذر تسجيل الدخول");
    const data = await response.json();
    session = {access_token:data.access_token,refresh_token:data.refresh_token,user:data.user};
    sessionStorage.setItem("tani_admin_session", JSON.stringify(session));
    await enterDashboard();
  } catch (e) {
    sessionStorage.removeItem("tani_admin_session"); session=null;
    $("loginStatus").textContent = e.message || "تعذر تسجيل الدخول";
  } finally { $("loginBtn").disabled = false; }
}

async function getMyRole() {
  const uid = session?.user?.id;
  if (!uid) throw new Error("جلسة غير صالحة");
  const response = await authFetch(`/rest/v1/profiles?id=eq.${encodeURIComponent(uid)}&select=role,is_active&limit=1`);
  if (!response.ok) throw new Error("تعذر التحقق من صلاحية الحساب");
  const rows = await response.json();
  const profile = rows[0];
  if (!profile?.is_active || !["admin","support"].includes(profile?.role)) throw new Error("هذا الحساب لا يملك صلاحية لوحة الإدارة");
  return profile.role;
}

async function enterDashboard() {
  currentRole = await getMyRole();
  $("loginView").classList.add("hidden");
  $("dashboard").classList.remove("hidden");
  $("roleBadge").textContent = currentRole === "admin" ? "Admin" : "Support";
  await loadDashboard();
}

async function restRows(table, query) {
  const response = await authFetch(`/rest/v1/${table}?${query}`);
  if (!response.ok) throw new Error(`تعذر تحميل ${table}`);
  return response.json();
}

async function exactCount(table, filter="") {
  const q = `select=id${filter ? `&${filter}` : ""}&limit=1`;
  const response = await authFetch(`/rest/v1/${table}?${q}`, {headers:{Prefer:"count=exact",Range:"0-0"}});
  if (!response.ok) return 0;
  const range = response.headers.get("content-range") || "0/0";
  const total = Number(range.split("/").pop());
  return Number.isFinite(total) ? total : 0;
}

async function loadMetrics() {
  const [orders, completed, cancelled, pendingMerchants, complaints, errors, users] = await Promise.all([
    exactCount("orders"), exactCount("orders","status=eq.delivered"), exactCount("orders","status=eq.cancelled"),
    exactCount("merchant_profiles","verification_status=eq.pending"), exactCount("complaints","status=neq.closed"),
    exactCount("app_errors"), exactCount("profiles")
  ]);
  const cards = [
    ["الطلبات",orders],["المكتملة",completed],["الملغاة",cancelled],["طلبات تجار معلقة",pendingMerchants],
    ["شكاوى مفتوحة",complaints],["أخطاء مسجلة",errors],["الحسابات",users],["Completion", orders ? `${Math.round(completed/orders*100)}%` : "0%"]
  ];
  const box=$("metrics"); box.replaceChildren();
  cards.forEach(([label,value])=>{const el=document.createElement("div");el.className="metric";const s=document.createElement("span");s.textContent=label;const b=document.createElement("strong");b.textContent=value;el.append(s,b);box.append(el)});
}

function fmtDate(v){return v ? new Date(v).toLocaleString("ar") : "—"}
function statusText(v){return ({pending:"قيد المراجعة",changes_requested:"تعديلات مطلوبة",approved:"مقبول",rejected:"مرفوض",suspended:"موقوف"})[v] || v || "—"}

async function loadMerchants() {
  const rows = await restRows("merchant_profiles","select=id,business_name,store_name,phone,phone_verified_at,verification_status,review_note,submitted_at,created_at&order=created_at.desc&limit=50");
  const body=$("merchantsBody"); body.replaceChildren();
  for (const m of rows) {
    const docs = await restRows("merchant_identity_documents",`select=storage_path,document_type,created_at&merchant_id=eq.${encodeURIComponent(m.id)}&order=created_at.desc&limit=1`);
    const tr=document.createElement("tr");
    const values=[m.business_name,m.store_name||"—",`${m.phone||"—"}${m.phone_verified_at?" ✓":""}`,statusText(m.verification_status)];
    values.forEach(value=>{const td=document.createElement("td");td.textContent=value;tr.append(td)});
    const docTd=document.createElement("td");
    if(docs[0]){const b=document.createElement("button");b.className="secondary";b.textContent="فتح";b.onclick=()=>openMerchantDocument(docs[0].storage_path);docTd.append(b)} else docTd.textContent="—";
    tr.append(docTd);
    const dateTd=document.createElement("td");dateTd.textContent=fmtDate(m.submitted_at||m.created_at);tr.append(dateTd);
    const action=document.createElement("td");
    if(currentRole==="admin" && ["pending","changes_requested","rejected"].includes(m.verification_status)){
      const wrap=document.createElement("div");wrap.className="actions";
      const approve=document.createElement("button");approve.textContent="قبول";approve.onclick=()=>reviewMerchant(m,"approved");
      const changes=document.createElement("button");changes.textContent="طلب تعديل";changes.className="secondary";changes.onclick=()=>reviewMerchant(m,"changes_requested");
      const reject=document.createElement("button");reject.textContent="رفض";reject.className="danger";reject.onclick=()=>reviewMerchant(m,"rejected");
      wrap.append(approve,changes,reject);action.append(wrap);
    } else if(currentRole==="admin" && m.verification_status==="approved") {
      const suspend=document.createElement("button");suspend.textContent="تعليق";suspend.className="danger";suspend.onclick=()=>reviewMerchant(m,"suspended");action.append(suspend);
    } else action.textContent=m.review_note||"—";
    tr.append(action);body.append(tr);
  }
  if(!rows.length){const tr=document.createElement("tr");const td=document.createElement("td");td.colSpan=7;td.className="empty";td.textContent="لا توجد طلبات تجار";tr.append(td);body.append(tr)}
}

async function openMerchantDocument(path){
  const response=await authFetch(`/storage/v1/object/authenticated/merchant-private/${path}`);
  if(!response.ok){$("dashboardStatus").textContent="تعذر فتح مستند الهوية";return}
  const blob=await response.blob();
  const url=URL.createObjectURL(blob);
  window.open(url,"_blank","noopener,noreferrer");
  setTimeout(()=>URL.revokeObjectURL(url),60000);
}

async function reviewMerchant(merchant,status){
  if(currentRole!=="admin") return;
  const needsNote=status!=="approved";
  const note=needsNote ? (prompt(status==="changes_requested"?"ما التعديلات المطلوبة؟":status==="rejected"?"سبب الرفض":"سبب التعليق")||"").trim() : "";
  if(needsNote && !note) return;
  const response=await authFetch("/rest/v1/rpc/admin_review_merchant_application",{method:"POST",body:JSON.stringify({p_merchant_id:merchant.id,p_status:status,p_note:note||null})});
  if(!response.ok){const data=await response.json().catch(()=>({}));$("dashboardStatus").textContent=data.message||"تعذر تحديث حالة التاجر";return}
  $("dashboardStatus").textContent="تم تحديث حالة التاجر";
  await loadDashboard();
}

async function setMerchantStatus(id,status){
  if(currentRole!=="admin") return;
  const response = await authFetch("/rest/v1/rpc/admin_set_merchant_status",{method:"POST",body:JSON.stringify({p_merchant_id:id,p_status:status})});
  if(!response.ok){$("dashboardStatus").textContent="تعذر تحديث حالة التاجر";return}
  $("dashboardStatus").textContent="تم تحديث حالة التاجر";
  await loadDashboard();
}

function renderList(target, rows, formatter) {
  const box=$(target);box.replaceChildren();
  if(!rows.length){const e=document.createElement("div");e.className="empty";e.textContent="لا توجد بيانات";box.append(e);return}
  rows.forEach(row=>{const item=document.createElement("div");item.className="list-item";const [title,sub]=formatter(row);const strong=document.createElement("strong");strong.textContent=title;const muted=document.createElement("div");muted.className="muted";muted.textContent=sub;item.append(strong,muted);box.append(item)})
}

async function loadLists(){
  const [complaints, errors, audits] = await Promise.all([
    restRows("complaints","select=id,subject,status,priority,created_at&order=created_at.desc&limit=20"),
    restRows("app_errors","select=source,error_type,message,created_at&order=created_at.desc&limit=20"),
    restRows("audit_logs","select=action,entity_type,entity_id,source,created_at&order=created_at.desc&limit=30")
  ]);
  actionList("complaintsList",complaints,r=>r.subject,r=>`${r.status} • ${r.priority} • ${fmtDate(r.created_at)}`,r=>[
    actionButton("قيد المعالجة","secondary",()=>updateComplaint(r.id,"in_progress")),
    actionButton("حل","",()=>resolveComplaint(r.id))
  ]);
  renderList("errorsList",errors,r=>[`${r.error_type} — ${r.source}`,`${(r.message||"").slice(0,160)} • ${fmtDate(r.created_at)}`]);
  renderList("auditList",audits,r=>[`${r.action} — ${r.entity_type}`,`${r.entity_id||"—"} • ${r.source||"—"} • ${fmtDate(r.created_at)}`]);
}

async function loadDashboard(){
  $("dashboardStatus").textContent="";
  try{await Promise.all([loadMetrics(),loadMerchants(),loadLists()])}
  catch(e){$("dashboardStatus").textContent=e.message||"تعذر تحميل لوحة الإدارة"}
}

async function logout(){
  if(session?.access_token){try{await fetch(`${SUPABASE_URL}/auth/v1/logout`,{method:"POST",headers:{apikey:PUBLISHABLE_KEY,Authorization:`Bearer ${session.access_token}`}})}catch{}}
  sessionStorage.removeItem("tani_admin_session");session=null;currentRole=null;location.reload();
}

$("loginBtn").addEventListener("click",login);
$("logoutBtn").addEventListener("click",logout);
$("refreshBtn").addEventListener("click",loadDashboard);
$("password").addEventListener("keydown",e=>{if(e.key==="Enter")login()});

if(session){enterDashboard().catch(()=>{sessionStorage.removeItem("tani_admin_session");session=null})}

async function rpc(name, body={}) {
  const response = await authFetch(`/rest/v1/rpc/${name}`, {method:"POST", body:JSON.stringify(body)});
  if(!response.ok){const data=await response.json().catch(()=>({}));throw new Error(data.message||`تعذر تنفيذ ${name}`)}
  const text=await response.text(); return text ? JSON.parse(text) : null;
}

function metricCards(target, entries){
  const box=$(target); box.replaceChildren();
  entries.forEach(([label,value])=>{const el=document.createElement("div");el.className="metric";const s=document.createElement("span");s.textContent=label;const b=document.createElement("strong");b.textContent=value;el.append(s,b);box.append(el)});
}

function actionList(target, rows, titleFn, subFn, actionsFn){
  const box=$(target); box.replaceChildren();
  if(!rows.length){const e=document.createElement("div");e.className="empty";e.textContent="لا توجد بيانات";box.append(e);return}
  rows.forEach(row=>{const item=document.createElement("div");item.className="list-item";const strong=document.createElement("strong");strong.textContent=titleFn(row);const muted=document.createElement("div");muted.className="muted";muted.textContent=subFn(row);item.append(strong,muted);const actions=actionsFn?.(row)||[];if(actions.length){const wrap=document.createElement("div");wrap.className="actions";actions.forEach(a=>wrap.append(a));item.append(wrap)}box.append(item)});
}
function actionButton(label, klass, handler){const b=document.createElement("button");b.textContent=label;if(klass)b.className=klass;b.onclick=handler;return b}

async function loadAdvanced(){
  const [kpis, trust, tickets, subReqs, featReqs, cities, providers, reports] = await Promise.all([
    rpc("weekly_marketplace_kpis",{}).catch(()=>({})),
    restRows("merchant_trust_scores","select=*&order=trust_score.desc&limit=30"),
    restRows("support_tickets","select=id,subject,status,priority,user_id,created_at&order=created_at.desc&limit=30"),
    restRows("subscription_requests","select=*&order=created_at.desc&limit=30"),
    restRows("featured_requests","select=*&order=created_at.desc&limit=30"),
    restRows("market_cities","select=*&order=sort_order.asc,name.asc&limit=50"),
    restRows("delivery_providers","select=code,display_name,provider_type,is_active,supported_cities&order=display_name.asc&limit=50"),
    restRows("review_reports","select=*&order=created_at.desc&limit=30")
  ]);
  metricCards("weeklyKpis",[
    ["طلبات",kpis.orders||0],["مكتملة",kpis.completed_orders||0],["ملغاة",kpis.cancelled_orders||0],["GMV",kpis.gmv||0],
    ["عملاء نشطون",kpis.active_customers||0],["تجار نشطون",kpis.active_merchants||0],["مشاهدات",kpis.product_views||0],["أخطاء",kpis.app_errors||0]
  ]);
  renderList("trustList",trust,r=>[`Score ${Number(r.trust_score||0).toFixed(0)} — ${r.trust_level}`,`تقييم ${r.average_rating||0} • إكمال ${r.completion_rate||0}% • شكاوى ${r.open_complaints||0}`]);
  actionList("ticketsList",tickets,r=>r.subject,r=>`${r.status} • ${r.priority} • ${fmtDate(r.created_at)}`,r=>[
    actionButton("بدء المعالجة","secondary",()=>updateTicket(r.id,"in_progress")),
    actionButton("إغلاق","",()=>updateTicket(r.id,"closed"))
  ]);
  actionList("subscriptionRequestsList",subReqs,r=>`طلب اشتراك ${r.plan_id.slice(0,8)}`,r=>`${r.status} • ${fmtDate(r.created_at)}`,r=>currentRole==="admin"&&r.status==="pending"?[
    actionButton("قبول","",()=>reviewSubscription(r.id,"approved")),actionButton("رفض","danger",()=>reviewSubscription(r.id,"rejected"))]:[]);
  actionList("featuredRequestsList",featReqs,r=>`${r.placement} • ${r.product_id?"منتج":"متجر"}`,r=>`${r.status} • ${fmtDate(r.created_at)}`,r=>currentRole==="admin"&&r.status==="pending"?[
    actionButton("قبول","",()=>reviewFeatured(r.id,"approved")),actionButton("رفض","danger",()=>reviewFeatured(r.id,"rejected"))]:[]);
  renderList("citiesList",cities,r=>[`${r.name}${r.is_active?" ✓":""}`,`${r.code} • ${r.state_name||"—"}`]);
  renderList("deliveryProvidersList",providers,r=>[`${r.display_name}${r.is_active?" ✓":""}`,`${r.provider_type} • ${(r.supported_cities||[]).join(", ")}`]);
  actionList("reviewReportsList",reports,r=>`بلاغ تقييم ${r.review_id.slice(0,8)}`,r=>`${r.reason} • ${r.status} • ${fmtDate(r.created_at)}`,r=>r.status==="pending"?[
    actionButton("إخفاء التقييم","danger",()=>moderateReviewReport(r,"hide")),
    actionButton("رفض البلاغ","secondary",()=>moderateReviewReport(r,"dismiss"))
  ]:[]);
}


async function patchRow(table,id,body){
  const response=await authFetch(`/rest/v1/${table}?id=eq.${encodeURIComponent(id)}`,{method:"PATCH",headers:{Prefer:"return=minimal"},body:JSON.stringify(body)});
  if(!response.ok){const data=await response.json().catch(()=>({}));throw new Error(data.message||`تعذر تحديث ${table}`)}
}
async function updateComplaint(id,status){
  try{await patchRow("complaints",id,{status,updated_at:new Date().toISOString()});$("dashboardStatus").textContent="تم تحديث الشكوى";await loadDashboard()}catch(e){$("dashboardStatus").textContent=e.message}
}
async function resolveComplaint(id){
  const resolution=(prompt("اكتبي قرار/حل الشكوى")||"").trim();if(!resolution)return;
  try{await patchRow("complaints",id,{status:"resolved",resolution,resolved_at:new Date().toISOString(),resolved_by:session?.user?.id,updated_at:new Date().toISOString()});$("dashboardStatus").textContent="تم حل الشكوى";await loadDashboard()}catch(e){$("dashboardStatus").textContent=e.message}
}
async function updateTicket(id,status){
  try{await patchRow("support_tickets",id,{status,assigned_to:session?.user?.id,updated_at:new Date().toISOString()});$("dashboardStatus").textContent="تم تحديث التذكرة";await loadAdvanced()}catch(e){$("dashboardStatus").textContent=e.message}
}
async function moderateReviewReport(report,action){
  try{
    if(action==="hide"){await patchRow("reviews",report.review_id,{status:"hidden",moderation_reason:report.reason});await patchRow("review_reports",report.id,{status:"actioned"})}
    else await patchRow("review_reports",report.id,{status:"dismissed"});
    $("dashboardStatus").textContent="تمت معالجة البلاغ";await loadAdvanced();
  }catch(e){$("dashboardStatus").textContent=e.message}
}

async function reviewSubscription(id,status){
  try{await rpc("admin_review_subscription_request",{p_request_id:id,p_status:status,p_note:null});$("dashboardStatus").textContent="تم تحديث طلب الاشتراك";await loadAdvanced()}catch(e){$("dashboardStatus").textContent=e.message}
}
async function reviewFeatured(id,status){
  try{await rpc("admin_review_featured_request",{p_request_id:id,p_status:status,p_note:null});$("dashboardStatus").textContent="تم تحديث طلب الظهور";await loadAdvanced()}catch(e){$("dashboardStatus").textContent=e.message}
}

const originalLoadDashboard = loadDashboard;
loadDashboard = async function(){
  $("dashboardStatus").textContent="";
  try{await Promise.all([loadMetrics(),loadMerchants(),loadLists(),loadAdvanced()])}
  catch(e){$("dashboardStatus").textContent=e.message||"تعذر تحميل لوحة الإدارة"}
};
