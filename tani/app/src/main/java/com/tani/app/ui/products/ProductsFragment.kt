package com.tani.app.ui.products

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.*
import kotlinx.coroutines.launch

class ProductsFragment: Fragment(R.layout.fragment_products) {
    private var all=emptyList<Product>()
    override fun onViewCreated(v:View,s:Bundle?) {
        val box=v.findViewById<LinearLayout>(R.id.products_box)
        lifecycleScope.launch {
            runCatching { Repository().products() }.onSuccess { all=it; render(box, all) }
                .onFailure { box.addView(TextView(requireContext()).apply { text="تعذر تحميل المنتجات\n${it.message ?: "حاولي مرة أخرى"}"; textSize=16f; setPadding(12,30,12,30) }) }
        }
    }
    private fun render(box:LinearLayout, list:List<Product>) {
        box.removeAllViews()
        if(list.isEmpty()){ box.addView(TextView(requireContext()).apply{text="لا توجد منتجات حالياً";textSize=17f;setPadding(10,30,10,30)});return }
        list.forEach { p ->
            val card=LinearLayout(requireContext()).apply{orientation=LinearLayout.VERTICAL;setPadding(18,16,18,16);background=getDrawable(R.drawable.bg_card)}
            val title=TextView(requireContext()).apply{text=p.name;textSize=19f;setTypeface(null,Typeface.BOLD);setTextColor(resources.getColor(R.color.text_dark))}
            val desc=TextView(requireContext()).apply{text=p.description;textSize=14f;setTextColor(resources.getColor(R.color.text_muted));setPadding(0,5,0,5)}
            val price=TextView(requireContext()).apply{text="${p.price.toInt()} جنيه  •  المتوفر ${p.stock}";textSize=16f;setTextColor(resources.getColor(R.color.tani_primary));setTypeface(null,Typeface.BOLD)}
            val btn=Button(requireContext()).apply{text="أضيفي للسلة";setOnClickListener{Cart.add(p);Toast.makeText(requireContext(),"تمت إضافة ${p.name} للسلة",Toast.LENGTH_SHORT).show()}}
            card.addView(title);card.addView(desc);card.addView(price);card.addView(btn)
            box.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,14)})
        }
    }
    private fun getDrawable(id:Int)=requireContext().getDrawable(id)
}
