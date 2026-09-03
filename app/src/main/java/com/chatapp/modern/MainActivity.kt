package com.chatapp.modern

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.chatapp.modern.databinding.ActivityMainBinding
import com.chatapp.modern.ui.chat.ChatsFragment

/**
 * Single-activity host. It simply hosts [ChatsFragment] in a container. In a full
 * app this would be a bottom-navigation shell; here it is intentionally minimal so
 * the call UI is the focus.
 */
class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ChatsFragment())
                .commit()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
