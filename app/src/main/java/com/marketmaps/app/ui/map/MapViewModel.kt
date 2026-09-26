package com.marketmaps.app.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marketmaps.app.data.AppPreferences
import com.marketmaps.app.data.Store
import com.marketmaps.app.data.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * حالة شاشة الخريطة — نواة للانتقال التدريجي من remember في MapScreen.
 * حالياً يُحمّل المحلات؛ يمكن توسيعه لاحقاً ليشمل البحث والفلاتر.
 */
class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = StoreRepository()
    private val preferences = AppPreferences(app)

    private val _stores = MutableStateFlow<List<Store>>(emptyList())
    val stores: StateFlow<List<Store>> = _stores.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    init {
        refreshStores()
    }

    fun refreshStores() {
        viewModelScope.launch {
            val result = repository.getAllStores()
            if (result.isSuccess) {
                _stores.value = result.getOrDefault(emptyList())
                _loadError.value = null
            } else {
                _loadError.value = result.exceptionOrNull()?.message ?: "فشل تحميل المحلات"
            }
        }
    }
}
