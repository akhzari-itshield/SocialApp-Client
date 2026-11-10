package com.example.socialapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.socialapp.api.PhoneRequest
import com.example.socialapp.api.RetrofitClient
import com.example.socialapp.data.SessionManager
import com.example.socialapp.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var b: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RetrofitClient.init(this)
        session = SessionManager(this)

        if (session.isLoggedIn) {
            startActivity(Intent(this, ChatListActivity::class.java))
            finish()
            return
        }

        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnSendOtp.setOnClickListener {
            val phone = b.etPhone.text.toString().trim()
            if (phone.length < 10) {
                Toast.makeText(this, "شماره تلفن معتبر نیست", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            requestOtp(phone)
        }
    }

    private fun requestOtp(phone: String) {
        b.btnSendOtp.isEnabled = false
        lifecycleScope.launch {
            try {
                RetrofitClient.get().requestOtp(PhoneRequest(phone))
                val i = Intent(this@LoginActivity, OtpActivity::class.java)
                i.putExtra("phone", phone)
                startActivity(i)
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                b.btnSendOtp.isEnabled = true
            }
        }
    }
}