package com.onenation.recommender
import android.content.Context; import android.content.SharedPreferences; import org.json.JSONArray; import org.json.JSONObject; import java.text.SimpleDateFormat; import java.util.*
data class SavedNumber(val id: Long = System.currentTimeMillis(), val phone: String, val dateAdded: String, val timeAdded: String, var status: String, val source: String, var lastAttempted: String = "", var retryCount: Int = 0, var nextRetryTime: String = "")
object ContactManager {
    private const val P = "onenation_data"
    fun saveNumber(c: Context, n: SavedNumber) { if(isInstalled(c,n.phone)) return; val a=getPending(c).toMutableList(); if(a.any{it.phone==n.phone}) return; a.add(n); saveP(c,a); incTotal(c) }
    fun moveToInstalled(c: Context, p: String) { val a=getPending(c).toMutableList(); val f=a.find{it.phone==p}; a.removeAll{it.phone==p}; saveP(c,a); if(f!=null){ val i=getInstalled(c).toMutableList(); if(i.none{it.phone==p}){ i.add(f.copy(status=if(f.source.startsWith("IMPORTED"))"IMPORTED_INSTALLED" else "INSTALLED",lastAttempted=fmt())); saveI(c,i); incSuccess(c) } } }
    fun updateLastAttempted(c: Context, p: String) { val a=getPending(c).toMutableList(); val idx=a.indexOfFirst{it.phone==p}; if(idx>=0){ val cal=Calendar.getInstance(); cal.add(Calendar.HOUR_OF_DAY,23); a[idx]=a[idx].copy(lastAttempted=fmt(),retryCount=a[idx].retryCount+1,nextRetryTime=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(cal.time)); saveP(c,a) } }
    fun getDueForRetry(c: Context) = getPending(c).filter{it.nextRetryTime.isNotBlank()&&it.nextRetryTime<=fmt()}
    fun getPending(c: Context) = parse(c.getSharedPreferences(P,Context.MODE_PRIVATE),"pending")
    fun getInstalled(c: Context) = parse(c.getSharedPreferences(P,Context.MODE_PRIVATE),"installed")
    fun getPendingCount(c: Context) = getPending(c).size
    fun getInstalledCount(c: Context) = getInstalled(c).size
    fun getGeneratedCount(c: Context) = c.getSharedPreferences(P,Context.MODE_PRIVATE).getLong("gen",0L)
    fun incrementGeneratedCount(c: Context) { val p=c.getSharedPreferences(P,Context.MODE_PRIVATE); p.edit().putLong("gen",getGeneratedCount(c)+1).apply() }
    fun getLifetimeTotal(c: Context) = c.getSharedPreferences(P,Context.MODE_PRIVATE).getLong("total",0L)
    fun getLifetimeSuccess(c: Context) = c.getSharedPreferences(P,Context.MODE_PRIVATE).getLong("success",0L)
    fun getAutoDeleteSetting(c: Context) = c.getSharedPreferences(P,Context.MODE_PRIVATE).getString("autodel","never")?:"never"
    fun setAutoDeleteSetting(c: Context, s: String) { c.getSharedPreferences(P,Context.MODE_PRIVATE).edit().putString("autodel",s).apply() }
    fun deleteInstalledNumbers(c: Context) { saveI(c,emptyList()) }
    fun deleteNumber(c: Context, p: String) { saveP(c,getPending(c).filter{it.phone!=p}); saveI(c,getInstalled(c).filter{it.phone!=p}) }
    fun isInstalled(c: Context, p: String) = getInstalled(c).any{it.phone==p}
    fun isPending(c: Context, p: String) = getPending(c).any{it.phone==p}
    private fun incTotal(c: Context) { val p=c.getSharedPreferences(P,Context.MODE_PRIVATE); p.edit().putLong("total",getLifetimeTotal(c)+1).apply() }
    private fun incSuccess(c: Context) { val p=c.getSharedPreferences(P,Context.MODE_PRIVATE); p.edit().putLong("success",getLifetimeSuccess(c)+1).apply() }
    private fun fmt() = SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(Date())
    private fun parse(p: SharedPreferences, k: String): List<SavedNumber> { try{ val a=JSONArray(p.getString(k,"[]")?:"[]"); return (0 until a.length()).map{val o=a.getJSONObject(it); SavedNumber(o.optLong("id"),o.getString("phone"),o.getString("dateAdded"),o.getString("timeAdded"),o.getString("status"),o.getString("source"),o.optString("last"),o.optInt("retry"),o.optString("next"))} } catch(e:Exception){ return emptyList() } }
    private fun saveP(c: Context, l: List<SavedNumber>) { save(c,"pending",l) }
    private fun saveI(c: Context, l: List<SavedNumber>) { save(c,"installed",l) }
    private fun save(c: Context, k: String, l: List<SavedNumber>) { val a=JSONArray(); l.forEach{a.put(JSONObject().apply{put("id",it.id);put("phone",it.phone);put("dateAdded",it.dateAdded);put("timeAdded",it.timeAdded);put("status",it.status);put("source",it.source);put("last",it.lastAttempted);put("retry",it.retryCount);put("next",it.nextRetryTime)})}; c.getSharedPreferences(P,Context.MODE_PRIVATE).edit().putString(k,a.toString()).apply() }
}
