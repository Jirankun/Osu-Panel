/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.settings.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.theme.osuPink
import java.util.Locale

/**
 * Language picker screen.
 *
 * - Android 13+ (API 33): opens the system per-app language settings.
 * - Pre-Android 13: shows an in-app picker that uses AppCompatDelegate.
 *
 * Selected language is persisted in SharedPreferences so it survives restarts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerScreen(
    onBack: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("language_prefs", Context.MODE_PRIVATE) }

    // Read persisted tag (default: "system")
    val persistedTag = remember { prefs.getString("app_language", "system") ?: "system" }
    var selectedTag by remember { mutableStateOf(persistedTag) }

    // Available languages
    data class LangOption(val tag: String, val displayName: String, val nativeName: String)

    val languages = listOf(
        LangOption("system", stringResource(R.string.settings_language_system), ""),
        LangOption("en", stringResource(R.string.settings_language_english), "English"),
        LangOption("ja", stringResource(R.string.settings_language_japanese), "日本語"),
        LangOption("id", stringResource(R.string.settings_language_indonesian), "Bahasa Indonesia"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
    ) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.settings_language),
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.beatmap_back),
                        tint = colorScheme.onBackground,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.surface,
            ),
        )

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            // Hint
            Text(
                stringResource(R.string.settings_language_restart),
                fontSize = 12.sp,
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 16.dp),
            )

            languages.forEach { lang ->
                val isSelected = selectedTag == lang.tag
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        .clickable {
                            if (lang.tag != selectedTag) {
                                selectedTag = lang.tag
                                // Persist selection
                                prefs.edit().putString("app_language", lang.tag).apply()
                                // Apply locale
                                applyLanguage(lang.tag)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            lang.displayName,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                        )
                        if (lang.nativeName.isNotEmpty() && lang.nativeName != lang.displayName) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                lang.nativeName,
                                fontSize = 12.sp,
                                color = colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = osuPink(context),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Apply the selected language using AppCompatDelegate.
 * "system" = follow device locale, otherwise set the explicit locale.
 * The locale is persisted via SharedPreferences in the caller.
 */
private fun applyLanguage(tag: String) {
    val locale = if (tag == "system") {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.create(Locale(tag))
    }
    AppCompatDelegate.setApplicationLocales(locale)
}
