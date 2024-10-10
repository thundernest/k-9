package com.fsck.k9.ui.choosefolder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import app.k9mail.core.mail.folder.api.FolderType
import app.k9mail.core.ui.legacy.designsystem.atom.icon.Icons
import app.k9mail.legacy.account.Account
import app.k9mail.legacy.message.controller.MessageReference
import app.k9mail.legacy.ui.folder.DisplayFolder
import app.k9mail.legacy.ui.folder.FolderIconProvider
import app.k9mail.legacy.ui.folder.FolderNameFormatter
import com.fsck.k9.Preferences
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.logging.Timber.d
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.K9Activity
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import java.util.Locale
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class ChooseFolderActivity : K9Activity() {
    private val viewModel: ChooseFolderViewModel by viewModel()
    private val preferences: Preferences by inject()
    private val messagingController: MessagingController by inject()
    private val folderNameFormatter: FolderNameFormatter by inject()
    private val folderIconProvider: FolderIconProvider by inject { parametersOf(theme) }

    private lateinit var recyclerView: RecyclerView
    private lateinit var itemAdapter: ItemAdapter<FolderListItem>
    private lateinit var account: Account
    private lateinit var action: Action
    private var currentFolderId: Long? = null
    private var scrollToFolderId: Long? = null
    private var messageReference: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        d("MBAL: in ChooseFolderActivity.onCreate()")
        setLayout(R.layout.folder_list)

        if (!decodeArguments(savedInstanceState)) {
            finish()
            return
        }

        when (action) {
            Action.MOVE -> setTitle(R.string.choose_folder_move_title)
            Action.COPY -> setTitle(R.string.choose_folder_copy_title)
            else -> setTitle(R.string.choose_folder_title)
        }

        initializeActionBar()
        initializeSpinner()
        initializeFolderList()

        viewModel.getFolders().observe(this) { folders ->
            d("MBAL: in ChooseFolderActivity.onCreate(); got folders from viewModel; n folders=${folders.size}")
            updateFolderList(folders)
        }

        val savedShowHiddenFolders = savedInstanceState?.getBoolean(STATE_SHOW_HIDDEN_FOLDERS)
        val showHiddenFolders = savedShowHiddenFolders ?: false

// TODO MBAL remove this commented code
//        viewModel.setDisplayMode(account, showHiddenFolders)
        val accounts = preferences.getAccounts()
        viewModel.setDisplayMode(accounts, account, showHiddenFolders)
    }

    private fun decodeArguments(savedInstanceState: Bundle?): Boolean {
        action = intent.action?.toAction() ?: error("Missing Intent action")

        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT) ?: return false
        account = preferences.getAccount(accountUuid) ?: return false

        messageReference = intent.getStringExtra(EXTRA_MESSAGE_REFERENCE)
        currentFolderId = intent.getLongExtraOrNull(EXTRA_CURRENT_FOLDER_ID)

        scrollToFolderId = if (savedInstanceState != null) {
            savedInstanceState.getLongOrNull(STATE_SCROLL_TO_FOLDER_ID)
        } else {
            intent.getLongExtraOrNull(EXTRA_SCROLL_TO_FOLDER_ID)
        }

        return true
    }

    private fun initializeSpinner() {
        val spinner: Spinner = findViewById(R.id.accountSpinner)
        val accounts = preferences.getAccounts()
        val options = accounts.map { it.name + " (" + it.email + ")"}
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedAccount = accounts[position]
                val showHiddenFolders = viewModel.isShowHiddenFolders
                viewModel.setDisplayMode(accounts, selectedAccount, showHiddenFolders)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun initializeActionBar() {
        val actionBar = supportActionBar ?: error("Action bar missing")
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeAsUpIndicator(Icons.Outlined.Close)
    }

    private fun initializeFolderList() {
        d("MBAL: in ChooseFolderActivity.initializeFolderList()")
        itemAdapter = ItemAdapter()
        itemAdapter.itemFilter.filterPredicate = ::folderListFilter

        val folderListAdapter = FastAdapter.with(itemAdapter).apply {
            setHasStableIds(true)
            onClickListener = { _, _, item: FolderListItem, _ ->
                returnResult(item.databaseId, item.displayName)
                true
            }
        }

        recyclerView = findViewById(R.id.folderList)
        recyclerView.adapter = folderListAdapter
    }

    private fun updateFolderList(displayFolders: List<DisplayFolder>) {
        d("MBAL: in ChooseFolderActivity.updateFolderList(); n displayFolders=${displayFolders.size}")
        val folderListItems = displayFolders.asSequence()
            .filterNot { it.folder.type == FolderType.OUTBOX }
            .filterNot { it.folder.id == currentFolderId }
            .map { displayFolder ->
                val databaseId = displayFolder.folder.id
                val folderIconResource = folderIconProvider.getFolderIcon(displayFolder.folder.type)
                val displayName = folderNameFormatter.displayName(displayFolder.folder)

                FolderListItem(databaseId, folderIconResource, displayName)
            }
            .toList()

        itemAdapter.set(folderListItems)

        scrollToFolder(folderListItems)
    }

    private fun scrollToFolder(folders: List<FolderListItem>) {
        if (scrollToFolderId == null) return

        val index = folders.indexOfFirst { it.databaseId == scrollToFolderId }
        if (index != -1) {
            recyclerView.scrollToPosition(index)
        }

        scrollToFolderId = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        scrollToFolderId?.let { folderId -> outState.putLong(STATE_SCROLL_TO_FOLDER_ID, folderId) }
        outState.putBoolean(STATE_SHOW_HIDDEN_FOLDERS, viewModel.isShowHiddenFolders)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.choose_folder_option, menu)
        configureFolderSearchView(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.toggle_hidden_folders)?.setChecked(viewModel.isShowHiddenFolders)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun configureFolderSearchView(menu: Menu) {
        val folderMenuItem = menu.findItem(R.id.filter_folders)
        val folderSearchView = folderMenuItem.actionView as SearchView
        folderSearchView.queryHint = getString(R.string.folder_list_filter_hint)
        folderSearchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    itemAdapter.filter(query)
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    itemAdapter.filter(newText)
                    return true
                }
            },
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        d("MBAL: in ChooseFolderActivity.onOptionsItemSelected()")
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.toggle_hidden_folders -> setShowHiddenFolders(item.isChecked.not())
            R.id.list_folders -> refreshFolderListForAllAccounts() // MBAL -- listing folders for all accounts
//            R.id.list_folders -> refreshFolderList()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun refreshFolderList() {
        messagingController.refreshFolderList(account)
    }

    // MBAL -- listing folders for all accounts
    private fun refreshFolderListForAllAccounts() {
        d("MBAL: in refreshFolderListForAllAcconts")
        val accounts = preferences.getAccounts()
        d("MBAL: in refreshFolderListForAllAcconts; n accounts="+accounts.size)
        for (account in accounts) {
            d("MBAL: in refreshFolderListForAllAcconts; refreshing list for one account; account="+account.email)
            messagingController.refreshFolderList(account)
        }
    }
    private fun setShowHiddenFolders(enabled: Boolean) {
// TODO MBAL remove this commented code
//        viewModel.setDisplayMode(account, enabled)
        val accounts = preferences.getAccounts()
        viewModel.setDisplayMode(accounts, account, enabled)
    }

    private fun returnResult(folderId: Long, displayName: String) {
        val result = Intent().apply {
            putExtra(RESULT_SELECTED_FOLDER_ID, folderId)
            putExtra(RESULT_FOLDER_DISPLAY_NAME, displayName)
            putExtra(RESULT_MESSAGE_REFERENCE, messageReference)
        }

        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun folderListFilter(item: FolderListItem, constraint: CharSequence?): Boolean {
        if (constraint.isNullOrEmpty()) return true

        val locale = Locale.getDefault()
        val displayName = item.displayName.lowercase(locale)
        return constraint.splitToSequence(" ")
            .filter { it.isNotEmpty() }
            .map { it.lowercase(locale) }
            .any { it in displayName }
    }

    private fun Intent.getLongExtraOrNull(name: String): Long? {
        if (!hasExtra(name)) return null

        val value = getLongExtra(name, -1L)
        return if (value != -1L) value else null
    }

    private fun Bundle.getLongOrNull(name: String): Long? {
        return if (containsKey(name)) getLong(name) else null
    }

    private fun String.toAction() = Action.valueOf(this)

    enum class Action {
        MOVE,
        COPY,
        CHOOSE,
    }

    companion object {
        private const val STATE_SCROLL_TO_FOLDER_ID = "scrollToFolderId"
        private const val STATE_SHOW_HIDDEN_FOLDERS = "showHiddenFolders"
        private const val EXTRA_ACCOUNT = "accountUuid"
        private const val EXTRA_CURRENT_FOLDER_ID = "currentFolderId"
        private const val EXTRA_SCROLL_TO_FOLDER_ID = "scrollToFolderId"
        private const val EXTRA_MESSAGE_REFERENCE = "messageReference"
        const val RESULT_SELECTED_FOLDER_ID = "selectedFolderId"
        const val RESULT_FOLDER_DISPLAY_NAME = "folderDisplayName"
        const val RESULT_MESSAGE_REFERENCE = "messageReference"

        @JvmStatic
        fun buildLaunchIntent(
            context: Context,
            action: Action,
            accountUuid: String,
            currentFolderId: Long? = null,
            scrollToFolderId: Long? = null,
            messageReference: MessageReference? = null,
        ): Intent {
            return Intent(context, ChooseFolderActivity::class.java).apply {
                this.action = action.toString()
                putExtra(EXTRA_ACCOUNT, accountUuid)
                currentFolderId?.let { putExtra(EXTRA_CURRENT_FOLDER_ID, currentFolderId) }
                scrollToFolderId?.let { putExtra(EXTRA_SCROLL_TO_FOLDER_ID, scrollToFolderId) }
                messageReference?.let { putExtra(EXTRA_MESSAGE_REFERENCE, it.toIdentityString()) }
            }
        }
    }
}
