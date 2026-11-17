package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.graphics.Color

/**
 * Cactus and fern growth stages, drawn on the same 25 px grid as the original sprout vectors
 * (one pixel = 296 / 25 viewport units), bottom-centered on the stem.
 */
object PlantSprites {
    private val cactusPalette = mapOf(
        'D' to Color(0xFF2E7D4F),
        'M' to Color(0xFF45A36A),
        'L' to Color(0xFF7FD18F),
        's' to Color(0xFFF4F1D8),
        'f' to Color(0xFFFF6FA8),
        'Y' to Color(0xFFFFD84D),
    )

    private val fernPalette = mapOf(
        'a' to Color(0xFF009705),
        'b' to Color(0xFF54C30A),
        'c' to Color(0xFF84D200),
        'd' to Color(0xFF419E03),
    )

    val cactus: List<PixelSprite> = listOf(
        PixelSprite(
            listOf(
                "..DDD..",
                ".DLMMD.",
                "DLMsMMD",
                "DMMMsMD",
                "DMsMMMD",
                ".DDDDD.",
            ),
            cactusPalette,
        ),
        PixelSprite(
            listOf(
                "..DDD..",
                ".DLMMD.",
                ".DLsMD.",
                ".DLMMD.",
                ".DMMsD.",
                ".DLMMD.",
                "sDsMMDs",
                ".DLMMD.",
                ".DMMsD.",
                ".DLMMD.",
                "..DDD..",
            ),
            cactusPalette,
        ),
        PixelSprite(
            listOf(
                "......fYf......",
                ".......f.......",
                "......DDD......",
                ".....DLMMD.....",
                ".....DLsMD.....",
                "..DD.DLMMD.....",
                ".DLMDDLMsD.DD..",
                ".DLsMMLMMDDLMD.",
                ".DLMMMLMMMMsMD.",
                "..DDDDLsMMMMDD.",
                ".....DLMMDDDD..",
                ".....DLMsD.....",
                ".....DLMMD.....",
                ".....DLMMD.....",
                ".....DsMMD.....",
                "......DDD......",
            ),
            cactusPalette,
        ),
    )

    val fern: List<PixelSprite> = listOf(
        PixelSprite(
            listOf(
                "..b...b..",
                ".bc.c.cb.",
                "..bcccb..",
                "...bcb...",
                "....d....",
                "....d....",
            ),
            fernPalette,
        ),
        PixelSprite(
            listOf(
                "..bb.......bb..",
                ".bccb.....bccb.",
                "..bccb...bccb..",
                "...bccb.bccb...",
                ".bb.bccbccb.bb.",
                "bccb.bcccb.bccb",
                ".bccbbcccbbccb.",
                "...bbbcdcbbb...",
                "......ddd......",
                ".......d.......",
                ".......d.......",
            ),
            fernPalette,
        ),
        PixelSprite(
            listOf(
                "....bb.........bb....",
                "...bccb.......bccb...",
                "..bcccb..b.b..bcccb..",
                "..bccb..bcbcb..bccb..",
                "...bccb.bcccb.bccb...",
                ".bb.bccbbcccbbccb.bb.",
                "bccb.bccbcccbccb.bccb",
                "bcccb.bccbcbccb.bcccb",
                ".bcccbbbccccccbbbccb.",
                "..bbccccbcdcbccccbb..",
                "....bbbbbbdbbbbbb....",
                "..........d..........",
                "..........d..........",
                "..........d..........",
            ),
            fernPalette,
        ),
    )
}
