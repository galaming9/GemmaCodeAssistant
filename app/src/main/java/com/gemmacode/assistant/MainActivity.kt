package com.gemmacode.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gemmacode.assistant.databinding.ActivityMainBinding
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var gemmaEngine: GemmaEngine
    private lateinit var chatAdapter: ChatAdapter
    private var isGenerating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUI()
        requestStoragePermission()
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, 100)
            } else {
                initModel()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
            } else {
                initModel()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) initModel()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 101) initModel()
    }

    private fun setupUI() {
        val markwon = Markwon.create(this)
        chatAdapter = ChatAdapter(markwon)
        binding.rvMessages.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
        }
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
    }

    private fun initModel() {
        binding.tvStatus.text = "⏳ Loading..."
        binding.btnSend.isEnabled = false
        lifecycleScope.launch {
            gemmaEngine = GemmaEngine(applicationContext)
            gemmaEngine.initialize().fold(
                onSuccess = {
                    binding.tvStatus.text = "✅ Ready"
                    binding.btnSend.isEnabled = true
                    chatAdapter.addMessage(ChatMessage("👋 Gemma siap! Tanya apa saja soal kode.", false))
                },
                onFailure = { e ->
                    binding.tvStatus.text = "❌ Error"
                    chatAdapter.addMessage(ChatMessage(
                        "⚠️ Gagal load model:\n```\n${e.message}\n```\n\nPastikan file ada di:\n`/storage/emulated/0/Documents/Gemma4/gemma-2b-it-gpu-int4.bin`",
                        false))
                }
            )
        }
    }

    private fun sendMessage() {
        if (isGenerating || !::gemmaEngine.isInitialized || !gemmaEngine.isReady) return
        val text = binding.etInput.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        binding.etInput.setText("")
        chatAdapter.addMessage(ChatMessage(text, true))
        scrollToBottom()
        isGenerating = true
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSend.isEnabled = false
        chatAdapter.addMessage(ChatMessage("▊", false))
        val buffer = StringBuilder()
        lifecycleScope.launch {
            try {
                gemmaEngine.generate(text) { token ->
                    buffer.append(token)
                    lifecycleScope.launch(Dispatchers.Main) {
                        chatAdapter.updateLastMessage(buffer.toString())
                        scrollToBottom()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    chatAdapter.updateLastMessage("❌ Error: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isGenerating = false
                    binding.progressBar.visibility = View.GONE
                    binding.btnSend.isEnabled = true
                }
            }
        }
    }

    private fun scrollToBottom() {
        binding.rvMessages.scrollToPosition(chatAdapter.itemCount - 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::gemmaEngine.isInitialized) gemmaEngine.close()
    }
}
