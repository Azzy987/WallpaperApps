package com.droidates.wallpapers.viewmodel

import androidx.lifecycle.ViewModel
import com.google.android.gms.ads.nativead.NativeAd
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AdViewModel @Inject constructor() : ViewModel() {
    private val _nativeAds = mutableMapOf<Int, NativeAd>()
    
    fun getAd(position: Int): NativeAd? = _nativeAds[position]
    
    fun setAd(position: Int, ad: NativeAd) {
        _nativeAds[position]?.destroy()
        _nativeAds[position] = ad
    }
    
    override fun onCleared() {
        super.onCleared()
        _nativeAds.values.forEach { it.destroy() }
        _nativeAds.clear()
    }
} 