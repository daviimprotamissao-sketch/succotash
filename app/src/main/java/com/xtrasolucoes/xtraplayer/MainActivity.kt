// xTra Player - Autoria: xTra Soluções (todos os direitos reservados)
package com.xtrasolucoes.xtraplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent {
            val typo = remember { appTypography(buildAppFont(this@MainActivity)) }
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Color(0xFFE50914), background = Color(0xFF0E0E10), surface = Color(0xFF0E0E10)),
                typography = typo
            ) {
                Surface(Modifier.fillMaxSize()) {
                    val vm: AppVM = viewModel()
                    Root(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    // Se a pessoa arrastar a barra para fora (gesto de revelar), escondemos de novo
    // assim que a janela recupera o foco — mantendo a tela cheia "imersiva".
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Esconde a barra de status e a barra de navegação/gestos, deixando o app em tela cheia. */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
