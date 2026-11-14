package com.phaize.companion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the connection credential with Android Keystore AES-GCM. Backups disabled in manifest. */
class SecureConnectionStore(ctx:Context) {
 private val prefs=ctx.getSharedPreferences("connection",Context.MODE_PRIVATE)
 private val alias="phaize.mobile.connection.v1"
 private val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
 private fun key():SecretKey {
  val existing=ks.getKey(alias,null) as? SecretKey
  if(existing!=null)return existing
  val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  generator.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
  return generator.generateKey()
 }
 fun save(base:String,token:String){
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
  val encrypted=cipher.doFinal(token.toByteArray(Charsets.UTF_8))
  prefs.edit().putString("base",base).putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
   .putString("ciphertext",Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply()
 }
 fun load():Pair<String,String>? { return try {
  val base=prefs.getString("base",null)?:return null
  val iv=Base64.decode(prefs.getString("iv",null),Base64.NO_WRAP)
  val ciphertext=Base64.decode(prefs.getString("ciphertext",null),Base64.NO_WRAP)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv))
  base to String(cipher.doFinal(ciphertext),Charsets.UTF_8)
 }catch(_:Exception){null} }
 /** Stable installation identifier; never grants access by itself. Native pairing must bind it to a revocable credential. */
 fun deviceId():String {
  val existing=prefs.getString("device_id",null)
  if(existing!=null && Regex("[a-f0-9-]{36}").matches(existing))return existing
  val fresh=java.util.UUID.randomUUID().toString()
  prefs.edit().putString("device_id",fresh).commit()
  return fresh
 }
 fun clear(){prefs.edit().clear().commit()}
}
