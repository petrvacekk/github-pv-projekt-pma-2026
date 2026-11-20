package com.example.ownly

import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ownly.data.AppDatabase
import com.example.ownly.data.ClothingItem
import com.example.ownly.databinding.DialogClothingItemBinding
import com.example.ownly.databinding.FragmentWardrobeBinding
import com.example.ownly.databinding.ItemClothingBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigDecimal

class WardrobeFragment : Fragment(R.layout.fragment_wardrobe) {
    @StringRes
    private var selectedCategory = R.string.my_wardrobe
    private val clothingDao by lazy { AppDatabase.getDatabase(requireContext()).clothingDao() }
    private var itemsJob: Job? = null
    private var activeDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentWardrobeBinding.bind(view)
        selectedCategory = savedInstanceState?.getInt(SELECTED_CATEGORY, R.string.my_wardrobe)
            ?: R.string.my_wardrobe

        val backCallback = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            selectedCategory = R.string.my_wardrobe
            showSelection(binding)
            isEnabled = false
        }

        val categories = listOf(
            binding.categoryTShirts to R.string.category_t_shirts,
            binding.categoryPants to R.string.category_pants,
            binding.categoryHoodies to R.string.category_hoodies,
            binding.categoryJackets to R.string.category_jackets,
            binding.categoryShoes to R.string.category_shoes,
            binding.categoryAccessories to R.string.category_accessories
        )
        for ((button, title) in categories) {
            button.setOnClickListener {
                selectedCategory = title
                showSelection(binding)
                backCallback.isEnabled = true
            }
        }
        binding.backToCategories.setOnClickListener {
            backCallback.handleOnBackPressed()
        }

        binding.addItem.setOnClickListener { showItemForm(categoryKey(), item = null) }
        showSelection(binding)
        backCallback.isEnabled = selectedCategory != R.string.my_wardrobe
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SELECTED_CATEGORY, selectedCategory)
    }

    private fun showSelection(binding: FragmentWardrobeBinding) {
        val showingCategory = selectedCategory != R.string.my_wardrobe
        binding.wardrobeTitle.setText(selectedCategory)
        binding.categoryGrid.isVisible = !showingCategory
        binding.categoryDetail.isVisible = showingCategory
        itemsJob?.cancel()
        binding.clothingList.removeAllViews()
        binding.emptyItems.isVisible = false
        if (showingCategory) {
            val category = categoryKey()
            itemsJob = viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    clothingDao.getItemsByCategory(category).collect { items ->
                        binding.emptyItems.isVisible = items.isEmpty()
                        binding.clothingList.removeAllViews()
                        for (item in items) {
                            val row = ItemClothingBinding.inflate(layoutInflater, binding.clothingList, false)
                            row.root.text = item.name
                            row.root.setOnClickListener { showItemForm(category, item) }
                            binding.clothingList.addView(row.root)
                        }
                    }
                }
            }
        }
    }

    // The resource name is a stable key across display languages.
    private fun categoryKey(): String = resources.getResourceEntryName(selectedCategory)

    private fun showItemForm(category: String, item: ClothingItem?) {
        val form = DialogClothingItemBinding.inflate(layoutInflater)
        if (item != null) {
            form.itemName.setText(item.name)
            form.itemDescription.setText(item.description)
            form.itemPrice.setText(BigDecimal.valueOf(item.purchasePriceInMinorUnits, 2).toPlainString())
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (item == null) R.string.add_item else R.string.edit_item)
            .setView(form.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save_item, null)
        if (item != null) {
            builder.setNeutralButton(R.string.delete_item) { _, _ -> confirmDelete(item) }
        }
        val dialog = builder.show()
        trackDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = form.itemName.text.toString().trim()
            val price = parsePrice(form.itemPrice.text.toString())
            form.itemName.error = if (name.isBlank()) getString(R.string.item_name_required) else null
            form.itemPrice.error = if (price == null) getString(R.string.invalid_item_price) else null
            if (name.isBlank() || price == null) return@setOnClickListener

            val editedItem = ClothingItem(
                id = item?.id ?: 0L,
                name = name,
                category = category,
                description = form.itemDescription.text.toString().trim(),
                purchasePriceInMinorUnits = price
            )
            writeItem(dialog) {
                if (item == null) clothingDao.insert(editedItem) else clothingDao.update(editedItem)
            }
        }
    }

    private fun parsePrice(input: String): Long? {
        val price = input.trim().replace(',', '.')
        if (price.isEmpty()) return 0L
        if (!PRICE_PATTERN.matches(price)) return null
        return try {
            price.toBigDecimal().movePointRight(2).longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun confirmDelete(item: ClothingItem) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_item)
            .setMessage(getString(R.string.confirm_delete_item, item.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_item, null)
            .show()
        trackDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            writeItem(dialog) { clothingDao.delete(item) }
        }
    }

    private fun trackDialog(dialog: AlertDialog) {
        activeDialog = dialog
        dialog.setOnDismissListener {
            if (activeDialog === dialog) activeDialog = null
        }
    }

    private fun writeItem(dialog: AlertDialog, operation: suspend () -> Unit) {
        val buttons = listOf(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        )
        buttons.forEach { it?.isEnabled = false }
        dialog.setCancelable(false)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                operation()
                dialog.dismiss()
            } catch (_: SQLiteException) {
                Toast.makeText(requireContext(), R.string.item_write_failed, Toast.LENGTH_SHORT).show()
                buttons.forEach { it?.isEnabled = true }
                dialog.setCancelable(true)
            }
        }
    }

    override fun onDestroyView() {
        activeDialog?.dismiss()
        activeDialog = null
        itemsJob = null
        super.onDestroyView()
    }

    private companion object {
        const val SELECTED_CATEGORY = "selected_category"
        val PRICE_PATTERN = Regex("[0-9]+(?:[.][0-9]{1,2})?")
    }
}
