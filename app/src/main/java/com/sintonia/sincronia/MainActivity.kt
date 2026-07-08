package com.sintonia.sincronia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sintonia.sincronia.navigation.SintoniaSincroniaApp
import com.sintonia.sincronia.ui.theme.SintoniaSincroniaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SintoniaSincroniaTheme {
                SintoniaSincroniaApp()
            }
        }
    }
}
