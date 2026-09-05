package com.tani.app
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tani.app.data.Supabase
import com.tani.app.ui.auth.AuthFragment
import com.tani.app.ui.home.HomeFragment
import com.tani.app.ui.products.ProductsFragment
import com.tani.app.ui.cart.CartFragment
import com.tani.app.ui.orders.OrdersFragment
import com.tani.app.ui.profile.ProfileFragment
class MainActivity:AppCompatActivity(){lateinit var nav:BottomNavigationView;override fun onCreate(s:Bundle?){super.onCreate(s);Supabase.init(this);setContentView(R.layout.activity_main);nav=findViewById(R.id.bottom_nav);nav.menu.clear();nav.inflateMenu(R.menu.bottom_nav);nav.setOnItemSelectedListener{when(it.itemId){R.id.home->show(HomeFragment());R.id.categories->show(ProductsFragment());R.id.cart->show(CartFragment());R.id.orders->show(OrdersFragment());R.id.profile->show(ProfileFragment())};true};if(Supabase.token==null)showAuth()else showApp()};fun showAuth(){nav.visibility=View.GONE;show(AuthFragment())};fun showApp(){nav.visibility=View.VISIBLE;show(HomeFragment())};fun show(f:Fragment)=supportFragmentManager.beginTransaction().replace(R.id.nav_host,f).commit()}
