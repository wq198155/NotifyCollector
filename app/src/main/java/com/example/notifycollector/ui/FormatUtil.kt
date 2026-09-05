package com.example.notifycollector.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmt(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
