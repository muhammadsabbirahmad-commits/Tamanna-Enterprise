package com.tamanna.enterprise.finance
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.partner.Partner
import com.tamanna.enterprise.partner.PartnerStorage
import com.tamanna.enterprise.product.ProductStorage
import java.text.SimpleDateFormat
import java.util.*
class FinanceActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{FinanceScreen(this)}}}
fun todayFinance()=SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(Date())
@Composable fun FinanceScreen(a:ComponentActivity){
 var tab by remember{mutableIntStateOf(0)}
 Column(Modifier.fillMaxSize().padding(16.dp)){Text("খরচ, পার্টনার উত্তোলন ও ড্যামেজ",style=MaterialTheme.typography.headlineSmall);Row{Button({tab=0},Modifier.weight(1f)){Text("প্রতিষ্ঠানের খরচ")};Button({tab=1},Modifier.weight(1f)){Text("পার্টনার উত্তোলন")};Button({tab=2},Modifier.weight(1f)){Text("ড্যামেজ মাল")}};Spacer(Modifier.height(8.dp));when(tab){0->ExpensePane(a);1->WithdrawalPane(a);2->DamagePane(a)}}}
@Composable fun ExpensePane(a:ComponentActivity){
 var cat by remember{mutableStateOf("")};var amt by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var date by remember{mutableStateOf(todayFinance())};var list by remember{mutableStateOf(ExpenseStorage.getExpenses(a))}
 Column{Text("প্রতিষ্ঠানের দৈনন্দিন/মাসিক খরচ");OutlinedTextField(date,{date=it},label={Text("তারিখ")},modifier=Modifier.fillMaxWidth());OutlinedTextField(cat,{cat=it},label={Text("খরচের ধরন")},modifier=Modifier.fillMaxWidth());OutlinedTextField(amt,{amt=it},label={Text("টাকা")},modifier=Modifier.fillMaxWidth());OutlinedTextField(note,{note=it},label={Text("বিবরণ")},modifier=Modifier.fillMaxWidth());Button({val x=amt.toDoubleOrNull();if(cat.isNotBlank()&&x!=null&&x>0){ExpenseStorage.addExpense(a,Expense(System.currentTimeMillis(),date,cat,x,note));list=ExpenseStorage.getExpenses(a);amt="";note=""}},Modifier.fillMaxWidth()){Text("খরচ যোগ করুন")};Text("মোট: ৳ %.2f".format(Locale.US,list.sumOf{it.amount}));LazyColumn{items(list.take(50)){Text(it.date+" — "+it.category+": ৳ "+String.format(Locale.US,"%.2f",it.amount),Modifier.padding(5.dp))}}}}
@Composable fun WithdrawalPane(a:ComponentActivity){
 val ps=remember{PartnerStorage.getPartners(a)};var p by remember{mutableStateOf<Partner?>(ps.firstOrNull())};var amt by remember{mutableStateOf("")};var method by remember{mutableStateOf("নগদ")};var note by remember{mutableStateOf("")};var date by remember{mutableStateOf(todayFinance())};var list by remember{mutableStateOf(ExpenseStorage.getWithdrawals(a))};var open by remember{mutableStateOf(false)}
 Column{if(ps.isEmpty())Text("আগে পার্টনার যোগ করুন।")else{Box{Button({open=true},Modifier.fillMaxWidth()){Text("পার্টনার নির্বাচন")};DropdownMenu(open,{open=false}){ps.forEach{x->DropdownMenuItem(text={Text(x.name)},onClick={p=x;open=false})}}};Text("নির্বাচিত: "+(p?.name?:""));OutlinedTextField(date,{date=it},label={Text("তারিখ")},modifier=Modifier.fillMaxWidth());OutlinedTextField(amt,{amt=it},label={Text("উত্তোলনের টাকা")},modifier=Modifier.fillMaxWidth());OutlinedTextField(method,{method=it},label={Text("পদ্ধতি: নগদ / bKash / ব্যাংক")},modifier=Modifier.fillMaxWidth());OutlinedTextField(note,{note=it},label={Text("বিবরণ")},modifier=Modifier.fillMaxWidth());Button({val x=amt.toDoubleOrNull();val q=p;if(q!=null&&x!=null&&x>0){ExpenseStorage.addWithdrawal(a,PartnerWithdrawal(System.currentTimeMillis(),date,q.id,q.name,x,method,note));list=ExpenseStorage.getWithdrawals(a);amt="";note=""}},Modifier.fillMaxWidth()){Text("উত্তোলন সংরক্ষণ")};Text("মোট উত্তোলন: ৳ %.2f".format(Locale.US,list.sumOf{it.amount}));LazyColumn{items(list.take(50)){Text(it.date+" — "+it.partnerName+": ৳ "+String.format(Locale.US,"%.2f",it.amount)+" ("+it.method+")",Modifier.padding(5.dp))}}}}}
@Composable fun DamagePane(a:ComponentActivity){
 val products=remember{ProductStorage.getProducts(a)};var code by remember{mutableStateOf("")};var qty by remember{mutableStateOf("")};var reason by remember{mutableStateOf("")};var date by remember{mutableStateOf(todayFinance())};var list by remember{mutableStateOf(ExpenseStorage.getDamages(a))};var msg by remember{mutableStateOf("")}
 Column{Text("ড্যামেজ/নষ্ট/ভাঙা পণ্য");OutlinedTextField(date,{date=it},label={Text("তারিখ")},modifier=Modifier.fillMaxWidth());OutlinedTextField(code,{code=it},label={Text("পণ্যের কোড")},modifier=Modifier.fillMaxWidth());OutlinedTextField(qty,{qty=it},label={Text("পরিমাণ")},modifier=Modifier.fillMaxWidth());OutlinedTextField(reason,{reason=it},label={Text("কারণ")},modifier=Modifier.fillMaxWidth());Button({val p=products.firstOrNull{it.code.equals(code.trim(),true)};val q=qty.toIntOrNull();when{p==null->msg="পণ্যের কোড মেলেনি।";q==null||q<=0->msg="সঠিক পরিমাণ দিন।";q>p.stockQuantity->msg="স্টকে এত পরিমাণ নেই।";else->{ExpenseStorage.addDamage(a,DamageRecord(System.currentTimeMillis(),date,p.code,p.name,q,p.purchasePrice,reason));ProductStorage.updateStock(a,p.code,-q);list=ExpenseStorage.getDamages(a);msg="ড্যামেজ যোগ হয়েছে এবং স্টক কমেছে।";code="";qty="";reason=""}}},Modifier.fillMaxWidth()){Text("ড্যামেজ সংরক্ষণ")};if(msg.isNotBlank())Text(msg);Text("মোট ক্ষতি: ৳ %.2f".format(Locale.US,list.sumOf{it.totalLoss}));LazyColumn{items(list.take(50)){Text(it.date+" — "+it.productName+" × "+it.quantity+": ৳ "+String.format(Locale.US,"%.2f",it.totalLoss),Modifier.padding(5.dp))}}}}
