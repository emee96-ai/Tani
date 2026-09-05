package com.tani.app.ui.cart
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.*
import kotlinx.coroutines.launch
class CartFragment:Fragment(R.layout.fragment_cart){
 private lateinit var box:LinearLayout
 override fun onViewCreated(v:View,s:Bundle?){box=v.findViewById(R.id.cart_box);render()}
 private fun render(){box.removeAllViews();val items=Cart.all();if(items.isEmpty()){box.addView(TextView(requireContext()).apply{text="السلة فارغة 🛍️\nاختاري منتجاتك من قسم المنتجات.";textSize=17f;setPadding(10,30,10,30)});return};items.forEach{item->box.addView(TextView(requireContext()).apply{text="${item.product.name}\n${item.quantity} × ${item.product.price.toInt()} = ${(item.product.price*item.quantity).toInt()} جنيه\nاضغطي هنا لتقليل الكمية";textSize=16f;setPadding(16,16,16,16);background=requireContext().getDrawable(R.drawable.bg_card);setOnClickListener{Cart.remove(item.product.id);render()}})};box.addView(TextView(requireContext()).apply{text="الإجمالي ${Cart.total().toInt()} جنيه";textSize=21f;setPadding(8,20,8,12)});box.addView(Button(requireContext()).apply{text="تأكيد الطلب";setOnClickListener{checkout()}})}
 private fun checkout(){val address=EditText(requireContext()).apply{hint="العنوان بالتفصيل"};val phone=EditText(requireContext()).apply{hint="رقم الهاتف";inputType=InputType.TYPE_CLASS_PHONE};val layout=LinearLayout(requireContext()).apply{orientation=LinearLayout.VERTICAL;setPadding(10,10,10,10);addView(address);addView(phone)};AlertDialog.Builder(requireContext()).setTitle("بيانات التوصيل").setView(layout).setPositiveButton("إرسال"){_,_->lifecycleScope.launch{runCatching{Repository().placeOrder(address.text.toString(),phone.text.toString(),Cart.all())}.onSuccess{Cart.clear();Toast.makeText(requireContext(),"تم إرسال الطلب بنجاح",Toast.LENGTH_LONG).show();render()}.onFailure{Toast.makeText(requireContext(),it.message?:"تعذر إنشاء الطلب",Toast.LENGTH_LONG).show()}}}.setNegativeButton("إلغاء",null).show()}
}
