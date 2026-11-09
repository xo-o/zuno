package com.talkie.ai.viewmodel

/** Glyph shown in a toast; mapped to a drawable by the UI. */
enum class ToastIcon { Check, Pin, Trash, SignOut, Link, Alert, Calendar, Bell }

/** A pill toast for the app-level host, so it survives navigation (e.g. Delete → Home). */
data class ToastEvent(
    val message: String,
    val icon: ToastIcon = ToastIcon.Check,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    /** Shown under the status bar instead of above the composer (e.g. "You've signed out"). */
    val atTop: Boolean = false,
    val id: Long = System.nanoTime(),
)
