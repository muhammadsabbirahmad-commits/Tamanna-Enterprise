package com.tamanna.enterprise.partner

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONArray
import org.json.JSONObject

data class Partner(
    val id: Long,
    val name: String,
    val investment: Double,
    val percentage: Double,
    val username: String = "",
    val passwordHash: String = "",
    val active: Boolean = true
)

object PartnerStorage {
    private const val PREFS = "tamanna_enterprise_partners"
    private const val KEY_PARTNERS = "partners"
    private const val KEY_SESSION = "current_partner_id"

    private fun prefs(context: Context) = BusinessStorage.prefs(context, PREFS)

    fun getPartners(context: Context): List<Partner> {
        val raw = prefs(context).getString(KEY_PARTNERS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(Partner(o.optLong("id"), o.optString("name"), o.optDouble("investment", 0.0), o.optDouble("percentage", 0.0), o.optString("username", ""), o.optString("passwordHash", ""), o.optBoolean("active", true)))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun savePartners(context: Context, partners: List<Partner>) {
        val array = JSONArray()
        partners.forEach { p ->
            array.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("investment", p.investment)
                put("percentage", p.percentage); put("username", p.username)
                put("passwordHash", p.passwordHash); put("active", p.active)
            })
        }
        prefs(context).edit().putString(KEY_PARTNERS, array.toString()).apply()
    }

    fun addPartner(context: Context, name: String, investment: Double, percentage: Double, username: String, password: String): Boolean {
        val n=name.trim(); val u=username.trim()
        if(n.isBlank() || investment<0 || percentage<0 || percentage>100 || u.isBlank() || password.length<4) return false
        val list=getPartners(context).toMutableList()
        if(list.any{it.username.equals(u,true)}) return false
        list.add(Partner(System.currentTimeMillis(),n,investment,percentage,u,com.tamanna.enterprise.security.SecurityStorage.hashPassword(password),true))
        savePartners(context,list); return true
    }

    fun updatePartner(context: Context, id: Long, name: String, investment: Double, percentage: Double, username: String, newPassword: String?=null, active: Boolean=true): Boolean {
        val n=name.trim(); val u=username.trim()
        if(n.isBlank() || investment<0 || percentage<0 || percentage>100 || u.isBlank()) return false
        val list=getPartners(context).toMutableList()
        val i=list.indexOfFirst{it.id==id}; if(i<0 || list.any{it.id!=id && it.username.equals(u,true)}) return false
        val old=list[i]
        val hash=if(!newPassword.isNullOrBlank()){if(newPassword.length<4)return false;com.tamanna.enterprise.security.SecurityStorage.hashPassword(newPassword)}else old.passwordHash
        list[i]=old.copy(name=n,investment=investment,percentage=percentage,username=u,passwordHash=hash,active=active)
        savePartners(context,list); return true
    }

    fun deletePartner(context: Context,id:Long){if(getCurrentPartnerId(context)==id)logout(context);savePartners(context,getPartners(context).filterNot{it.id==id})}
    fun totalInvestment(context: Context)=getPartners(context).sumOf{it.investment}
    fun totalPercentage(context: Context)=getPartners(context).sumOf{it.percentage}
    fun profitShare(profit:Double,partner:Partner)=maxOf(0.0,profit)*partner.percentage/100.0

    fun authenticate(context: Context, username:String, password:String):Partner?{
        val hash=com.tamanna.enterprise.security.SecurityStorage.hashPassword(password)
        return getPartners(context).firstOrNull{it.active && it.username.equals(username.trim(),true) && it.passwordHash.isNotBlank() && it.passwordHash==hash}
    }
    fun login(context:Context,partnerId:Long){prefs(context).edit().putLong(KEY_SESSION,partnerId).apply()}
    fun logout(context:Context){prefs(context).edit().remove(KEY_SESSION).apply()}
    fun getCurrentPartnerId(context:Context):Long?=prefs(context).getLong(KEY_SESSION,-1L).takeIf{it>0}
    fun getCurrentPartner(context:Context):Partner?=getCurrentPartnerId(context)?.let{id->getPartners(context).firstOrNull{it.id==id&&it.active}}
}
