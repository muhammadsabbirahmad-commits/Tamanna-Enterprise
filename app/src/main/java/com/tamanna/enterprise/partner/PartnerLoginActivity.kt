package com.tamanna.enterprise.partner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.settings.ThemeStorage

class PartnerLoginActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{TamannaTheme(ThemeStorage.getTheme(this)){
  var username by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
  Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){
   Text("Partner Login",style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.height(8.dp))
   Text("এটি Admin/Gmail লগইন থেকে সম্পূর্ণ আলাদা Partner Login।");Spacer(Modifier.height(18.dp))
   OutlinedTextField(username,{username=it;error=""},label={Text("Partner Username")},singleLine=true,modifier=Modifier.fillMaxWidth())
   Spacer(Modifier.height(10.dp));OutlinedTextField(password,{password=it;error=""},label={Text("Partner Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
   Spacer(Modifier.height(12.dp));if(error.isNotBlank()){Text(error,color=MaterialTheme.colorScheme.error);Spacer(Modifier.height(8.dp))}
   Button({val p=PartnerStorage.authenticate(this@PartnerLoginActivity,username,password);if(p==null)error="Username বা Password সঠিক নয়, অথবা Partner account নিষ্ক্রিয়।" else {PartnerStorage.login(this@PartnerLoginActivity,p.id);startActivity(Intent(this@PartnerLoginActivity,PartnerLedgerActivity::class.java).putExtra("partner_id",p.id).putExtra("partner_mode",true));finish()}},Modifier.fillMaxWidth()){Text("Partner Login")}
   Spacer(Modifier.height(10.dp));OutlinedButton({finish()},Modifier.fillMaxWidth()){Text("ফিরে যান")}
  }}
 }}}}
