package com.tani.app.data
object Cart { private val map=linkedMapOf<String,CartItem>(); fun all()=map.values.toList(); fun add(p:Product){val x=map[p.id];map[p.id]=CartItem(p,(x?.quantity?:0)+1)};fun remove(id:String){val x=map[id]?:return;if(x.quantity<=1)map.remove(id)else map[id]=x.copy(quantity=x.quantity-1)};fun clear()=map.clear();fun total()=map.values.sumOf{it.product.price*it.quantity} }
