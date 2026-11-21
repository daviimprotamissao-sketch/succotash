
// xTra Player - Autoria: xTra Soluções (todos os direitos reservados)

package com.xtrasolucoes.xtraplayer

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Fonte do aplicativo: Helvetica.
 *
 * Procura fontes licenciadas em:
 * app/src/main/assets/fonts/
 *
 * Fallback: Liberation Sans.
 */
fun buildAppFont(ctx: Context): FontFamily {
    val am = ctx.assets

    val files: Set<String> = try {
        am.list("fonts")?.toSet() ?: emptySet()
    } catch (e: Exception) {
        emptySet()
    }

    fun find(base: String): String? {
        return listOf("ttf", "otf")
            .map { "$base.$it" }
            .firstOrNull { it in files }
    }

    val reg = find("Helvetica-Regular")
    val bold = find("Helvetica-Bold")
    val light = find("Helvetica-Light")

    if (reg != null && bold != null) {
        try {
            val fonts = mutableListOf(
                Font(
                    "fonts/$reg",
                    am,
                    FontWeight.Normal
                ),
                Font(
                    "fonts/$bold",
                    am,
                    FontWeight.Bold
                )
            )

            if (light != null) {
                fonts.add(
                    Font(
                        "fonts/$light",
                        am,
                        FontWeight.Light
                    )
                )
            }

            return FontFamily(fonts)

        } catch (e: Exception) {
            // Arquivos inválidos: utiliza o fallback.
        }
    }

    return FontFamily(
        Font(R.font.sans_regular, FontWeight.Normal),
        Font(R.font.sans_bold, FontWeight.Bold)
    )
}

fun appTypography(f: FontFamily): Typography {
    val base = Typography()

    fun TextStyle.withAppFont(): TextStyle {
        return copy(fontFamily = f)
    }

    return Typography(
        displayLarge = base.displayLarge.withAppFont(),
        displayMedium = base.displayMedium.withAppFont(),
        displaySmall = base.displaySmall.withAppFont(),

        headlineLarge = base.headlineLarge.withAppFont(),
        headlineMedium = base.headlineMedium.withAppFont(),
        headlineSmall = base.headlineSmall.withAppFont(),

        titleLarge = base.titleLarge.withAppFont(),
        titleMedium = base.titleMedium.withAppFont(),
        titleSmall = base.titleSmall.withAppFont(),

        bodyLarge = base.bodyLarge.withAppFont(),
        bodyMedium = base.bodyMedium.withAppFont(),
        bodySmall = base.bodySmall.withAppFont(),

        labelLarge = base.labelLarge.withAppFont(),
        labelMedium = base.labelMedium.withAppFont(),
        labelSmall = base.labelSmall.withAppFont()
    )
}
