package com.cadence.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Thin outline icons for the Minimal theme, drawn on a 24×24 grid.
 * They are white strokes; `Icon(tint = …)` recolours them.
 */
object LineIcons {
    private fun line(name: String, vararg paths: String, stroke: Float = 1.5f, filled: Boolean = false): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = addPathNodes(d),
                fill = if (filled) SolidColor(Color.White) else null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    // Tabs
    val Queue = line("Queue", "M3 6h12M3 12h12M3 18h7M18.5 18V9l3-1", "M13.5 18a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0")
    val NowPlaying = line("NowPlaying", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M10 8.5v7l6-3.5z")
    val Folder = line("Folder", "M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val Album = line("Album", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0")
    val Artist = line("Artist", "M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M4 21a8 8 0 0 1 16 0")
    val Genre = line("Genre", "M3 12V4a1 1 0 0 1 1-1h8l9 9-9 9z", "M6 7.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0")
    val Playlists = line(
        "Playlists",
        "M9 3h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        "M3 7v12a2 2 0 0 0 2 2h12",
        "M15 6v6.5",
        "M11 12.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    )
    val Radio = line(
        "Radio",
        "M4 9h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z",
        "M7 9l10-5",
        "M13 14.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
        "M6 13h4M6 16h4",
    )
    val Search = line("Search", "M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M20 20l-3.5-3.5")
    val MoreVertical = line("MoreVertical", "M12 5h.01M12 12h.01M12 19h.01", stroke = 2.6f)
    val MoreHorizontal = line("MoreHorizontal", "M5 12h.01M12 12h.01M19 12h.01", stroke = 2.6f)

    // Player
    val Heart = line("Heart", "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z")
    val HeartFilled = line("HeartFilled", "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z", filled = true)
    val Info = line("Info", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 11v5", "M12 8h.01")
    val PlaylistAdd = line("PlaylistAdd", "M3 6h12M3 12h12M3 18h8", "M18 14v8M14 18h8")
    val Repeat = line("Repeat", "M17 1l4 4-4 4", "M3 11V9a4 4 0 0 1 4-4h14", "M7 23l-4-4 4-4", "M21 13v2a4 4 0 0 1-4 4H3")
    val RepeatOne = line("RepeatOne", "M17 1l4 4-4 4", "M3 11V9a4 4 0 0 1 4-4h14", "M7 23l-4-4 4-4", "M21 13v2a4 4 0 0 1-4 4H3", "M11 10.5l1.5-1v5")
    val Shuffle = line("Shuffle", "M16 3h5v5", "M4 20L21 3", "M21 16v5h-5", "M15 15l6 6", "M4 4l5 5")
    val Volume = line("Volume", "M4 9v6h4l5 4V5L8 9z", "M16 9a4 4 0 0 1 0 6")
    val Previous = line("Previous", "M19 5v14L9 12z", "M6 5v14")
    val Next = line("Next", "M5 5v14l10-7z", "M18 5v14")
    val Play = line("Play", "M8 5v14l11-7z")
    val Pause = line("Pause", "M9 5v14", "M15 5v14", stroke = 1.6f)
    val Stop = line("Stop", "M7 6h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z")
    val Equalizer = line("Equalizer", "M6 20v-6", "M12 20V8", "M18 20v-9")
    val Visualizer = line("Visualizer", "M2 12h3l2-5 3 10 3-14 3 12 2-3h4")
    val Dj = line(
        "Dj",
        "M3 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
        "M13 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
        "M7 8h.01M17 8h.01",
        "M3 19h18",
        "M10 16.5v5",
    )
    val Sleep = line("Sleep", "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z")
}
