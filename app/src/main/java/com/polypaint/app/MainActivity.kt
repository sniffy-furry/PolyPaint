package com.polypaint.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.polypaint.app.ui.PaintScreen
import com.polypaint.app.ui.theme.PolyPaintTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PolyPaintTheme {
                PaintScreen()
            }
        }
    }
}
