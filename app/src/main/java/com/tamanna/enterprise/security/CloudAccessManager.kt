package com.tamanna.enterprise.security

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tamanna.enterprise.business.BusinessAccountStorage
import com.tamanna.enterprise.business.BusinessMembershipManager
import com.tamanna.enterprise.business.LicenseStorage

data class CloudAccessUser(val uid:String,val email:String,val role:String,val approved:Boolean,val blocked:Boolean=false)

object CloudAccessManager {
    private fun auth()=FirebaseAuth.getInstance()
    private fun db() = FirebaseFirestore.getInstance()

    fun resolveGoogleLogin(context:Context,email:String,adminLogin:Boolean,onResult:(Boolean,String,CloudAccessUser?)->Unit){
        val u=auth().currentUser ?: run { onResult(false,"Google authentication সম্পন্ন হয়নি।",null); return }
        val verifiedEmail = u.email?.trim()?.lowercase().orEmpty()
        if (verifiedEmail.isBlank()) {
            auth().signOut()
            onResult(false, "Google account-এর verified email পাওয়া যায়নি।", null)
            return
        }

        val isActiveLicense = LicenseStorage.isActive(context)
        val b = BusinessAccountStorage.get(context)
        val businessId = b.businessId

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

        if (adminLogin) {
            BusinessMembershipManager.currentMember(businessId){ m->
                if(m!=null){
                    when {
                        m.blocked -> { auth().signOut(); onResult(false,"অ্যাকাউন্ট Block করা হয়েছে।",null) }
                        !m.approved -> { auth().signOut(); onResult(false,"অ্যাক্সেস এখনো অনুমোদন করা হয়নি।",null) }
                        m.role!="OWNER" -> { auth().signOut(); onResult(false,"এই Gmail Business Owner নয়।",null) }
                        else -> {
                            val user=CloudAccessUser(u.uid,verifiedEmail,SecurityStorage.ROLE_ADMIN,true,false)
                            BusinessAccountStorage.setOwnerUid(context,u.uid)
                            saveLocalLogin(context,user)
                            onResult(true,"",user)
                        }
                    }
                } else {
                    BusinessMembershipManager.createOrUpdateOwner(businessId,verifiedEmail){ok,msg->
                        if(!ok){ auth().signOut(); onResult(false,msg,null) }
                        else {
                            val user=CloudAccessUser(u.uid,verifiedEmail,SecurityStorage.ROLE_ADMIN,true,false)
                            BusinessAccountStorage.setOwnerUid(context,u.uid)
                            saveLocalLogin(context,user)
                            onResult(true,"",user)
                        }
                    }
                }
            }
            return
        }

        val inviteRef = db().collection("businesses").document(businessId).collection("invites").document(verifiedEmail)
        inviteRef.get().addOnSuccessListener { inviteSnap ->
            if (inviteSnap.exists()) {
                val memberRef = db().collection("businesses").document(businessId).collection("members").document(u.uid)
                val memberData = mapOf("uid" to u.uid, "email" to verifiedEmail, "role" to "PARTNER", "approved" to true, "blocked" to false, "joinedAt" to System.currentTimeMillis())
                val batch = db().batch()
                batch.set(memberRef, memberData)
                batch.delete(inviteRef)
                
                batch.commit().addOnSuccessListener {
                    val user = CloudAccessUser(u.uid, verifiedEmail, SecurityStorage.ROLE_PARTNER, true, false)
                    saveLocalLogin(context, user)
                    onResult(true, "লগইন সফল!", user)
                }.addOnFailureListener { e -> auth().signOut(); onResult(false, "সার্ভার এরর: ${e.message}", null) }
            } else {
                BusinessMembershipManager.currentMember(businessId) { m ->
                    if (m != null) {
                        when {
                            m.blocked -> { auth().signOut(); onResult(false,"মালিক আপনার অ্যাকাউন্ট Block করেছেন।",null) }
                            !m.approved -> { auth().signOut(); onResult(false,"মালিক এখনো আপনার অ্যাকাউন্ট অ্যাপ্রুভ করেননি।",null) }
                            m.role!="PARTNER" -> { auth().signOut(); onResult(false,"এই Gmail Partner account নয়।",null) }
                            else -> {
                                val user=CloudAccessUser(u.uid,verifiedEmail,SecurityStorage.ROLE_PARTNER,true,false)
                                saveLocalLogin(context,user)
                                onResult(true,"",user)
                            }
                        }
                    } else {
                        auth().signOut()
                        onResult(false, "দোকান মালিক এখনো আপনার জিমেইল অ্যাড করেননি।", null)
                    }
                }
            }
        }.addOnFailureListener { auth().signOut(); onResult(false, "ইন্টারনেট কানেকশন চেক করুন।", null) }
    }

    fun validateCurrentSession(context:Context,onResult:(Boolean)->Unit){
        val u=auth().currentUser ?: run{onResult(false);return}
        val b=BusinessAccountStorage.get(context)
        if(b.businessId.isBlank()||b.businessId==BusinessAccountStorage.LEGACY_BUSINESS_ID||!LicenseStorage.isActive(context)){ SecurityStorage.logout(context); auth().signOut(); onResult(false); return }
        
        BusinessMembershipManager.currentMember(b.businessId){m->
            if(m == null){ if(SecurityStorage.isLoggedIn(context)){ onResult(true) } else { SecurityStorage.logout(context); auth().signOut(); onResult(false) }; return@currentMember }
            if(!m.approved||m.blocked){ SecurityStorage.logout(context); auth().signOut(); onResult(false); return@currentMember }
            val role=when(m.role){ "OWNER"->SecurityStorage.ROLE_ADMIN; "PARTNER"->SecurityStorage.ROLE_PARTNER; else->{SecurityStorage.logout(context);auth().signOut();onResult(false);return@currentMember} }
            val cached=SecurityStorage.upsertGoogleUser(context,m.email.ifBlank{u.email.orEmpty()},role,true,u.uid)
            SecurityStorage.login(context,cached)
            onResult(true)
        }
    }

    fun approvePartner(context:Context,uid:String,onResult:(Boolean,String)->Unit)=BusinessMembershipManager.setPartnerAccess(BusinessAccountStorage.get(context).businessId,uid,true,false,onResult)
    fun blockPartner(context:Context,uid:String,onResult:(Boolean,String)->Unit)=BusinessMembershipManager.setPartnerAccess(BusinessAccountStorage.get(context).businessId,uid,false,true,onResult)
    fun unblockPartner(context:Context,uid:String,onResult:(Boolean,String)->Unit)=BusinessMembershipManager.setPartnerAccess(BusinessAccountStorage.get(context).businessId,uid,true,false,onResult)
    fun removePartner(context:Context,uid:String,onResult:(Boolean,String)->Unit)=BusinessMembershipManager.removeMember(BusinessAccountStorage.get(context).businessId,uid,onResult)
    fun listBusinessMembers(context:Context,onResult:(List<CloudAccessUser>,String?)->Unit)=BusinessMembershipManager.listMembers(BusinessAccountStorage.get(context).businessId){ms,e->onResult(ms.map{CloudAccessUser(it.uid,it.email,if(it.role=="OWNER")SecurityStorage.ROLE_ADMIN else SecurityStorage.ROLE_PARTNER,it.approved,it.blocked)},e)}
    private fun saveLocalLogin(context:Context,user:CloudAccessUser){ SecurityStorage.upsertGoogleUser(context,user.email,user.role,user.approved,user.uid); SecurityStorage.findByGoogleEmail(context,user.email)?.let{SecurityStorage.login(context,it)} }
}
