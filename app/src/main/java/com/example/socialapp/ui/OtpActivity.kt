package com.example.socialapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.socialapp.api.OtpVerify
import com.example.socialapp.api.RetrofitClient
import com.example.socialapp.data.SessionManager
import com.example.socialapp.databinding.ActivityOtpBinding
import kotlinx.coroutines.launch

class OtpActivity : AppCompatActivity() {
    private lateinit var b: ActivityOtpBinding
    private lateinit var session: SessionManager
    private var phone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOtpBinding.inflate(layoutInflater)
        setContentView(b.root)
        RetrofitClient.init(this)
        session = SessionManager(this)
        phone = intent.getStringExtra("phone") ?: ""

        b.tvPhone.text = phone
        b.btnVerify.setOnClickListener { verify() }
    }

    private fun verify() {
        val otp = b.etOtp.text.toString().trim()
        if (otp.length != 6) {
            Toast.makeText(this, "کد باید ۶ رقم باشد", Toast.LENGTH_SHORT).show()
            return
        }
        b.btnVerify.isEnabled = false
        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.get().verifyOtp(OtpVerify(phone, otp))
                session.saveSession(resp.access_token, resp.user_id, resp.display_name, phone)
                startActivity(Intent(this@OtpActivity, ChatListActivity::class.java))
                finishAffinity()
            } catch (e: Exception) {
                Toast.makeText(this@OtpActivity, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                b.btnVerify.isEnabled = true
            }
        }
    }
}