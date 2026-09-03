package com.chatapp.modern.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.chatapp.modern.databinding.FragmentChatsBinding
import com.chatapp.modern.model.Contact
import com.chatapp.modern.ui.call.CallActivity
import com.chatapp.modern.ui.dialer.DialerActivity

/**
 * Chat/conversation list. This is the integration point: tapping the call icon on
 * a conversation launches the imported Call UI ([CallActivity]).
 */
class ChatsFragment : Fragment() {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    private val contacts = listOf(
        Contact("1", "Sara Ahmed", "sara@example.com", "هل وصلت إلى المنزل؟", 2, true),
        Contact("2", "Omar Khaled", "omar@example.com", "سأتصل بك لاحقاً", 0, true),
        Contact("3", "Layla Hassan", "layla@example.com", "تم استلام الملف", 0, false),
        Contact("4", "Yousef Ali", "yousef@example.com", "نراكم في الاجتماع", 5, true),
        Contact("5", "Mona Salem", "mona@example.com", "شكراً لك", 0, false)
    )

    private val adapter by lazy {
        ContactAdapter(contacts, onRowClick = ::openConversation, onCallClick = ::startCall)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.chatListRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.chatListRecycler.adapter = adapter

        // FAB opens the Dialer (phone pad) so the user can dial any number.
        binding.fabDialer.setOnClickListener {
            startActivity(Intent(requireContext(), DialerActivity::class.java))
        }
    }

    private fun openConversation(contact: Contact) {
        Toast.makeText(
            requireContext(),
            getString(com.chatapp.modern.R.string.opening_conversation, contact.displayName),
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Integration: launch the imported Call UI for an outgoing call. */
    private fun startCall(contact: Contact) {
        val intent = Intent(requireContext(), CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_REMOTE_ADDRESS, contact.sipAddress)
            putExtra(CallActivity.EXTRA_DISPLAY_NAME, contact.displayName)
            putExtra(CallActivity.EXTRA_OUTGOING, true)
            putExtra(CallActivity.EXTRA_VIDEO_ENABLED, false)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
