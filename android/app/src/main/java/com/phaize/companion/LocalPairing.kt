package com.phaize.companion

import android.net.Uri
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class LanInvitation(val host:String,val fingerprint:String,val secret:String,val port:Int) {
 val base:String get()="https://$host:$port"
 companion object {
  fun parse(text:String):LanInvitation {
   val uri=Uri.parse(text.trim())
   require(uri.scheme=="phaize" && uri.host=="pair"){"Scan a PhAize desktop pairing QR or paste its link"}
   val host=uri.getQueryParameter("host")?:""
   val octets=host.split(".").mapNotNull{it.toIntOrNull()}
   require(octets.size==4 && octets.all{it in 0..255} && (
    octets[0]==10 || (octets[0]==172 && octets[1] in 16..31) ||
    (octets[0]==192 && octets[1]==168)
   )){"Pairing requires a private local IPv4 address"}
   val port=uri.getQueryParameter("port")?.toIntOrNull()
   require(port==8791){"Unexpected PhAize LAN port"}
   val fp=uri.getQueryParameter("fp")?.lowercase()?:""
   val secret=uri.getQueryParameter("secret")?:""
   require(fp.matches(Regex("[0-9a-f]{64}")) && secret.matches(Regex("[0-9a-f]{64}"))){"Invalid pairing certificate or invitation"}
   return LanInvitation(host,fp,secret,port)
  }
 }
}
object PinnedLanTls {
 fun apply(connection:HttpsURLConnection,host:String,expectedFingerprint:String) {
  require(expectedFingerprint.matches(Regex("[0-9a-f]{64}"))){"Valid certificate fingerprint required"}
  val manager=object:X509TrustManager {
   override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String) {
    throw CertificateException("Client certificates are not accepted")
   }
   override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String) {
    val cert=chain.firstOrNull()?:throw CertificateException("Missing certificate")
    cert.checkValidity()
    val hash=MessageDigest.getInstance("SHA-256").digest(cert.encoded)
     .joinToString(""){"%02x".format(it.toInt() and 0xff)}
    if(!MessageDigest.isEqual(hash.toByteArray(),expectedFingerprint.toByteArray()))
      throw CertificateException("PhAize desktop certificate mismatch: pairing refused")
   }
   override fun getAcceptedIssuers():Array<X509Certificate> = emptyArray()
  }
  val ssl=SSLContext.getInstance("TLS")
  ssl.init(null,arrayOf<TrustManager>(manager),null)
  connection.sslSocketFactory=ssl.socketFactory
  // Certificate identity is verified above against the QR-scanned fingerprint.
  // Restrict this hostname verifier to the exact numeric LAN address from the invite.
  connection.hostnameVerifier=javax.net.ssl.HostnameVerifier{claimed,_ -> claimed==host}
 }
}
