package com.example.farmapp.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.farmapp.data.model.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class CropSavePresentationTest {
    @get:Rule val compose = createComposeRule()
    private val product = FarmerProductResponse("p", "Avocado", "A long crop description. ".repeat(30), "pieces",
        BigDecimal("100"), BigDecimal("5"), categoryId = "category", categoryName = "Backend category")
    private val categories = CategoryCatalog(listOf(CategoryResponse("category", "Backend category", "FARM_PRODUCE", true)), loaded = true)

    @Test fun saveErrorStaysVisibleBesideSaveEvenWhenFormIsScrolled() {
        val state = mutableStateOf(CropSaveState())
        val message = "Could not save changes. Update crop failed (HTTP 500). Your edits are kept; you can retry."
        compose.setContent {
            MaterialTheme {
                CropDetailScreen(product, Modifier.fillMaxSize(), state.value.saving, null, 0, {}, {},
                    onSave = { state.value = CropSaveState(message = message, error = true) }, onRemove = {},
                    categories = categories, saveState = state.value)
            }
        }
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithText("Description").performScrollTo()
        compose.onNodeWithText("Save changes").assertIsDisplayed().performClick()
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithText("Save changes").assertIsEnabled()
    }
    @Test fun confirmedRefreshExitsEditAndShowsSuccess() {
        val state = mutableStateOf(CropSaveState())
        compose.setContent {
            MaterialTheme {
                CropDetailScreen(product, Modifier.fillMaxSize(), false, null, 0, {}, {},
                    onSave = { state.value = CropSaveState(message = "Crop updated successfully.", completedSaves = 1) },
                    onRemove = {}, categories = categories, saveState = state.value)
            }
        }
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.onNodeWithText("Save changes").assertDoesNotExist()
        compose.onNodeWithText("Crop updated successfully.").assertIsDisplayed()
    }
}
