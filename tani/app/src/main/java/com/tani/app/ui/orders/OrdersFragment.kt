package com.tani.app.ui.orders
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.Repository
import kotlinx.coroutines.launch
class OrdersFragment:Fragment(R.layout.fragment_orders){override fun onViewCreated(v:View,s:Bundle?){val box=v.findViewById<LinearLayout>(R.id.orders_box);lifecycleScope.launch{runCatching{Repository().orders()}.onSuccess{os->box.removeAllViews();os.forEach{o->box.addView(TextView(requireContext()).apply{text="طلب ${o.id.take(8)}\n${o.total} جنيه — ${o.status}\n${o.address}";textSize=17f;setPadding(5,15,5,15)})}}.onFailure{box.addView(TextView(requireContext()).apply{text="تعذر تحميل الطلبات"})}}}}
