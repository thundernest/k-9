package com.fsck.k9.ui

import android.content.res.Resources.Theme
import android.util.TypedValue

fun Theme.resolveColorAttribute(attrId: Int): Int {
    val typedValue = TypedValue()

    val found = resolveAttribute(attrId, typedValue, true)
    if (!found) {
        throw IllegalStateException("Couldn't resolve attribute ($attrId)")
    }

    return typedValue.data
}

fun Theme.getIntArray(attrId: Int): IntArray {
    val typedValue = TypedValue()

    val found = resolveAttribute(attrId, typedValue, true)
    if (!found) {
        throw IllegalStateException("Couldn't resolve attribute ($attrId)")
    }

    return resources.getIntArray(typedValue.resourceId)
}
