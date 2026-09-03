package com.chatapp.modern.ui.dialer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.chatapp.modern.R
import com.chatapp.modern.databinding.ActivityDialerBinding
import com.chatapp.modern.ui.call.CallActivity

/**
 * Dialer screen: a phone keypad with a call button. Typed digits form the remote
 * address that is handed to the imported Call UI.
 */
class DialerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDialerBinding

    private val digits = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDialerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wireKeypad()
        updateDisplay()

        binding.actionCall.setOnClickListener {
            val number = digits.toString()
            if (number.isBlank()) return@setOnClickListener
            val intent = Intent(this, CallActivity::class.java).apply {
                putExtra(CallActivity.EXTRA_REMOTE_ADDRESS, number)
                putExtra(CallActivity.EXTRA_OUTGOING, true)
                putExtra(CallActivity.EXTRA_VIDEO_ENABLED, false)
            }
            startActivity(intent)
        }

        binding.actionDelete.setOnClickListener {
            if (digits.isNotEmpty()) {
                digits.deleteCharAt(digits.length - 1)
                updateDisplay()
            }
        }

        binding.buttonBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun wireKeypad() {
        val keys = listOf(
            Pair(binding.key0, '0'), Pair(binding.key1, '1'), Pair(binding.key2, '2'),
            Pair(binding.key3, '3'), Pair(binding.key4, '4'), Pair(binding.key5, '5'),
            Pair(binding.key6, '6'), Pair(binding.key7, '7'), Pair(binding.key8, '8'),
            Pair(binding.key9, '9'), Pair(binding.keyStar, '*'), Pair(binding.keyHash, '#')
        )
        keys.forEach { (view, digit) ->
            view.setOnClickListener {
                if (digits.length < 40) {
                    digits.append(digit)
                    updateDisplay()
                }
            }
        }
    }

    private fun updateDisplay() {
        binding.numberDisplay.text = digits.toString().ifBlank { getString(R.string.dialer_hint) }
        binding.numberDisplay.alpha = if (digits.isEmpty()) 0.5f else 1f
        binding.actionCall.isEnabled = digits.isNotEmpty()
        binding.actionCall.alpha = if (digits.isEmpty()) 0.5f else 1f
    }
}
