package com.tani.app.ui.seller
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.Repository
import kotlinx.coroutines.launch
class SellerFragment:Fragment(R.layout.fragment_seller){
 private lateinit var box:LinearLayout
 override fun onViewCreated(v:View,s:Bundle?){box=v.findViewById(R.id.seller_box);loadSeller()}
 private fun loadSeller(){lifecycleScope.launch{runCatching{Repository().seller()}.onSuccess{seller->if(box.childCount>1)box.removeViews(1,box.childCount-1);if(seller==null){box.addView(TextView(requireContext()).apply{text="لا يوجد متجر"});return@onSuccess};box.addView(TextView(requireContext()).apply{text="${seller.store_name}\nالحالة: ${seller.verification_status}\nالتوصيل: ${seller.delivery_fee.toInt()} جنيه";textSize=20f;setPadding(0,16,0,16)});if(seller.verification_status=="approved"){box.addView(Button(requireContext()).apply{text="إضافة منتج";setOnClickListener{addProduct(seller.id)}});loadProducts(seller.id)}}.onFailure{box.addView(TextView(requireContext()).apply{text=it.message?:"تعذر تحميل المتجر"})}}}
 private fun addProduct(id:String){val l=LinearLayout(requireContext()).apply{orientation=LinearLayout.VERTICAL};val n=EditText(requireContext()).apply{hint="اسم المنتج"};val d=EditText(requireContext()).apply{hint="الوصف"};val p=EditText(requireContext()).apply{hint="السعر";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL};val st=EditText(requireContext()).apply{hint="المخزون";inputType=InputType.TYPE_CLASS_NUMBER};l.addView(n);l.addView(d);l.addView(p);l.addView(st);AlertDialog.Builder(requireContext()).setTitle("إضافة منتج").setView(l).setPositiveButton("حفظ"){_,_->lifecycleScope.launch{runCatching{Repository().addProduct(id,n.text.toString(),d.text.toString(),p.text.toString().toDouble(),st.text.toString().toInt())}.onSuccess{loadSeller()}.onFailure{Toast.makeText(requireContext(),it.message?:"خطأ",Toast.LENGTH_LONG).show()}}}.setNegativeButton("إلغاء",null).show()}
 private fun loadProducts(id:String){lifecycleScope.launch{runCatching{Repository().sellerProducts(id)}.onSuccess{products->products.forEach{p->box.addView(TextView(requireContext()).apply{text="${p.name} — ${p.price.toInt()} جنيه — مخزون ${p.stock}";setPadding(5,12,5,12)})}}}}
}
