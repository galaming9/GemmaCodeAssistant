package com.gemmacode.assistant

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
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
        initModel()
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
                    chatAdapter.addMessage(ChatMessage("👋 Gemma 4 siap! Tanya apa saja soal kode.", false))
                },
                onFailure = { e ->
                    binding.tvStatus.text = "❌ Error"
                    chatAdapter.addMessage(ChatMessage("⚠️ Gagal load model:\n```\n${e.message}\n```\nPastikan **gemma4.task** ada di `/sdcard/Download/`", false))
                }
            )
        }
    }

    private fun sendMessage() {
        if (isGenerating || !gemmaEngine.isReady) return
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
        gemmaEngine.close()
    }
}
