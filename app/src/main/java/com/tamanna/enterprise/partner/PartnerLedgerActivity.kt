package com.tamanna.enterprise.partner

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
import androidx.core.content.FileProvider
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.finance.ExpenseStorage
import com.tamanna.enterprise.finance.PartnerWithdrawal
import com.tamanna.enterprise.sales.SaleReturnStorage
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.sales.SalesTransactionStorage
import com.tamanna.enterprise.security.SecurityStorage
import com.tamanna.enterprise.settings.ThemeStorage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
class PartnerLedgerActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val requested=intent.getLongExtra("partner_id",-1L)
  val session=PartnerStorage.getCurrentPartnerId(this)
  val current=SecurityStorage.getCurrentUser(this)
  val partnerId=if(current?.role==SecurityStorage.ROLE_ADMIN) requested else session ?: -1L
  if(partnerId<=0L){finish();return}
  setContent{TamannaTheme(ThemeStorage.getTheme(this)){PartnerLedgerScreen(this,partnerId,current?.role==SecurityStorage.ROLE_ADMIN)}}
 }
}

private fun day(v:String)=v.substringBefore(" ")
private fun inRange(v:String,from:String,to:String)=day(v) in from..to

private data class ProfitResult(val revenue:Double,val cost:Double,val returns:Double){
 val profit get()=revenue-cost
}

private fun calculateProfit(context:ComponentActivity,from:String,to:String):ProfitResult{
 val valid=from.length==10&&to.length==10&&from<=to
 if(!valid)return ProfitResult(0.0,0.0,0.0)
 val sales=SalesStorage.getSales(context)
 val txs=SalesTransactionStorage.getTransactions(context).associateBy{it.transactionId}
 val returns=SaleReturnStorage.getReturns(context)
 val returnLines=SaleReturnStorage.getLines(context)
 var revenue=0.0;var cost=0.0;var returnAmount=0.0
 val groups=sales.filter{inRange(it.date,from,to)}.groupBy{it.transactionId}
 groups.forEach{(txId,lines)->
  val tx=txs[txId]
  val baseRevenue=tx?.total ?: lines.sumOf{it.quantity*it.salePrice}
  revenue+=baseRevenue
  cost+=lines.sumOf{it.quantity*it.purchasePrice}
 }
 returns.filter{inRange(it.date,from,to)}.forEach{r->returnAmount+=r.amount}
 returnLines.filter{inRange(returns.firstOrNull{x->x.returnId==it.returnId}?.date.orEmpty(),from,to)}.forEach{line->cost-=line.quantity*(sales.firstOrNull{x->x.productCode==line.productCode&&x.transactionId==line.transactionId}?.purchasePrice?:0.0)}
 revenue-=returnAmount
 return ProfitResult(revenue,max(0.0,cost),returnAmount)
}

@Composable private fun PartnerLedgerScreen(a:ComponentActivity,partnerId:Long,isAdmin:Boolean){
 val partner=PartnerStorage.getPartners(a).firstOrNull{it.id==partnerId}
 val today=remember{SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(Date())}
 var from by remember{mutableStateOf("2000-01-01")};var to by remember{mutableStateOf(today)};var refresh by remember{mutableIntStateOf(0)}
 if(partner==null){Surface(Modifier.fillMaxSize()){Text("পার্টনার পাওয়া যায়নি।",Modifier.padding(20.dp))};return}

 val sales=remember(refresh){SalesStorage.getSales(a)}
 val expenses=remember(refresh){ExpenseStorage.getExpenses(a)}
 val damages=remember(refresh){ExpenseStorage.getDamages(a)}
 val withdrawals=remember(refresh){ExpenseStorage.getWithdrawals(a)}
 val valid=from.length==10&&to.length==10&&from<=to
 val result=remember(refresh,from,to){calculateProfit(a,from,to)}
 val expenseAmount=if(valid)expenses.filter{inRange(it.date,from,to)}.sumOf{it.amount}else 0.0
 val damageAmount=if(valid)damages.filter{inRange(it.date,from,to)}.sumOf{it.totalLoss}else 0.0
 val netProfit=result.profit-expenseAmount-damageAmount
 val share=PartnerStorage.profitShare(netProfit,partner)
 val periodWithdrawals=if(valid)withdrawals.filter{it.partnerId==partner.id&&inRange(it.date,from,to)}else emptyList()
 val periodWithdrawn=periodWithdrawals.sumOf{it.amount}
 val accruedProfit=if(valid){
  val all=calculateProfit(a,"2000-01-01",to)
  val ex=expenses.filter{day(it.date)<=to}.sumOf{it.amount};val dm=damages.filter{day(it.date)<=to}.sumOf{it.totalLoss}
  PartnerStorage.profitShare(max(0.0,all.profit-ex-dm),partner)
 }else 0.0
 val lifetimeWithdrawn=withdrawals.filter{it.partnerId==partner.id&&day(it.date)<=to}.sumOf{it.amount}
 val payable=max(0.0,accruedProfit-lifetimeWithdrawn)

 Scaffold(topBar={TopAppBar(title={Text("পার্টনার লেজার — "+partner.name)},actions={if(!isAdmin){TextButton(onClick={PartnerStorage.logout(a);a.startActivity(Intent(a,PartnerLoginActivity::class.java));a.finish()}){Text("Logout")}}})}){padding->
  Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(from,{from=it},label={Text("শুরু (YYYY-MM-DD)")},singleLine=true,modifier=Modifier.weight(1f));OutlinedTextField(to,{to=it},label={Text("শেষ (YYYY-MM-DD)")},singleLine=true,modifier=Modifier.weight(1f))}
   Spacer(Modifier.height(8.dp));Button({refresh++},enabled=valid,modifier=Modifier.fillMaxWidth()){Text("হিসাব আপডেট করুন")}
   Spacer(Modifier.height(10.dp))
   Text("বিনিয়োগ: ৳ "+"%.2f".format(Locale.US,partner.investment))
   Text("লাভের অংশ: "+"%.2f".format(Locale.US,partner.percentage)+"%")
   Text("নেট বিক্রয় (রিটার্ন বাদ): ৳ "+"%.2f".format(Locale.US,result.revenue))
   Text("পণ্য ক্রয়মূল্য: ৳ "+"%.2f".format(Locale.US,result.cost))
   Text("প্রতিষ্ঠানের খরচ: ৳ "+"%.2f".format(Locale.US,expenseAmount))
   Text("ড্যামেজ ক্ষতি: ৳ "+"%.2f".format(Locale.US,damageAmount))
   Text("নিট লাভ: ৳ "+"%.2f".format(Locale.US,netProfit))
   Text("এই সময়ের Partner লাভ: ৳ "+"%.2f".format(Locale.US,share))
   Text("এই সময়ের উত্তোলন: ৳ "+"%.2f".format(Locale.US,periodWithdrawn))
   Divider(Modifier.padding(vertical=8.dp))
   Text("আজ পর্যন্ত মোট অর্জিত Partner লাভ: ৳ "+"%.2f".format(Locale.US,accruedProfit))
   Text("আজ পর্যন্ত মোট উত্তোলন: ৳ "+"%.2f".format(Locale.US,lifetimeWithdrawn))
   Text("বর্তমান পাওনা: ৳ "+"%.2f".format(Locale.US,payable),style=MaterialTheme.typography.titleMedium)
   Spacer(Modifier.height(10.dp))
   Button({shareStatement(a,partner,from,to,netProfit,share,periodWithdrawn,accruedProfit,lifetimeWithdrawn,payable,periodWithdrawals)},enabled=valid,modifier=Modifier.fillMaxWidth()){Text("স্টেটমেন্ট PDF শেয়ার করুন")}
   Spacer(Modifier.height(10.dp));Text("উত্তোলনের ইতিহাস",style=MaterialTheme.typography.titleLarge)
   LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){items(periodWithdrawals){w->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(w.date+" — ৳ "+"%.2f".format(Locale.US,w.amount)+" — "+w.method);if(w.note.isNotBlank())Text(w.note)}}}}
  }
 }
}

private fun shareStatement(context:ComponentActivity,partner:Partner,from:String,to:String,netProfit:Double,share:Double,periodWithdrawn:Double,accruedProfit:Double,lifetimeWithdrawn:Double,payable:Double,withdrawals:List<PartnerWithdrawal>){
 val pdf=PdfDocument();val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=12f};val title=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=20f;isFakeBoldText=true}
 val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,1).create());val canvas=page.canvas;var y=48f
 fun line(s:String){canvas.drawText(s.take(90),32f,y,paint);y+=20f}
 canvas.drawText("Tamanna Enterprise - Partner Statement",32f,y,title);y+=30f
 line("Partner: "+partner.name);line("Period: "+from+" to "+to);line("Investment: ৳ "+"%.2f".format(Locale.US,partner.investment));line("Profit share: "+"%.2f".format(Locale.US,partner.percentage)+"%")
 line("Net profit: ৳ "+"%.2f".format(Locale.US,netProfit));line("Period partner share: ৳ "+"%.2f".format(Locale.US,share));line("Period withdrawals: ৳ "+"%.2f".format(Locale.US,periodWithdrawn))
 line("Accrued partner profit to "+to+": ৳ "+"%.2f".format(Locale.US,accruedProfit));line("All withdrawals to "+to+": ৳ "+"%.2f".format(Locale.US,lifetimeWithdrawn));line("Current receivable: ৳ "+"%.2f".format(Locale.US,payable))
 y+=12f;line("WITHDRAWAL HISTORY");withdrawals.forEach{line(it.date+" | ৳ "+"%.2f".format(Locale.US,it.amount)+" | "+it.method)}
 pdf.finishPage(page)
 val dir=File(context.cacheDir,"partner_statements").apply{mkdirs()};val file=File(dir,"partner_"+partner.id+"_"+from+"_to_"+to+".pdf")
 FileOutputStream(file).use{pdf.writeTo(it)};pdf.close()
 val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",file)
 context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"পার্টনার স্টেটমেন্ট শেয়ার করুন"))
}