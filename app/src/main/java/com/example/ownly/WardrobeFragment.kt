package com.example.ownly

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.ownly.databinding.FragmentWardrobeBinding

class WardrobeFragment : Fragment(R.layout.fragment_wardrobe) {
    @StringRes
    private var selectedCategory = R.string.my_wardrobe

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
    }

    private companion object {
        const val SELECTED_CATEGORY = "selected_category"
    }
}
