package com.tani.app.data
import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.contentType
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
object Supabase {
 const val URL="https://sihttimibjzoahvwuwbm.supabase.co"; const val KEY="sb_publishable_9syQMNqMr0q9V0_Z4W-jvA_OQjhHj1Y"
 private lateinit var prefs:android.content.SharedPreferences
 val json=Json{ignoreUnknownKeys=true;encodeDefaults=true;isLenient=true}; val client=HttpClient(Android){expectSuccess=false}
 fun init(context:Context){prefs=context.applicationContext.getSharedPreferences("tani_auth",Context.MODE_PRIVATE)}
 var token:String? get()=prefs.getString("access_token",null) set(v)=prefs.edit().putString("access_token",v).apply()
 var userId:String? get()=prefs.getString("user_id",null) set(v)=prefs.edit().putString("user_id",v).apply()
 fun clearSession()=prefs.edit().clear().apply()
 @PublishedApi internal fun authHeaders(b:HttpRequestBuilder){b.headers{append("apikey",KEY);append("Authorization","Bearer ${token?:KEY}")}}
 suspend inline fun <reified T> get(path:String,query:String=""):T{val r=client.get("$URL/rest/v1/$path${if(query.isBlank())"" else "?$query"}"){authHeaders(this)};return parse(r)}
 suspend inline fun <reified T> post(path:String,body:String):T{val r=client.post("$URL/rest/v1/$path"){authHeaders(this);contentType(ContentType.Application.Json);headers{append("Prefer","return=representation")};setBody(body)};return parse(r)}
 suspend inline fun <reified T> authPost(path:String,body:String):T{val r=client.post("$URL/auth/v1/$path"){headers{append("apikey",KEY);append("Authorization","Bearer $KEY")};contentType(ContentType.Application.Json);setBody(body)};return parse(r)}
 suspend inline fun <reified T> parse(r:HttpResponse):T{val text=r.bodyAsText();if(!r.status.isSuccess()){val msg=runCatching{json.parseToJsonElement(text).jsonObject["message"]?.toString()?.trim('"')}.getOrNull();error(msg?:"Supabase ${r.status.value}: $text")};return json.decodeFromString(text)}
}
