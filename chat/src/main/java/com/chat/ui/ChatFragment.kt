package com.chat.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.chat.ui.views.MicrophoneButton
import com.chat.ui.views.SendButton
import com.chat.utils.SystemBarUtils
import com.chat.utils.resolveColor
import com.chat.utils.resolveStyleRes
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch


internal class ChatFragment : Fragment(), BackPressHandler {
    private var toolbar: MaterialToolbar? = null
    private var editText: EditText? = null
    private var microphoneButton: MicrophoneButton? = null
    private var sendButton: SendButton? = null
    private var messageListView: RecyclerView? = null
    private var messageAdapter: MessageAdapter? = null
    private var suggestionsChipGroup: ChipGroup? = null
    private var multiSelectionActionMode: ActionMode? = null

    private var messageTypeChooserButton: View? = null
    private var messageTypeChooserLayout: View? = null
    private var generateTextButton: View? = null
    private var generateImageButton: View? = null

    // Colors
    @ColorInt
    private var statusBarColor: Int? = null
    @ColorInt
    private var actionModeBackgroundColor: Int? = null

    private val viewModel: ChatViewModel by lazy {
        val factory = ChatViewModelFactory(requireContext())
        val provider = ViewModelProvider(this, factory)
        provider[ChatViewModel::class.java]
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        statusBarColor = context.resolveColor(android.R.attr.statusBarColor)
        context.resolveStyleRes(com.google.android.material.R.attr.actionModeStyle)?.let { styleId ->
            val themedContext = androidx.appcompat.view.ContextThemeWrapper(context, styleId)
            actionModeBackgroundColor = themedContext.resolveColor(com.google.android.material.R.attr.background)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        skipWindowInsets(view)

        view.findViewById<SafeDrawingImageView>(R.id.background).also { imageView ->
            skipWindowInsets(imageView)
            imageView.onError = { err ->
                viewModel.onUiErrorOccurred(err)
            }
            ChatBackgroundLoader.load(imageView)
        }

        toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.speaker -> viewModel.onSpeakerClick()
                }
                false
            }
        }

        editText = view.findViewById<EditText>(R.id.edit_text).apply {
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    triggerSendMessage()
                    true
                } else {
                    false
                }
            }
        }

//        microphoneButton = view.findViewById<MicrophoneButton>(R.id.microphone_button).apply {
//            isVisible = isMicrophoneAvailable(context)
//            setOnClickListener {
//                handleMicrophoneButtonClick()
//            }
//        }

        sendButton = view.findViewById<SendButton>(R.id.send_button).apply {
            setOnClickListener {
                triggerSendMessage()
            }
        }

        val adapter = MessageAdapter(
            onItemClickListener = object : OnItemClickListener {
                override fun onItemClick(item: Message, itemView: View) {
                    showContextMenu(item, itemView)
                }
            },
            multiSelectionModeListener = object : MultiSelectionModeListener {
                override fun onStartMultiSelectionMode() {
                    startMultiSelectionActionMode()
                }
                override fun onItemSelectionChanged(item: Message, isSelected: Boolean) {
                    updateMultiSelectionActionMode(item, isSelected)
                }
                override fun onStopMultiSelectionMode() {
                    stopMultiSelectionActionMode()
                }
            }
        )
        messageAdapter = adapter
        messageListView = view.findViewById<RecyclerView>(R.id.messages_list).apply {
            layoutManager = MessageLayoutManager(view.context).apply {
                stackFromEnd = true
            }
            // addItemDecoration(MessageMarginItemDecoration())
            // this.adapter = adapter
            this.adapter = ConcatAdapter(OnboardingMessageAdapter(), adapter)
        }

        suggestionsChipGroup = view.findViewById(R.id.suggestions)

        messageTypeChooserButton = view.findViewById<View?>(R.id.message_type_chooser)?.apply {
            setOnClickListener {
                viewModel.onMessageTypeChooserClick()
            }
        }
        messageTypeChooserLayout = view.findViewById(R.id.message_type_chooser_layout)
        generateTextButton = view.findViewById<View?>(R.id.generate_text)?.apply {
            setOnClickListener {
                viewModel.onTextMessageTypeClick()
            }
        }
        generateImageButton = view.findViewById<View?>(R.id.generate_image)?.apply {
            setOnClickListener {
                viewModel.onImageMessageTypeClick()
            }
        }

        observeViewModel(viewLifecycleOwner)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        toolbar = null
        editText = null
        microphoneButton = null
        sendButton = null
        messageListView = null
        messageAdapter = null
        suggestionsChipGroup = null
        messageTypeChooserButton = null
        messageTypeChooserLayout = null
        generateTextButton = null
        generateImageButton = null
    }

    private fun observeViewModel(owner: LifecycleOwner) = with(viewModel) {
        isLoading.observe(owner) { isLoading ->
            sendButton?.state = if (isLoading) SendButton.State.LOADING else SendButton.State.IDLE
        }

        messagePagedList.observe(owner) { pagedList ->
            messageAdapter?.submitList(pagedList) {
                // When a new paged list appears, just scroll to the bottom immediately
                scrollToLastMessage(smoothScroll = false)
            }
        }

        suggestions.observe(owner) { suggestions ->
            // setSuggestions(suggestions)
            // TODO: disable suggestions for now since we have an onboarding message
            setSuggestions(null)
        }

        error.observe(owner) { error ->
            error?.also(::showError)
        }

        clearInputFieldEvent.observe(owner) {
            editText?.text = null
        }

        copyMessagesEvent.observe(owner) { messages ->
            lifecycleScope.launch {
                context?.copyMessagesToClipboard(messages)
                context?.also {
                    Toast.makeText(it, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                }
            }
        }

        shareMessagesEvent.observe(owner) { messages ->
            lifecycleScope.launch {
                context?.shareMessages(messages)
            }
        }

        deleteMessagesConfirmationEvent.observe(owner) { messages ->
            askConfirmationForMessageDeletion(messages)
        }

        closeContextMenuEvent.observe(owner) {
            multiSelectionActionMode?.finish()
        }

        isSpeakerMuted.observe(owner) { isMuted ->
            toolbar?.menu?.findItem(R.id.speaker)?.also { item ->
                item.setIcon(
                    if (isMuted) R.drawable.ic_speaker_muted_24 else R.drawable.ic_speaker_24
                )
            }
        }

        isListeningToSpeech.observe(owner) { isListening ->
            microphoneButton?.state =
                if (isListening) MicrophoneButton.State.LISTENING else MicrophoneButton.State.IDLE
        }

        isMessageTypeChooserVisible.observe(owner) { isVisible: Boolean? ->
            val isActuallyVisible = isVisible ?: false
            (view as? ViewGroup)?.also { sceneRoot ->
//                val slideEdge = if (isVisible == false) Gravity.TOP else Gravity.BOTTOM
                val transition = AutoTransition().apply {
                    duration = 80L
//                    interpolator = AccelerateInterpolator()
                }
                TransitionManager.beginDelayedTransition(sceneRoot, transition)
            }
            messageTypeChooserLayout?.isVisible = isActuallyVisible
            messageTypeChooserButton?.isActivated = isActuallyVisible
        }

        messageType.observe(owner) { type ->
            when (type) {
                ChatViewModel.MessageType.GENERATE_TEXT -> {
                    generateTextButton?.isActivated = true
                    generateImageButton?.isActivated = false
                    editText?.setHint(R.string.generate_text)
                }
                ChatViewModel.MessageType.GENERATE_IMAGE -> {
                    generateTextButton?.isActivated = false
                    generateImageButton?.isActivated = true
                    editText?.setHint(R.string.generate_image)
                }
                else -> {
                    generateTextButton?.isEnabled = false
                    generateImageButton?.isEnabled = false
                    editText?.hint = ""
                }
            }
        }
    }

    private fun scrollToLastMessage(smoothScroll: Boolean) {
        val messageCount = messageAdapter?.currentList?.size ?: return
        val listView = messageListView ?: return
        if (smoothScroll) {
            listView.smoothScrollToPosition(messageCount)
        } else {
            listView.scrollToPosition(messageCount)
        }
    }

    private fun skipWindowInsets(view: View) {
        view.fitsSystemWindows = true
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets -> insets }
        view.requestApplyInsets()
    }

    private fun triggerSendMessage() {
        val editText = editText ?: return
        val text = editText.text ?: return
        if (text.isNotEmpty()) {
            viewModel.onSendMessageClick(text)
        }
    }

    private fun showError(error: Throwable) {
        val context = this.context ?: return
        val message = error.message.orEmpty().ifBlank { error.toString() }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopMultiSelectionActionMode()
        }
    }

    private fun startMultiSelectionActionMode() {
        if (multiSelectionActionMode != null) {
            // TODO: check if the current action mode is active
            return
        }
        val activity = this.activity as? AppCompatActivity ?: return
        val callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(R.menu.context_menu_messages, menu)
                actionModeBackgroundColor?.also(::setStatusBarColor)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    R.id.copy -> {
                        viewModel.onCopyMessagesClick(messageAdapter?.getSelectedItems().orEmpty())
                    }
                    R.id.share -> {
                        viewModel.onShareMessagesClick(messageAdapter?.getSelectedItems().orEmpty())
                    }
                    R.id.delete -> {
                        viewModel.onDeleteMessagesClick(messageAdapter?.getSelectedItems().orEmpty())
                    }
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                messageAdapter?.stopMultiSelectionMode()
                multiSelectionActionMode = null
                statusBarColor?.also(::setStatusBarColor)
            }
        }
        val actionMode = activity.startSupportActionMode(callback)
        multiSelectionActionMode = actionMode
    }

    private fun updateMultiSelectionActionMode(item: Message, isSelected: Boolean) {
        val selectedItemCount = messageAdapter?.getSelectedItemCount() ?: 0
        multiSelectionActionMode?.title = selectedItemCount.toString()
    }

    private fun stopMultiSelectionActionMode() {
        multiSelectionActionMode?.finish()
        multiSelectionActionMode = null
    }

    private fun setStatusBarColor(@ColorInt color: Int) {
        val activity = this.activity ?: return
        SystemBarUtils.setStatusBarColor(activity, color)
    }

    private fun askConfirmationForMessageDeletion(messages: Collection<Message>) {
        val context = this.context ?: return
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.delete_messages)
            .setMessage(R.string.delete_messages_confirmation)
            .setPositiveButton(R.string.delete) { dialog, _ ->
                viewModel.onMessageDeletionConfirmed(messages)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                viewModel.onMessageDeletionDeclined(messages)
                dialog.dismiss()
            }
            .create()
        dialog.show()
    }

    private fun showContextMenu(item: Message, itemView: View) {
        val containsImages = !item.imageAttachments?.images.isNullOrEmpty()
        val popup = PopupMenu(itemView.context, itemView)
        popup.inflate(R.menu.menu_message)
        popup.menu.findItem(R.id.view_images)?.isVisible = containsImages
        popup.menu.findItem(R.id.download_images)?.isVisible = false //containsImages
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.view_images -> viewImages(item)
                R.id.download_images -> viewModel.onDownloadMessageImagesClick(item)
                R.id.copy -> viewModel.onCopyMessageClick(item)
                R.id.share -> viewModel.onShareMessageClick(item)
                R.id.delete -> viewModel.onDeleteMessageClick(item)
            }
            true
        }
        popup.show()
    }

//    private fun smoothScrollChatToBottom() {
//        val scrollView = this.chatScrollView ?: return
//        val childView = if (scrollView.childCount > 0) scrollView.getChildAt(0) else null
//        childView ?: return
//        childView.doOnLayout {
//            val childBottom = childView.bottom + scrollView.paddingBottom
//            val deltaScrollY = childBottom - scrollView.measuredHeight - scrollView.scrollY
//            scrollView.smoothScrollBy(0, deltaScrollY, 400)
//        }
//    }

    private fun viewImages(message: Message) {
        MessageImageViewerDialog.show(childFragmentManager, message)
    }

    private fun setSuggestions(suggestions: List<String>?) {
        val chipGroup = suggestionsChipGroup ?: return
        chipGroup.removeAllViews()
        suggestions?.forEach { text ->
            val chip = Chip(chipGroup.context)
            chip.setEnsureMinTouchTargetSize(false)
            chip.text = text
            chip.setOnClickListener {
                viewModel.onSuggestionClick(text)
            }
            chipGroup.addView(chip)
        }
        val visible = !suggestions.isNullOrEmpty()
        // Animation works well only when the suggestion layout appears
        if (visible) {
            (view as? ViewGroup)?.also { sceneRoot ->
                val transition = AutoTransition().apply {
                    duration = 150L
                }
                TransitionManager.beginDelayedTransition(sceneRoot, transition)
            }
        }
        chipGroup.isVisible = visible
    }

    private fun handleMicrophoneButtonClick() {
        val activity = this.activity ?: return
        if (!isMicrophoneAvailable(activity)) {
            return
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_MICROPHONE_BUTTON_CLICK)
        } else {
            viewModel.onMicrophoneButtonClick()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i in permissions.indices) {
            if (permissions[i] == Manifest.permission.RECORD_AUDIO) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    viewModel.onMicrophoneButtonClick()
                }
            }
        }
    }

    private fun isMicrophoneAvailable(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }

    override fun handleBackPress(): Boolean {
        return viewModel.handleBackPress()
    }

    companion object {
        private const val RC_MICROPHONE_BUTTON_CLICK = 1337
    }
}