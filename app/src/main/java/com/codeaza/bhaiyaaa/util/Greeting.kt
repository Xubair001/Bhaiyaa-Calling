package com.codeaza.bhaiyaaa.util

import java.util.Calendar

fun timeOfDayGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning, boss \uD83D\uDC4B"
        hour < 17 -> "What's up, bhai?"
        else -> "Good evening, bhai \uD83C\uDF19"
    }
}
