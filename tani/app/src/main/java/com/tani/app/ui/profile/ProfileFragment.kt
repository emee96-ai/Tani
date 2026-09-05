package com.tani.app.ui.profile
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Repository
import com.tani.app.ui.seller.SellerFragment
import kotlinx.coroutines.launch
class ProfileFragment:Fragment(R.layout.fragment_profile){
 override fun onViewCreated(v:View,s:Bundle?){v.findViewById<Button>(R.id.profile_seller).setOnClickListener{requestSeller()};v.findViewById<Button>(R.id.profile_dashboard).setOnClickListener{parentFragmentManager.beginTransaction().replace(R.id.nav_host,SellerFragment()).addToBackStack(null).commit()};v.findViewById<Button>(R.id.profile_logout).setOnClickListener{Repository().logout();(activity as MainActivity).showAuth()}}
 private fun requestSeller(){val store=EditText(requireContext()).apply{hint="اسم المتجر"};AlertDialog.Builder(requireContext()).setTitle("طلب فتح متجر").setView(store).setPositiveButton("إرسال"){_,_->lifecycleScope.launch{runCatching{Repository().becomeSeller(store.text.toString())}.onSuccess{Toast.makeText(requireContext(),"تم إرسال الطلب للمراجعة",Toast.LENGTH_SHORT).show()}.onFailure{Toast.makeText(requireContext(),it.message?:"تعذر إرسال الطلب",Toast.LENGTH_LONG).show()}}}.setNegativeButton("إلغاء",null).show()}
}
