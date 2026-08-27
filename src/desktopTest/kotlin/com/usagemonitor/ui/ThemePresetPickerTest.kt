package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.components.THEME_PRESET_TEST_TAG_PREFIX
import com.usagemonitor.presentation.ui.components.ThemePresetPicker
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ThemePresetPickerTest {

    @Test
    fun `picker renders all sixteen presets`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(preset = AppThemePreset.OBSIDIANA_DARK) {
                Box(modifier = Modifier.width(700.dp).height(560.dp)) {
                    ThemePresetPicker(
                        selected = AppThemePreset.OBSIDIANA_DARK,
                        onSelect = {}
                    )
                }
            }
        }

        for (preset in AppThemePreset.entries) {
            onNodeWithTag(THEME_PRESET_TEST_TAG_PREFIX + preset.name).assertIsDisplayed()
        }
    }

    @Test
    fun `picker wraps on a narrow settings surface`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(preset = AppThemePreset.PORCELANA_LIGHT) {
                Box(modifier = Modifier.width(300.dp).height(1_200.dp)) {
                    ThemePresetPicker(
                        selected = AppThemePreset.PORCELANA_LIGHT,
                        onSelect = {}
                    )
                }
            }
        }

        for (preset in AppThemePreset.entries) {
            onNodeWithTag(THEME_PRESET_TEST_TAG_PREFIX + preset.name).assertIsDisplayed()
        }
    }

    @Test
    fun `picker reports selected preset and changes selection`() = runDesktopComposeUiTest {
        var selected by mutableStateOf(AppThemePreset.OBSIDIANA_DARK)
        var callbackValue: AppThemePreset? = null

        setContent {
            AppTheme(preset = selected) {
                ThemePresetPicker(
                    selected = selected,
                    onSelect = {
                        callbackValue = it
                        selected = it
                    }
                )
            }
        }

        onNodeWithTag(THEME_PRESET_TEST_TAG_PREFIX + AppThemePreset.OBSIDIANA_DARK.name)
            .assertIsSelected()
        onNodeWithTag(THEME_PRESET_TEST_TAG_PREFIX + AppThemePreset.GELO_LIGHT.name)
            .performClick()
        onNodeWithTag(THEME_PRESET_TEST_TAG_PREFIX + AppThemePreset.GELO_LIGHT.name)
            .assertIsSelected()
        assertEquals(AppThemePreset.GELO_LIGHT, callbackValue)
    }
}
