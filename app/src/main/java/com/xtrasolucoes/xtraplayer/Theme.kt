// xTra Player - Autoria: xTra Soluções (todos os direitos reservados)
package com.xtrasolucoes.xtraplayer

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Fonte do app: Helvetica.
 *
 * A Helvetica é uma fonte comercial (não pode ser distribuída junto com o app sem licença),
 * por isso o app procura os arquivos da SUA cópia licenciada em app/src/main/assets/fonts/:
 *   Helvetica-Regular.ttf|otf, Helvetica-Bold.ttf|otf e (opcional) Helvetica-Light.ttf|otf.
 * Se os arquivos não existirem, usa Liberation Sans (licença OFL, métricas da Helvetica/Arial),
 * que tem praticamente o mesmo desenho.
 */
fun buildAppFont(ctx: Context): FontFamily {
    val am = ctx.assets
    val files: Set<String> = try { am.list("fonts")?.toSet() ?: emptySet() } catch (e: Exception) { emptySet() }
    fun find(base: String): String? = listOf("ttf", "otf").map { "$base.$it" }.firstOrNull { it in files }
    val reg = find("Helvetica-Regular")
    val bold = find("Helvetica-Bold")
    val light = find("Helvetica-Light")
    if (reg != null && bold != null) {
        try {
            val list = mutableListOf(
                Font(am, "fonts/$reg", FontWeight.Normal),
                Font(am, "fonts/$bold", FontWeight.Bold),
            )
            if (light != null) list.add(Font(am, "fonts/$light", FontWeight.Light))
            return FontFamily(list)
        } catch (e: Exception) {
            // arquivo inválido: cai no fallback abaixo
        }
    }
    return FontFamily(
        Font(R.font.sans_regular, FontWeight.Normal),
        Font(R.font.sans_bold, FontWeight.Bold),
    )
}

fun appTypography(f: FontFamily): Typography {
    val b = Typography()
    fun TextStyle.h() = copy(fontFamily = f)
    return Typography(
        displayLarge = b.displayLarge.h(), displayMedium = b.displayMedium.h(), displaySmall = b.displaySmall.h(),
        headlineLarge = b.headlineLarge.h(), headlineMedium = b.headlineMedium.h(), headlineSmall = b.headlineSmall.h(),
        titleLarge = b.titleLarge.h(), titleMedium = b.titleMedium.h(), titleSmall = b.titleSmall.h(),
        bodyLarge = b.bodyLarge.h(), bodyMedium = b.bodyMedium.h(), bodySmall = b.bodySmall.h(),
        labelLarge = b.labelLarge.h(), labelMedium = b.labelMedium.h(), labelSmall = b.labelSmall.h(),
    )
}
