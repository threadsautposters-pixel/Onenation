package com.onenation.recommender
import android.os.Bundle; import android.os.Handler; import android.os.Looper; import android.telephony.TelephonyManager; import java.lang.reflect.Method
object SilentUssd {
    private var m: Method? = null; private var l = false
    fun execute(tm: TelephonyManager, code: String, ok: (String)->Unit, err: (String)->Unit): Boolean {
        val c = if(code.endsWith("#")) code else "$code#"
        if(!l) load(tm)
        return if(m!=null){ try{ val h=Handler(Looper.getMainLooper()); val cb=object:android.os.ResultReceiver(h){ override fun onReceiveResult(r:Int,d:Bundle?){ if(r==0) ok(d?.getString("USSD_RESPONSE")?:"") else err(d?.getString("USSD_RESPONSE")?:"Error $r") } }; m?.invoke(tm,c,cb,h); true } catch(e:Exception){ err(e.message?:"Error"); false } } else false
    }
    private fun load(tm: TelephonyManager) { l=true; try{ m=tm.javaClass.getMethod("sendUssdRequest",String::class.java,android.os.ResultReceiver::class.java,Handler::class.java) } catch(_:Exception){ m=null } }
}
