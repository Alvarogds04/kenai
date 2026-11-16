package com.kenai.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kenai.android.R
import com.kenai.android.app.AppModel
import com.kenai.android.core.ProviderKeyState
import com.kenai.android.core.ProviderPresets
import com.kenai.android.core.ProviderSettings
import com.kenai.android.core.Spacing
import com.kenai.android.core.TypeScale
import com.kenai.android.provider.ProviderConfig

/**
 * Which provider the chat talks to, and the providers themselves: who answers (the computer, or one the phone calls
 * itself), each one's key state, and an editor for adding, changing or deleting one.
 *
 * The screen is honest about the trade (the messages leave the phone for the provider), the key never comes back out of
 * the Keystore (the field opens empty every time, and nothing typed here survives a recreation), and the address is a
 * field, never a constant: S10 says nothing belongs to one machine.
 */
@Composable
fun ProvidersScreen(model: AppModel, onBack: () -> Unit) {
    // The editor, while it is open. Held in remember and never in rememberSaveable: a recreated activity closes it and
    // forgets what was typed, which is the only safe thing to do with a key (T6).
    var editor by remember { mutableStateOf<Editor?>(null) }
    when (val open = editor) {
        null -> ProviderList(model, onBack, onEdit = { editor = Editor(it) }, onNew = { editor = Editor(null) })
        else -> ProviderEditor(model, open.config, onDone = { editor = null })
    }
}

/** The editor that is open: a saved provider, or a new one (null). */
private class Editor(val config: ProviderConfig?)

// ── The list: who answers, and which providers exist ─────────────────────────────────────────────

@Composable
private fun ProviderList(model: AppModel, onBack: () -> Unit, onEdit: (ProviderConfig) -> Unit, onNew: () -> Unit) {
    val t = tokens()
    val settings = model.providerSettings
    // The keys are read once, when the list appears: the Keystore is not asked again on every frame.
    val keyStates = remember(settings.providers) {
        settings.providers.associate { it.id to model.providerKeyState(it.id) }
    }
    // A change of source starts a new conversation, so it is asked first; null: nothing is waiting.
    var pendingSwitch by remember { mutableStateOf<NewSource?>(null) }
    val computer = stringResource(R.string.providers_source_computer)

    LazyColumn(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(Gap)) {
        item { ScreenTitle(stringResource(R.string.providers_title), onBack) }
        item {
            // The explanation stays on screen, disclosure included: it is a requirement, not a hint (T1a-3b).
            Panel {
                Column(Modifier.padding(Spacing.INSIDE.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.providers_explain), style = TypeScale.BODY.style(t.text.c()))
                    Text(stringResource(R.string.providers_disclosure), style = TypeScale.SECONDARY.style(t.textDim.c()))
                }
            }
        }

        item { SectionHeading(stringResource(R.string.providers_source_section)) }
        item {
            Panel {
                // The computer comes first: it is the default, and the only source until the user adds one.
                DescribedRow(
                    title = computer, hint = stringResource(R.string.providers_source_computer_hint),
                    description = stringResource(R.string.providers_choose, computer),
                    onClick = { if (settings.activeChat != null) pendingSwitch = NewSource(null, computer) },
                    trailing = { if (settings.activeChat == null) ChosenChip() },
                )
                for (config in settings.providers) {
                    RowDivider()
                    DescribedRow(
                        title = config.name, hint = hostOf(config.baseUrl),
                        description = stringResource(R.string.providers_choose, config.name),
                        onClick = { if (settings.activeChat != config.id) pendingSwitch = NewSource(config.id, config.name) },
                        trailing = { if (settings.activeChat == config.id) ChosenChip() },
                    )
                }
            }
        }

        item { SectionHeading(stringResource(R.string.providers_list_section)) }
        item {
            Panel {
                if (settings.providers.isEmpty()) {
                    Text(
                        stringResource(R.string.providers_empty), Modifier.padding(Spacing.INSIDE.dp),
                        style = TypeScale.BODY.style(t.textDim.c()),
                    )
                } else {
                    settings.providers.forEachIndexed { index, config ->
                        if (index > 0) RowDivider()
                        DescribedRow(
                            title = config.name, hint = hostOf(config.baseUrl),
                            description = stringResource(R.string.providers_edit, config.name),
                            onClick = { onEdit(config) },
                            trailing = { Chip(keyLabel(keyStates[config.id] ?: ProviderKeyState.ABSENT)) },
                        )
                    }
                }
            }
        }

        item {
            DescribedPill(
                stringResource(R.string.providers_add), stringResource(R.string.providers_add), { onNew() },
                Modifier.fillMaxWidth(), PillKind.PRIMARY,
            )
        }
        item { Spacer(Modifier.height(Gutter)) }
    }

    pendingSwitch?.let { wanted ->
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text(stringResource(R.string.providers_switch_title)) },
            text = { Text(stringResource(R.string.providers_switch_body, wanted.label)) },
            confirmButton = {
                DescribedTextAction(stringResource(R.string.providers_switch_confirm), stringResource(R.string.providers_switch_confirm)) {
                    pendingSwitch = null
                    model.selectChatSource(wanted.id)
                }
            },
            dismissButton = {
                DescribedTextAction(stringResource(R.string.action_cancel), stringResource(R.string.action_cancel)) { pendingSwitch = null }
            },
        )
    }
}

/** The source a confirmation waits for: a provider's id, or null for the computer. */
private class NewSource(val id: String?, val label: String)

@Composable
private fun ChosenChip() {
    Chip(stringResource(R.string.providers_chosen), tint = tokens().online.c())
}

// ── The editor: one provider, its address and its key ───────────────────────────────────────────

@Composable
private fun ProviderEditor(model: AppModel, config: ProviderConfig?, onDone: () -> Unit) {
    val t = tokens()
    val id = config?.id
    // What the fields hold. Never saved and never restored: these are remember, not rememberSaveable (T6).
    var name by remember(id) { mutableStateOf(config?.name.orEmpty()) }
    var address by remember(id) { mutableStateOf(config?.baseUrl.orEmpty()) }
    var defaultModel by remember(id) { mutableStateOf(config?.defaultModel.orEmpty()) }
    // The saved key is never read back, so the field opens empty even for a provider that already has one.
    var key by remember(id) { mutableStateOf("") }
    var keyState by remember(id) { mutableStateOf(config?.let { model.providerKeyState(it.id) }) }
    var addressError by remember(id) { mutableStateOf(false) }
    var saveError by remember(id) { mutableStateOf(false) }
    var askRemove by remember(id) { mutableStateOf(false) }

    // The address is checked by the same rule the backend uses, before anything is written; a refused save is said too.
    fun save() {
        addressError = false
        saveError = false
        val provider = ProviderConfig(
            id = id ?: idOf(name),
            name = name.trim().ifBlank { hostOf(address).ifBlank { idOf(name) } },
            baseUrl = address,
            defaultModel = defaultModel.trim(),
            models = config?.models.orEmpty(),
        )
        val valid = provider.validated()
        if (valid == null) {
            addressError = true
            return
        }
        if (!model.saveProvider(valid)) {
            saveError = true
            return
        }
        // Only now, with the provider saved, the key goes to the Keystore; the field gives it back at once.
        if (key.isNotBlank() && !model.setProviderKey(valid.id, key)) {
            saveError = true
            return
        }
        key = ""
        onDone()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter)
            .padding(bottom = Gutter),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        ScreenTitle(stringResource(if (config == null) R.string.providers_add else R.string.providers_edit_title), onDone)

        if (config == null) {
            Text(
                stringResource(R.string.providers_preset_hint), Modifier.padding(horizontal = 4.dp),
                style = TypeScale.SECONDARY.style(t.textDim.c()),
            )
            // A preset is a suggestion the user copies into his own provider; «Otro» is an OpenAI-compatible one he types.
            for (preset in ProviderPresets.ALL) {
                DescribedPill(
                    preset.name, stringResource(R.string.providers_preset_use, preset.name),
                    {
                        name = preset.name
                        address = preset.baseUrl
                        defaultModel = preset.defaultModel
                        addressError = false
                        saveError = false
                    },
                    Modifier.fillMaxWidth(),
                )
            }
            DescribedPill(
                stringResource(R.string.providers_preset_other), stringResource(R.string.providers_preset_other),
                {
                    name = ""
                    address = ""
                    defaultModel = ""
                    addressError = false
                    saveError = false
                },
                Modifier.fillMaxWidth(),
            )
        }

        DescribedField(
            value = name, onValueChange = { name = it; saveError = false },
            label = stringResource(R.string.providers_name), description = stringResource(R.string.providers_name),
        )
        DescribedField(
            value = address,
            onValueChange = { address = it; addressError = false; saveError = false },
            label = stringResource(R.string.providers_address), description = stringResource(R.string.providers_address),
        )
        if (addressError) Text(stringResource(R.string.providers_url_error), style = TypeScale.SECONDARY.style(t.danger.c()))
        DescribedField(
            value = defaultModel, onValueChange = { defaultModel = it },
            label = stringResource(R.string.providers_model), description = stringResource(R.string.providers_model),
        )

        SectionHeading(stringResource(R.string.providers_key))
        Panel {
            Column(Modifier.padding(Spacing.INSIDE.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                keyState?.let { Chip(keyLabel(it)) }
                if (keyState == ProviderKeyState.LOST) {
                    Text(stringResource(R.string.providers_key_lost_body), style = TypeScale.SECONDARY.style(t.danger.c()))
                }
                Text(stringResource(R.string.providers_key_hint), style = TypeScale.SECONDARY.style(t.textDim.c()))
                DescribedField(
                    value = key, onValueChange = { key = it },
                    label = stringResource(R.string.providers_key), description = stringResource(R.string.providers_key),
                    transformation = PasswordVisualTransformation(),
                    // A key is not a sentence: no autocorrect, no suggestions, and the characters are dots.
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                if (config != null) {
                    DescribedPill(
                        stringResource(R.string.providers_key_replace), stringResource(R.string.providers_key_replace),
                        {
                            if (key.isNotBlank() && model.setProviderKey(config.id, key)) {
                                keyState = ProviderKeyState.PRESENT
                                key = ""
                            } else if (key.isNotBlank()) {
                                saveError = true
                            }
                        },
                        Modifier.fillMaxWidth(), PillKind.SECONDARY, enabled = key.isNotBlank(),
                    )
                    DescribedPill(
                        stringResource(R.string.providers_key_clear), stringResource(R.string.providers_key_clear),
                        {
                            model.clearProviderKey(config.id)
                            keyState = model.providerKeyState(config.id)
                            key = ""
                        },
                        Modifier.fillMaxWidth(), PillKind.DANGER,
                    )
                }
            }
        }

        DescribedPill(
            stringResource(R.string.providers_save), stringResource(R.string.providers_save), { save() },
            Modifier.fillMaxWidth(), PillKind.PRIMARY,
        )
        if (saveError) Text(stringResource(R.string.providers_save_error), style = TypeScale.SECONDARY.style(t.danger.c()))
        if (config != null) {
            DescribedPill(
                stringResource(R.string.providers_remove), stringResource(R.string.providers_remove), { askRemove = true },
                Modifier.fillMaxWidth(), PillKind.DANGER,
            )
        }
    }

    if (askRemove && config != null) {
        AlertDialog(
            onDismissRequest = { askRemove = false },
            title = { Text(stringResource(R.string.providers_remove_title)) },
            text = { Text(stringResource(R.string.providers_remove_body, config.name)) },
            confirmButton = {
                DescribedTextAction(stringResource(R.string.providers_remove_confirm), stringResource(R.string.providers_remove_confirm), danger = true) {
                    askRemove = false
                    model.removeProvider(config.id)
                    onDone()
                }
            },
            dismissButton = {
                DescribedTextAction(stringResource(R.string.action_cancel), stringResource(R.string.action_cancel)) { askRemove = false }
            },
        )
    }
}

// ── Small pieces, shared by both parts ──────────────────────────────────────────────────────────

/**
 * The key state in the app's words: saved, none, or one that cannot be read any more (a reset Keystore), which the user
 * has to load again. Never the key itself.
 */
@Composable
private fun keyLabel(state: ProviderKeyState): String = stringResource(
    when (state) {
        ProviderKeyState.PRESENT -> R.string.providers_key_present
        ProviderKeyState.ABSENT -> R.string.providers_key_absent
        ProviderKeyState.LOST -> R.string.providers_key_lost
    },
)

/**
 * Only the host of the provider's address: the path is the app's business and never the user's to read back. An address
 * that cannot be parsed has nothing to show.
 */
private fun hostOf(baseUrl: String): String = try {
    java.net.URI(baseUrl.trim()).host.orEmpty()
} catch (_: java.net.URISyntaxException) {
    ""
}

/** An id both the settings and the Keystore accept ([A-Za-z0-9_-]), made from the name the user typed. */
private fun idOf(name: String): String {
    val slug = name.trim().lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("")
    return slug.trim('-').take(ProviderSettings.ID_MAX).ifBlank { "provider" }
}

// Every control on this screen goes through one of these four, so none can reach the user without a description.

/** A pill that also tells TalkBack what it does. */
@Composable
private fun DescribedPill(
    text: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    kind: PillKind = PillKind.SECONDARY, enabled: Boolean = true,
) {
    PillButton(text, onClick, modifier.semantics { contentDescription = description }, kind = kind, enabled = enabled)
}

/** A row of a panel that says what tapping it does. */
@Composable
private fun DescribedRow(
    title: String, hint: String?, description: String, onClick: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    ListRow(title, Modifier.semantics { contentDescription = description }, hint = hint, onClick = onClick, trailing = trailing)
}

/** A text field with its own name, for TalkBack and for the eye. */
@Composable
private fun DescribedField(
    value: String, onValueChange: (String) -> Unit, label: String, description: String,
    transformation: VisualTransformation = VisualTransformation.None,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
) {
    val t = tokens()
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        label = { Text(label) }, singleLine = true, visualTransformation = transformation, keyboardOptions = keyboard,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = t.panel.c(), unfocusedContainerColor = t.panel.c(),
            focusedBorderColor = t.textDim.c(), unfocusedBorderColor = t.line.c(),
            focusedTextColor = t.text.c(), unfocusedTextColor = t.text.c(), cursorColor = t.text.c(),
        ),
    )
}

/** A dialog button, which is a text button in this app (the dialogs of Clear chat). */
@Composable
private fun DescribedTextAction(text: String, description: String, danger: Boolean = false, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = Spacing.MIN_TOUCH.dp).semantics { contentDescription = description },
    ) {
        Text(text, color = if (danger) MaterialTheme.colorScheme.error else LocalContentColor.current)
    }
}
