package com.tani.app.ui.home
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.ui.products.ProductsFragment
class HomeFragment:Fragment(R.layout.fragment_home){override fun onViewCreated(v:View,s:Bundle?){v.findViewById<Button>(R.id.home_products).setOnClickListener{(activity as MainActivity).nav.selectedItemId=R.id.categories}}}
