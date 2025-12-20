package com.yepgoryo.CaptureCap

interface Encoder {
    interface Callback {
        fun onError(encoder: Encoder, exc: Exception)
    }
}
