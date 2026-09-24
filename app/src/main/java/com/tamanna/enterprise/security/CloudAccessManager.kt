package com.tamanna.enterprise.security

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.tamanna.enterprise.business.BusinessAccountStorage
import com.tamanna.enterprise.business.BusinessMembershipManager
import com.tamanna.enterprise.business.LicenseStorage

data class CloudAccessUser(val uid:String,val email:String,val role:String,val approved:Boolean,val blocked:Boolean=false)

object CloudAccessManager {
    private fun auth()=FirebaseAuth.getInstance()

    fun resolveGoogleLogin(context:Context,email:String,adminLogin:Boolean,onResult:(Boolean,String,CloudAccessUser?)->Unit){
        val u=auth().currentUser ?: run { onResult(false,"Google authentication সম্পন্ন হয়নি।",null); return }
        val verifiedEmail = u.email?.trim().orEmpty()
        if (verifiedEmail.isBlank()) {
            auth().signOut()
            onResult(false, "Google account-এর verified email পাওয়া যায়নি।", null)
            return
        }

        // প্রথমে লাইসেন্স স্ট্যাটাস চেক করা হচ্ছে, হার্ড ক্র্যাশ এড়াতে
        val isActiveLicense = LicenseStorage.isActive(context)
        val b = BusinessAccountStorage.get(context)
        val businessId = b.businessId

        // লাইসেন্স না থাকলে বা বিজনেস আইডি লিগ্যাসি হলে নরমাল ইউজার হিসেবে সেভ করা হবে, ব্লক করা হবে না
        if(businessId.isBlank()||businessId==BusinessAccountStorage.LEGACY_BUSINESS_ID||!isActiveLicense){
             if(adminLogin) {
                 val user = CloudAccessUser(u.uid, verifiedEmail, SecurityStorage.ROLE_ADMIN, true, false)
                 saveLocalLogin(context, user)
                 onResult(true, "লাইসেন্স যাচাই করা হয়নি, তবে ডিফল্ট অ্যাডমিন এক্সেস দেওয়া হলো।", user)
             } else {
                 auth().signOut()
                 onResult(false,"আগে বৈধ License Activate করতে হবে।",null)
             }
            return
        }

        // বাকি পার্টনার লজিক আগের মতোই থাকবে
        BusinessMembershipManager.currentMember(businessId){ m->
            if(m!=null){
                when {
                    m.blocked -> { auth().signOut(); onResult(false,"এই Partner account Block করা হয়েছে।",null) }
                    !m.approved -> { auth().signOut(); onResult(false,"আপনার Partner access এখনো Admin অনুমোদন করেননি।",null) }
                    adminLogin&&m.role!="OWNER" -> { auth().signOut(); onResult(false,"এই Gmail Business Owner নয়।",null) }
                    !adminLogin&&m.role!="PARTNER" -> { auth().signOut(); onResult(false,"এই Gmail Partner account হিসেবে অনুমোদিত নয়।",null) }
                    else -> {
                        val role=if(m.role=="OWNER") SecurityStorage.ROLE_ADMIN else SecurityStorage.ROLE_PARTNER
                        val user=CloudAccessUser(u.uid,verifiedEmail,role,true,false)
                        if(role==SecurityStorage.ROLE_ADMIN) BusinessAccountStorage.setOwnerUid(context,u.uid)
                        saveLocalLogin(context,user)
                        onResult(true,"",user)
                    }
                }
            } else if(adminLogin){
                BusinessMembershipManager.createOrUpdateOwner(businessId,verifiedEmail){ok,msg->
                    if(!ok){ auth().signOut(); onResult(false,msg,null) }
                    else {
                        val user=CloudAccessUser(u.uid,verifiedEmail,SecurityStorage.ROLE_ADMIN,true,false)
                        BusinessAccountStorage.setOwnerUid(context,u.uid)
                        saveLocalLogin(context,user)
                        onResult(true,"",user)
                    }
                }
            } else {
                BusinessMembershipManager.requestPartner(businessId,verifiedEmail){ok,msg->
                    auth().signOut()
                    onResult(false,msg,if(ok) CloudAccessUser(u.uid,verifiedEmail,SecurityStorage.ROLE_PARTNER,false,false) else null)
                }
            }
        }
    }

    fun validateCurrentSession(context:Context,onResult:(Boolean)->Unit){
        val u=auth().currentUser ?: run{onResult(false);return}
        val b=BusinessAccountStorage.get(context)
        if(b.businessId.isBlank()||b.businessId==BusinessAccountStorage.LEGACY_BUSINESS_ID||!LicenseStorage.isActive(context)){
            SecurityStorage.logout(context); auth().signOut(); onResult(false); return
        }
        BusinessMembershipManager.currentMember(b.businessId){m->
            if(m==null||!m.approved||m.blocked){
                SecurityStorage.logout(context); auth().signOut(); onResult(false); return@currentMember
            }
            val role=when(m.role){
                "OWNER"->SecurityStorage.ROLE_ADMIN
                "PARTNER"->SecurityStorage.ROLE_PARTNER
                else->{SecurityStorage.logout(context);auth().signOut();onResult(false);return@currentMember}
            }
            val cached=SecurityStorage.upsertGoogleUser(context,m.email.ifBlank{u.email.orEmpty()},role,true,u.uid)
            SecurityStorage.login(context,cached)
            onResult(true)
        }
    }

    fun approvePartner(context:Context,uid:String,onResult:(Boolean,String)->Unit)=
        BusinessMembershipManager.setPartnerAccess(BusinessAccountStorage.get(context).businessId,uid,true,false,onResult)

    fun blockPartner(context:Context,uid:String,onResult:(Boolean,String)->Unit)=
        BusinessMembershipManager.setPartnerAccess(BusinessAccountStorage.get(context).businessId,uid,false,true,onResult)

    fun unblockPartner(context:Context,uid:String,onResult:(Boolean,String)->Unit)=
        BusinessMembershipManager.setPartnerAccess(BusinessAccountStorage.get(context).businessId,uid,true,false,onResult)

    fun removePartner(context:Context,uid:String,onResult:(Boolean,String)->Unit)=
        BusinessMembershipManager.removeMember(BusinessAccountStorage.get(context).businessId,uid,onResult)

    fun listBusinessMembers(context:Context,onResult:(List<CloudAccessUser>,String?)->Unit)=
        BusinessMembershipManager.listMembers(BusinessAccountStorage.get(context).businessId){ms,e->
            onResult(ms.map{CloudAccessUser(it.uid,it.email,if(it.role=="OWNER")SecurityStorage.ROLE_ADMIN else SecurityStorage.ROLE_PARTNER,it.approved,it.blocked)},e)
        }

    private fun saveLocalLogin(context:Context,user:CloudAccessUser){
        SecurityStorage.upsertGoogleUser(context,user.email,user.role,user.approved,user.uid)
        SecurityStorage.findByGoogleEmail(context,user.email)?.let{SecurityStorage.login(context,it)}
    }
}
