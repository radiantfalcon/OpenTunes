package com.opentunes.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PixelLogo(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    // 7x5 pixel matrix for each letter in "opentunes"
    val letters = listOf(
        // 'o'
        listOf(
            " 111 ",
            "1   1",
            "1   1",
            "1   1",
            " 111 "
        ),
        // 'p'
        listOf(
            "1111 ",
            "1   1",
            "1111 ",
            "1    ",
            "1    "
        ),
        // 'e'
        listOf(
            " 111 ",
            "1   1",
            "1111 ",
            "1    ",
            " 111 "
        ),
        // 'n'
        listOf(
            "1111 ",
            "1   1",
            "1   1",
            "1   1",
            "1   1"
        ),
        // 't'
        listOf(
            " 111 ",
            "  1  ",
            "  1  ",
            "  1  ",
            "  11 "
        ),
        // 'u'
        listOf(
            "1   1",
            "1   1",
            "1   1",
            "1   1",
            " 111 "
        ),
        // 'n'
        listOf(
            "1111 ",
            "1   1",
            "1   1",
            "1   1",
            "1   1"
        ),
        // 'e'
        listOf(
            " 111 ",
            "1   1",
            "1111 ",
            "1    ",
            " 111 "
        ),
        // 's'
        listOf(
            " 1111",
            "1    ",
            " 111 ",
            "    1",
            "1111 "
        )
    )

    Box(
        modifier = modifier.height(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val totalLetters = letters.size
            val letterWidth = 5
            val letterHeight = 5
            val spacing = 2
            val totalCols = totalLetters * letterWidth + (totalLetters - 1) * spacing
            val pixelSize = (size.width * 0.85f / totalCols).coerceIn(4f, 10f)

            val startX = (size.width - (totalCols * pixelSize)) / 2f
            val startY = (size.height - (letterHeight * pixelSize)) / 2f

            var currentX = startX
            for (letter in letters) {
                for (row in 0 until letterHeight) {
                    val line = letter.getOrNull(row) ?: ""
                    for (col in 0 until letterWidth) {
                        if (col < line.length && line[col] == '1') {
                            drawRect(
                                color = color,
                                topLeft = Offset(
                                    currentX + col * pixelSize,
                                    startY + row * pixelSize
                                ),
                                size = Size(pixelSize * 0.92f, pixelSize * 0.92f)
                            )
                        }
                    }
                }
                currentX += (letterWidth + spacing) * pixelSize
            }
        }
    }
}
