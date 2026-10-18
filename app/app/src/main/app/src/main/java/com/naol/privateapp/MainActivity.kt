package com.naol.privateapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

private lateinit var status: TextView

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    requestNotificationPermission()

    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(36, 48, 36, 32)
        setBackgroundColor(0xFF0B1020.toInt())
    }

    val title = TextView(this).apply {
        text = "NAOL PRIVATE"
        textSize = 30f
        setTextColor(0xFF65D6FF.toInt())
    }

    val subtitle = TextView(this).apply {
        text = "Personal Phone Protection"
        textSize = 16f
        setTextColor(0xFFE7ECF5.toInt())
        setPadding(0, 12, 0, 24)
    }

    status = TextView(this).apply {
        text = "Protection is OFF"
        textSize = 18f
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(0, 12, 0, 24)
    }

    val startButton = Button(this).apply {
        text = "START CHARGING GUARD"
        setOnClickListener {
            val serviceIntent = Intent(
                this@MainActivity,
                AlarmService::class.java
            ).setAction(AlarmService.ACTION_START)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            status.text = "Protection is ON"
            Toast.makeText(
                this@MainActivity,
                "Charging Guard Started",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val stopButton = Button(this).apply {
        text = "STOP PROTECTION"
        setOnClickListener {
            stopService(
                Intent(this@MainActivity, AlarmService::class.java)
            )
            status.text = "Protection is OFF"
            Toast.makeText(
                this@MainActivity,
                "Protection Stopped",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val information = TextView(this).apply {
        text = """
            
            FEATURES
            
            • Charger disconnect alarm
            • Movement alarm while charging
            • Persistent notification
            • Stop alarm action
            
            LIMITATIONS
            
            Failed lock-screen pattern detection
            and silent background selfies are not
            supported in this starter version.
        """.trimIndent()

        textSize = 14f
        setTextColor(0xFFCAD3E2.toInt())
        setPadding(0, 20, 0, 0)
    }

    layout.addView(title)
    layout.addView(subtitle)
    layout.addView(status)
    layout.addView(startButton)
    layout.addView(stopButton)
    layout.addView(information)

    setContentView(layout)
}

private fun requestNotificationPermission() {
    if (
        Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
    ) {
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            100
        )
    }
}

}
