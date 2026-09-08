package com.androlink.agent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
object Api {
 fun post(url:String, fields:Map<String,String>):JSONObject {
  val c=URL(url).openConnection() as HttpURLConnection
  c.requestMethod="POST"; c.connectTimeout=15000; c.readTimeout=20000; c.doOutput=true
  c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8")
  val body=fields.entries.joinToString("&"){URLEncoder.encode(it.key,"UTF-8")+"="+URLEncoder.encode(it.value,"UTF-8")}
  c.outputStream.use{it.write(body.toByteArray())}
  val text=try{c.inputStream.bufferedReader().readText()}catch(_:Exception){c.errorStream?.bufferedReader()?.readText() ?: "{\"success\":false,\"message\":\"HTTP ${c.responseCode}\"}"}
  c.disconnect(); return JSONObject(text)
 }
 fun pair(id:String,token:String):JSONObject=post(Config.PAIR_ENDPOINT,mapOf("device_id" to id,"device_token" to token))
 fun heartbeat(id:String,token:String,session:String?):JSONObject=post(Config.HEARTBEAT_ENDPOINT,buildMap{put("device_id",id);put("device_token",token);if(!session.isNullOrBlank())put("session_token",session)})
}
