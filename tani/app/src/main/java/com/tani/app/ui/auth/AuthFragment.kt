package com.tani.app.ui.auth
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Repository
import com.tani.app.data.Supabase
import kotlinx.coroutines.launch
class AuthFragment:Fragment(R.layout.fragment_auth){
 override fun onViewCreated(v:View,s:Bundle?){
  val name=v.findViewById<EditText>(R.id.name);val phone=v.findViewById<EditText>(R.id.phone);val email=v.findViewById<EditText>(R.id.email);val pass=v.findViewById<EditText>(R.id.password);val action=v.findViewById<Button>(R.id.action);val toggle=v.findViewById<TextView>(R.id.toggle);val status=v.findViewById<TextView>(R.id.status);var signup=false
  fun render(){name.visibility=if(signup)View.VISIBLE else View.GONE;phone.visibility=if(signup)View.VISIBLE else View.GONE;action.text=if(signup)"إنشاء حساب" else "تسجيل الدخول";toggle.text=if(signup)"لديك حساب؟ تسجيل الدخول" else "ليس لديك حساب؟ إنشاء حساب"}
  render();toggle.setOnClickListener{signup=!signup;render();status.text=""}
  action.setOnClickListener{lifecycleScope.launch{action.isEnabled=false;runCatching{if(signup)Repository().signup(email.text.toString(),pass.text.toString(),name.text.toString(),phone.text.toString())else Repository().login(email.text.toString(),pass.text.toString())}.onSuccess{if(signup&&Supabase.token==null)status.text="تم إنشاء الحساب. أكدي بريدك إذا كان تأكيد البريد مفعلاً، ثم سجلي الدخول." else (activity as MainActivity).showApp()}.onFailure{status.text=it.message?:"حدث خطأ"};action.isEnabled=true}}
 }
}
