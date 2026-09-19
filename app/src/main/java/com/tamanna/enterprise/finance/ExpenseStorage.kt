package com.tamanna.enterprise.finance
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
data class Expense(val id:Long,val date:String,val category:String,val amount:Double,val note:String)
data class PartnerWithdrawal(val id:Long,val date:String,val partnerId:Long,val partnerName:String,val amount:Double,val method:String,val note:String)
data class DamageRecord(val id:Long,val date:String,val productCode:String,val productName:String,val quantity:Int,val unitCost:Double,val reason:String){val totalLoss get()=quantity*unitCost}
object ExpenseStorage{
 private const val P="tamanna_enterprise_finance";private const val E="expenses";private const val W="withdrawals";private const val D="damages"
 private fun a(c:Context,k:String)=JSONArray(c.getSharedPreferences(P,0).getString(k,"[]")?:"[]")
 private fun s(c:Context,k:String,a:JSONArray)=c.getSharedPreferences(P,0).edit().putString(k,a.toString()).apply()
 fun getExpenses(c:Context)=buildList{val a=a(c,E);for(i in 0 until a.length()){val o=a.getJSONObject(i);add(Expense(o.optLong("id"),o.optString("date"),o.optString("category"),o.optDouble("amount"),o.optString("note")))}}
 fun addExpense(c:Context,x:Expense){val a=a(c,E);a.put(JSONObject().apply{put("id",x.id);put("date",x.date);put("category",x.category);put("amount",x.amount);put("note",x.note)});s(c,E,a)}
 fun getWithdrawals(c:Context)=buildList{val a=a(c,W);for(i in 0 until a.length()){val o=a.getJSONObject(i);add(PartnerWithdrawal(o.optLong("id"),o.optString("date"),o.optLong("partnerId"),o.optString("partnerName"),o.optDouble("amount"),o.optString("method"),o.optString("note")))}}
 fun addWithdrawal(c:Context,x:PartnerWithdrawal){val a=a(c,W);a.put(JSONObject().apply{put("id",x.id);put("date",x.date);put("partnerId",x.partnerId);put("partnerName",x.partnerName);put("amount",x.amount);put("method",x.method);put("note",x.note)});s(c,W,a)}
 fun getDamages(c:Context)=buildList{val a=a(c,D);for(i in 0 until a.length()){val o=a.getJSONObject(i);add(DamageRecord(o.optLong("id"),o.optString("date"),o.optString("productCode"),o.optString("productName"),o.optInt("quantity"),o.optDouble("unitCost"),o.optString("reason")))}}
 fun addDamage(c:Context,x:DamageRecord){val a=a(c,D);a.put(JSONObject().apply{put("id",x.id);put("date",x.date);put("productCode",x.productCode);put("productName",x.productName);put("quantity",x.quantity);put("unitCost",x.unitCost);put("reason",x.reason)});s(c,D,a)}
}