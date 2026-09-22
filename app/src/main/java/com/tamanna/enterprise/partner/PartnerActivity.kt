package com.tamanna.enterprise.partner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.security.ActivityLogStorage
import com.tamanna.enterprise.security.SecurityStorage
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

// Step 6 Phase 1 build-safe investment UI
fun todayPartner()=SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(Date())

class PartnerActivity: ComponentActivity() {
 override fun onCreate(b: Bundle?) { super.onCreate(b); if (!SecurityStorage.canManage(this)) { finish(); return }; setContent { PartnerScreen(this) } }
}

@Composable private fun PartnerScreen(a: ComponentActivity) {
 var partners by remember { mutableStateOf(PartnerStorage.getPartners(a)) }
 var name by remember { mutableStateOf("") }; var investment by remember { mutableStateOf("") }; var percentage by remember { mutableStateOf("") }
 var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }
 var selected by remember { mutableStateOf<Partner?>(null) }; var addInvestmentAmount by remember { mutableStateOf("") }; var addInvestmentDate by remember { mutableStateOf(todayPartner()) }; var addInvestmentNote by remember { mutableStateOf("") }; var deletePin by remember { mutableStateOf("") }; var editPassword by remember { mutableStateOf("") }
 val totalInvestment=partners.sumOf{it.investment}; val totalPercentage=partners.sumOf{it.percentage}

 MaterialTheme { Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().padding(16.dp)) {
  Text("পার্টনার ব্যবস্থাপনা",style=MaterialTheme.typography.headlineMedium)
  Text("প্রত্যেক Partner-এর বিনিয়োগ, লাভের শতাংশ এবং আলাদা Login account এখানে থাকবে।")
  Spacer(Modifier.height(12.dp))
  OutlinedTextField(name,{name=it},label={Text("পার্টনারের নাম")},modifier=Modifier.fillMaxWidth(),singleLine=true)
  OutlinedTextField(investment,{investment=it},label={Text("বিনিয়োগ (টাকা)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
  OutlinedTextField(percentage,{percentage=it},label={Text("লাভের অংশ (%)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
  OutlinedTextField(username,{username=it},label={Text("Partner Username")},modifier=Modifier.fillMaxWidth(),singleLine=true)
  OutlinedTextField(password,{password=it},label={Text("Partner Password (কমপক্ষে ৪ অক্ষর)")},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=PasswordVisualTransformation())
  Spacer(Modifier.height(8.dp))
  Button({
   val inv=investment.toDoubleOrNull(); val pct=percentage.toDoubleOrNull()
   when {
    name.isBlank()||inv==null||pct==null||inv<0||pct<0||pct>100 -> message="সঠিক নাম, বিনিয়োগ ও ০–১০০% শতাংশ দিন।"
    totalPercentage+pct>100.000001 -> message="সব Partner-এর লাভের শতাংশ মিলিয়ে ১০০%-এর বেশি হতে পারবে না।"
    username.isBlank()||password.length<4 -> message="Partner Username দিন এবং Password কমপক্ষে ৪ অক্ষরের করুন।"
    !PartnerStorage.addPartner(a,name,inv,pct,username,password) -> message="Partner যোগ করা যায়নি। Username আগে থেকেই থাকতে পারে।"
    else -> { val created=PartnerStorage.getPartners(a).lastOrNull(); if(created!=null && inv!=null && inv>0){ PartnerInvestmentStorage.addInvestment(a,PartnerInvestment(created.id,created.id,created.name,inv,todayPartner(),"INV-${created.id}-${created.id}", "প্রাথমিক বিনিয়োগ")) }; partners=PartnerStorage.getPartners(a); name=""; investment=""; percentage=""; username=""; password=""; message="Partner সফলভাবে যোগ হয়েছে।" }
   }
  },Modifier.fillMaxWidth()){Text("Partner যোগ করুন")}
  Spacer(Modifier.height(8.dp))
  Text("মোট বিনিয়োগ: ৳ " + "%.2f".format(Locale.US,totalInvestment))
  Text("মোট লাভের অংশ: " + "%.2f".format(Locale.US,totalPercentage) + "%")
  Text(if(totalPercentage<=100.000001) "অবশিষ্ট লাভের অংশ: " + "%.2f".format(Locale.US,100-totalPercentage) + "%" else "লাভের শতাংশ ১০০%-এর বেশি হয়েছে.",color=if(totalPercentage<=100.000001)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
  if(message.isNotBlank()) Text(message)
  Spacer(Modifier.height(10.dp))
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
   items(partners,key={it.id}) { p -> Card(Modifier.fillMaxWidth().clickable{selected=p}) {
    Column(Modifier.padding(12.dp)) { Text(p.name,style=MaterialTheme.typography.titleMedium)
     Text("বিনিয়োগ: ৳ " + "%.2f".format(Locale.US,p.investment) + " • লাভ: " + "%.2f".format(Locale.US,p.percentage) + "%")
     Text("Login: " + p.username.ifBlank{"সেট করা নেই"})
    }
   }}
  }
 }}}
 selected?.let { p ->
  var en by remember(p.id){mutableStateOf(p.name)}; var ei by remember(p.id){mutableStateOf(p.investment.toString())}; var ep by remember(p.id){mutableStateOf(p.percentage.toString())}; var eu by remember(p.id){mutableStateOf(p.username)}; var active by remember(p.id){mutableStateOf(p.active)}
  AlertDialog(onDismissRequest={selected=null},title={Text(p.name)},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
   OutlinedTextField(en,{en=it},label={Text("নাম")},singleLine=true)
   OutlinedTextField(ei,{ei=it},label={Text("বিনিয়োগ")},singleLine=true)
   OutlinedTextField(ep,{ep=it},label={Text("লাভের শতাংশ")},singleLine=true)
   OutlinedTextField(eu,{eu=it},label={Text("Username")},singleLine=true)
   OutlinedTextField(editPassword,{editPassword=it},label={Text("নতুন Password (ফাঁকা রাখলে অপরিবর্তিত)")},singleLine=true,visualTransformation=PasswordVisualTransformation())
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Partner Login সক্রিয়");Switch(active,{active=it})}
   OutlinedTextField(addInvestmentAmount,{addInvestmentAmount=it},label={Text("নতুন বিনিয়োগ যোগ (টাকা)")},singleLine=true)
   OutlinedTextField(addInvestmentDate,{addInvestmentDate=it},label={Text("বিনিয়োগের তারিখ (YYYY-MM-DD)")},singleLine=true)
   OutlinedTextField(addInvestmentNote,{addInvestmentNote=it},label={Text("বিনিয়োগের বিবরণ")},singleLine=true)
   OutlinedTextField(deletePin,{deletePin=it},label={Text("Delete করতে Admin PIN")},singleLine=true,visualTransformation=PasswordVisualTransformation())
  }},
  confirmButton={Button({
   val inv=ei.toDoubleOrNull(); val pct=ep.toDoubleOrNull(); val others=partners.filter{it.id!=p.id}.sumOf{it.percentage}
   if(inv==null||pct==null||pct<0||pct>100||en.isBlank()||eu.isBlank()||editPassword.isNotBlank()&&editPassword.length<4){message="সঠিক তথ্য দিন।";return@Button}
   if(others+pct>100.000001){message="সব Partner-এর লাভের অংশ ১০০%-এর বেশি হতে পারবে না।";return@Button}
   if(PartnerStorage.updatePartner(a,p.id,en,inv,pct,eu,editPassword.ifBlank{null},active)){partners=PartnerStorage.getPartners(a);message="Partner তথ্য আপডেট হয়েছে।";editPassword="";deletePin="";selected=null}else message="আপডেট ব্যর্থ হয়েছে।"
  }){Text("সংরক্ষণ")}},
  dismissButton={Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
   Button({ val amount=addInvestmentAmount.toDoubleOrNull(); if(amount==null||amount<=0||addInvestmentDate.length!=10){message="নতুন বিনিয়োগের টাকা ও সঠিক তারিখ দিন।"} else { val tx="INV-${p.id}-${System.currentTimeMillis()}"; if(PartnerInvestmentStorage.addInvestment(a,PartnerInvestment(System.currentTimeMillis(),p.id,p.name,amount,addInvestmentDate,tx,addInvestmentNote)) && PartnerStorage.addInvestment(a,p.id,amount)){partners=PartnerStorage.getPartners(a);message="নতুন বিনিয়োগ সংরক্ষণ হয়েছে।";addInvestmentAmount="";addInvestmentNote=""} else message="বিনিয়োগ সংরক্ষণ ব্যর্থ হয়েছে।" }}){Text("বিনিয়োগ যোগ")}
   Button({a.startActivity(Intent(a,PartnerLedgerActivity::class.java).putExtra("partner_id",p.id));selected=null}){Text("লেজার")}
   Button({if(deletePin.isNotBlank()&&SecurityStorage.getUsers(a).firstOrNull{it.role==SecurityStorage.ROLE_ADMIN}?.passwordHash==SecurityStorage.hashPassword(deletePin)){PartnerStorage.deletePartner(a,p.id);ActivityLogStorage.add(a,"পার্টনার মুছে ফেলা",p.name);partners=PartnerStorage.getPartners(a);selected=null;deletePin=""}else message="ভুল Admin PIN।"}){Text("মুছে ফেলুন")}
  }} )
 }
}