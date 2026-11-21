package dev.robertmichalak.aaactionbuttons

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import dev.robertmichalak.aaactionbuttons.model.Action
import dev.robertmichalak.aaactionbuttons.model.ButtonConfig
import java.util.UUID

class ConfigActivity : Activity() {
    private lateinit var store: ButtonStore
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var debugLogView: TextView
    private lateinit var adapter: ButtonListAdapter
    private var buttons: MutableList<ButtonConfig> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        actionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu)
        }
        applyContentInsets()

        DebugLog.init(this)
        store = ButtonStore(this)
        buttons = store.load().toMutableList()

        listView = findViewById(R.id.button_list)
        emptyView = findViewById(R.id.empty_view)
        adapter = ButtonListAdapter()
        listView.adapter = adapter
        listView.emptyView = emptyView

        findViewById<Button>(R.id.add_button).setOnClickListener {
            if (buttons.size >= MAX_BUTTONS) {
                Toast.makeText(this, R.string.max_buttons_reached, Toast.LENGTH_SHORT).show()
            } else {
                showEditor(null)
            }
        }

        findViewById<Button>(R.id.import_tasker_button).setOnClickListener {
            showTaskerImport()
        }

        val debugSection = findViewById<View>(R.id.debug_log_section)
        if (BuildConfig.DEBUG) {
            debugSection.visibility = View.VISIBLE
            debugLogView = findViewById(R.id.debug_log)
            findViewById<Button>(R.id.debug_refresh).setOnClickListener { refreshDebugLog() }
            findViewById<Button>(R.id.debug_clear).setOnClickListener {
                DebugLog.clear(this)
                refreshDebugLog()
            }
            findViewById<Button>(R.id.debug_copy).setOnClickListener {
                val text = DebugLog.snapshot().ifBlank { getString(R.string.debug_log_empty) }
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("AA Action Buttons debug", text))
                Toast.makeText(this, R.string.debug_copied, Toast.LENGTH_SHORT).show()
            }
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            showEditor(buttons[position])
        }

        logPermissionState()
        refreshList()
        refreshDebugLog()
        if (savedInstanceState == null && maybeShowSetupGuide()) return
        maybeRequestTaskerPermission()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            showMainMenu()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        logPermissionState()
        refreshDebugLog()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_TASKER_PERMISSION) return
        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        DebugLog.i("Runtime permission result granted=$granted")
        DebugLog.persist(this)
        logPermissionState()
        refreshDebugLog()
        Toast.makeText(
            this,
            if (granted) R.string.tasker_permission_granted else R.string.tasker_permission_denied,
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun logPermissionState() {
        val granted = checkSelfPermission(TASKER_RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED
        DebugLog.i("Permission $TASKER_RUN_PERMISSION granted=$granted")
        DebugLog.persist(this)
    }

    private fun maybeShowSetupGuide(): Boolean {
        val prefs = getPreferences(MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SETUP_GUIDE_SHOWN, false)) return false
        prefs.edit().putBoolean(KEY_SETUP_GUIDE_SHOWN, true).apply()
        showSetupGuide(onDismiss = { maybeRequestTaskerPermission() })
        return true
    }

    private fun maybeRequestTaskerPermission() {
        if (checkSelfPermission(TASKER_RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getPreferences(MODE_PRIVATE)
        if (prefs.getBoolean(KEY_TASKER_PERMISSION_REQUESTED, false)) return
        prefs.edit().putBoolean(KEY_TASKER_PERMISSION_REQUESTED, true).apply()
        DebugLog.i("Requesting $TASKER_RUN_PERMISSION on first launch")
        DebugLog.persist(this)
        requestPermissions(arrayOf(TASKER_RUN_PERMISSION), REQUEST_TASKER_PERMISSION)
    }

    private fun fixTaskerPermission() {
        val granted = checkSelfPermission(TASKER_RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            Toast.makeText(this, R.string.tasker_permission_granted, Toast.LENGTH_SHORT).show()
            return
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean(KEY_TASKER_PERMISSION_REQUESTED, true).apply()
        DebugLog.i("Requesting $TASKER_RUN_PERMISSION at runtime")
        DebugLog.persist(this)
        requestPermissions(arrayOf(TASKER_RUN_PERMISSION), REQUEST_TASKER_PERMISSION)
        showTaskerPermissionHelp()
    }

    private fun showMainMenu() {
        val anchor = window.decorView.findViewById<View>(android.R.id.home)
        if (anchor == null) {
            AlertDialog.Builder(this)
                .setItems(
                    arrayOf(
                        getString(R.string.setup_guide),
                        getString(R.string.fix_tasker_permission),
                    ),
                ) { _, which ->
                    if (which == 0) showSetupGuide() else fixTaskerPermission()
                }
                .show()
            return
        }
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.main_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_setup_guide -> showSetupGuide()
                    R.id.menu_fix_tasker_permission -> fixTaskerPermission()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private fun showSetupGuide(onDismiss: (() -> Unit)? = null) {
        val steps = listOf(
            getString(R.string.setup_step_developer),
            getString(R.string.setup_step_sources),
            getString(R.string.setup_step_tasker),
            getString(R.string.setup_step_car),
        )
        var step = 0
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.setup_step_title, 1, steps.size))
            .setMessage(steps[0])
            .setPositiveButton(R.string.setup_next, null)
            .setNegativeButton(R.string.setup_back, null)
            .setNeutralButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            val messageView = dialog.findViewById<TextView>(android.R.id.message)
            fun render() {
                dialog.setTitle(getString(R.string.setup_step_title, step + 1, steps.size))
                messageView?.text = steps[step]
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(
                    if (step == steps.lastIndex) R.string.setup_done else R.string.setup_next,
                )
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = step > 0
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (step == steps.lastIndex) {
                    dialog.dismiss()
                } else {
                    step += 1
                    render()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                if (step > 0) {
                    step -= 1
                    render()
                }
            }
            render()
        }
        if (onDismiss != null) {
            dialog.setOnDismissListener { onDismiss() }
        }
        dialog.show()
    }

    private fun showTaskerPermissionHelp() {
        AlertDialog.Builder(this)
            .setTitle(R.string.tasker_permission_title)
            .setMessage(getString(R.string.tasker_permission_help, packageName))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun refreshList() {
        adapter.notifyDataSetChanged()
    }

    private fun applyContentInsets() {
        val root = findViewById<View>(R.id.config_root)
        val base = (16 * resources.displayMetrics.density).toInt()
        root.setOnApplyWindowInsetsListener { view, insets ->
            val left: Int
            val top: Int
            val right: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                left = bars.left
                top = bars.top
                right = bars.right
                bottom = bars.bottom
            } else {
                left = insets.systemWindowInsetLeft
                top = insets.systemWindowInsetTop
                right = insets.systemWindowInsetRight
                bottom = insets.systemWindowInsetBottom
            }
            // Target SDK 36 draws edge-to-edge, so the action bar sits on the content.
            val header = if (Build.VERSION.SDK_INT >= 35) actionBarHeight() else 0
            view.setPadding(base + left, base + top + header, base + right, base + bottom)
            insets
        }
        root.requestApplyInsets()
    }

    private fun actionBarHeight(): Int {
        val value = TypedValue()
        if (!theme.resolveAttribute(android.R.attr.actionBarSize, value, true)) return 0
        return TypedValue.complexToDimensionPixelSize(value.data, resources.displayMetrics)
    }

    private fun refreshDebugLog() {
        if (!::debugLogView.isInitialized) return
        val header = "build=${BuildConfig.VERSION_NAME}\n${AaDiagnostics.summary(this)}\n---\n"
        val text = DebugLog.snapshot()
        debugLogView.text = header + text.ifBlank { getString(R.string.debug_log_empty) }
    }

    private fun persistAndNotify() {
        store.save(buttons.toList())
        sendBroadcast(
            Intent(ButtonStore.ACTION_BUTTONS_CHANGED).setPackage(packageName),
        )
        refreshList()
    }

    private fun existingTaskerNames(): Set<String> = buttons.mapNotNull { button ->
        (button.action as? Action.Tasker)?.taskName
    }.toSet()

    private fun showTaskerImport() {
        when (val result = TaskerTasks.query(this)) {
            is TaskerQueryResult.ExternalAccessDisabled -> {
                Toast.makeText(this, R.string.tasker_ext_access_required, Toast.LENGTH_LONG).show()
            }
            is TaskerQueryResult.Unavailable -> {
                Toast.makeText(this, R.string.tasker_unavailable, Toast.LENGTH_LONG).show()
            }
            is TaskerQueryResult.Ok -> {
                if (result.tasks.isEmpty()) {
                    Toast.makeText(this, R.string.tasker_none_found, Toast.LENGTH_SHORT).show()
                    return
                }
                val already = existingTaskerNames()
                val candidates = result.tasks.filterNot { it.name in already }
                if (candidates.isEmpty()) {
                    Toast.makeText(this, R.string.tasker_all_imported, Toast.LENGTH_SHORT).show()
                    return
                }
                val slotsLeft = MAX_BUTTONS - buttons.size
                if (slotsLeft <= 0) {
                    Toast.makeText(this, R.string.max_buttons_reached, Toast.LENGTH_SHORT).show()
                    return
                }

                val labels = candidates.map { task ->
                    if (task.projectName.isBlank()) task.name
                    else "${task.projectName} / ${task.name}"
                }.toTypedArray()
                val checked = BooleanArray(candidates.size)

                AlertDialog.Builder(this)
                    .setTitle(R.string.tasker_import_title)
                    .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                        checked[which] = isChecked
                    }
                    .setPositiveButton(R.string.tasker_import_add) { _, _ ->
                        val selected = candidates.filterIndexed { index, _ -> checked[index] }
                        if (selected.isEmpty()) return@setPositiveButton
                        if (selected.size > slotsLeft) {
                            Toast.makeText(
                                this,
                                getString(R.string.tasker_slots_left, slotsLeft),
                                Toast.LENGTH_LONG,
                            ).show()
                            return@setPositiveButton
                        }
                        selected.forEach { task ->
                            buttons.add(
                                ButtonConfig(
                                    id = UUID.randomUUID().toString(),
                                    label = task.name,
                                    action = Action.Tasker(taskName = task.name),
                                ),
                            )
                        }
                        persistAndNotify()
                        Toast.makeText(
                            this,
                            getString(R.string.tasker_imported_count, selected.size),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun confirmDelete(button: ButtonConfig) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.confirm_delete_message, button.label))
            .setPositiveButton(R.string.delete) { _, _ ->
                buttons.removeAll { it.id == button.id }
                persistAndNotify()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditor(existing: ButtonConfig?) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_button, null)
        val labelInput = view.findViewById<EditText>(R.id.input_label)
        val typeSpinner = view.findViewById<Spinner>(R.id.input_type)
        val primaryInput = view.findViewById<EditText>(R.id.input_primary)
        val secondaryInput = view.findViewById<EditText>(R.id.input_secondary)

        typeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.action_types),
        )

        if (existing != null) {
            labelInput.setText(existing.label)
            when (val action = existing.action) {
                is Action.Tasker -> {
                    typeSpinner.setSelection(0)
                    primaryInput.setText(action.taskName)
                    secondaryInput.setText("")
                }
                is Action.Http -> {
                    typeSpinner.setSelection(1)
                    primaryInput.setText(action.url)
                    secondaryInput.setText(action.method)
                }
                is Action.IntentAction -> {
                    typeSpinner.setSelection(2)
                    primaryInput.setText(action.action)
                    secondaryInput.setText(action.packageName.orEmpty())
                }
            }
        } else {
            typeSpinner.setSelection(0)
            secondaryInput.setText("GET")
        }

        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                itemView: View?,
                position: Int,
                id: Long,
            ) {
                when (position) {
                    0 -> {
                        primaryInput.hint = getString(R.string.hint_task_name)
                        secondaryInput.hint = getString(R.string.hint_unused)
                        secondaryInput.isEnabled = false
                    }
                    1 -> {
                        primaryInput.hint = getString(R.string.hint_url)
                        secondaryInput.hint = getString(R.string.hint_http_method)
                        secondaryInput.isEnabled = true
                        if (secondaryInput.text.isNullOrBlank()) secondaryInput.setText("GET")
                    }
                    else -> {
                        primaryInput.hint = getString(R.string.hint_intent_action)
                        secondaryInput.hint = getString(R.string.hint_intent_package)
                        secondaryInput.isEnabled = true
                    }
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        typeSpinner.setSelection(typeSpinner.selectedItemPosition)

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_button else R.string.edit_button)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)

        if (existing != null) {
            builder.setNeutralButton(R.string.delete, null)
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val label = labelInput.text?.toString()?.trim().orEmpty()
                val primary = primaryInput.text?.toString()?.trim().orEmpty()
                val secondary = secondaryInput.text?.toString()?.trim().orEmpty()
                if (label.isEmpty() || primary.isEmpty()) {
                    Toast.makeText(this, R.string.validation_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val action = when (typeSpinner.selectedItemPosition) {
                    0 -> Action.Tasker(taskName = primary)
                    1 -> Action.Http(
                        method = secondary.ifEmpty { "GET" },
                        url = primary,
                    )
                    else -> Action.IntentAction(
                        action = primary,
                        packageName = secondary.takeIf { it.isNotEmpty() },
                    )
                }
                val updated = ButtonConfig(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    label = label,
                    action = action,
                )
                if (existing == null) {
                    buttons.add(updated)
                } else {
                    val index = buttons.indexOfFirst { it.id == existing.id }
                    if (index >= 0) buttons[index] = updated
                }
                persistAndNotify()
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                existing ?: return@setOnClickListener
                dialog.dismiss()
                confirmDelete(existing)
            }
        }
        dialog.show()
    }

    private inner class ButtonListAdapter : ArrayAdapter<ButtonConfig>(
        this,
        0,
        buttons,
    ) {
        override fun getCount(): Int = buttons.size

        override fun getItem(position: Int): ButtonConfig = buttons[position]

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_button, parent, false)
            val button = buttons[position]
            view.findViewById<TextView>(R.id.item_label).text = button.label
            view.findViewById<TextView>(R.id.item_subtitle).text = subtitleFor(button.action)

            view.findViewById<Button>(R.id.move_up).apply {
                isEnabled = position > 0
                setOnClickListener {
                    if (position > 0) {
                        buttons.swap(position, position - 1)
                        persistAndNotify()
                    }
                }
            }
            view.findViewById<Button>(R.id.move_down).apply {
                isEnabled = position < buttons.lastIndex
                setOnClickListener {
                    if (position < buttons.lastIndex) {
                        buttons.swap(position, position + 1)
                        persistAndNotify()
                    }
                }
            }
            view.findViewById<Button>(R.id.delete_button).setOnClickListener {
                confirmDelete(button)
            }
            return view
        }
    }

    private fun subtitleFor(action: Action): String = when (action) {
        is Action.Tasker -> getString(R.string.subtitle_tasker)
        is Action.Http -> getString(R.string.subtitle_http, action.method.uppercase())
        is Action.IntentAction -> getString(R.string.subtitle_intent)
    }

    private fun <T> MutableList<T>.swap(i: Int, j: Int) {
        val tmp = this[i]
        this[i] = this[j]
        this[j] = tmp
    }

    companion object {
        private const val MAX_BUTTONS = 8
        private const val REQUEST_TASKER_PERMISSION = 1001
        private const val TASKER_RUN_PERMISSION =
            "net.dinglisch.android.tasker.PERMISSION_RUN_TASKS"
        private const val KEY_TASKER_PERMISSION_REQUESTED = "tasker_permission_requested"
        private const val KEY_SETUP_GUIDE_SHOWN = "setup_guide_shown"
    }
}
