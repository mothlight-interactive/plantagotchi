package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.mothlight.plantagotchi.data.state.Species
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * How the plant moves this frame. All distances are in plant pixels.
 *
 * @param sway sideways bend at the top (wind)
 * @param lean extra bend at the top (behaviors such as turning to the sun)
 * @param flap leaf angle: positive raises leaves, negative lowers them
 * @param stretch extra stem height
 * @param squash vertical scale offset, e.g. 0.1 = 10 % taller
 * @param droop 0 = healthy, 1 = withered: bends over, leaves hang
 * @param time seconds, for per-leaf flutter
 */
data class PlantPose(
    val sway: Float = 0f,
    val lean: Float = 0f,
    val flap: Float = 0f,
    val stretch: Float = 0f,
    val squash: Float = 0f,
    val droop: Float = 0f,
    val flutter: Float = 0.3f,
    val time: Float = 0f,
    /** -1 = sun on the left, 1 = on the right. */
    val sunDirection: Float = 0f,
    /** 0 = night, 1 = day (smoothed). */
    val daylight: Float = 1f,
    /** Flytrap jaws: 0 = open, 1 = shut. */
    val snap: Float = 0f,
    /** Flytrap: a fly buzzes around. */
    val fly: Boolean = false,
)

/**
 * Draws plants procedurally on a pixel grid instead of using fixed images, so they can grow
 * continuously and every part can move. Positions are rounded to whole pixels to keep the
 * pixel art look.
 */
object PlantRenderer {
    // Sprout palette (same greens as the original vector art)
    private val dark = Color(0xFF009705)
    private val mid = Color(0xFF54C30A)
    private val light = Color(0xFF84D200)
    private val stemColor = Color(0xFF419E03)
    private val petal = Color(0xFFFFF4F8)
    private val pollen = Color(0xFFFFD84D)

    // Cactus
    private val cOutline = Color(0xFF2E7D4F)
    private val cMid = Color(0xFF45A36A)
    private val cLight = Color(0xFF7FD18F)
    private val spine = Color(0xFFF4F1D8)
    private val bloom = Color(0xFFFF6FA8)

    /** Grid height a fully grown plant may use. */
    const val GRID_HEIGHT = 25

    /**
     * Draws a plant with its stem base centered at [base] (bottom edge of the bottom pixel row).
     * [maturity] goes from 0 (seedling) to 1 (fully grown); [seed] varies the shape per plant.
     */
    fun draw(
        scope: DrawScope,
        species: Species,
        maturity: Float,
        seed: Int,
        pose: PlantPose,
        base: Offset,
        pixel: Float,
    ) {
        val canvas = PixelGrid(scope, base, pixel)
        val m = maturity.coerceIn(0f, 1f)
        when (species) {
            Species.SPROUT -> drawSprout(canvas, m, seed, pose)
            Species.CACTUS -> drawCactus(canvas, m, seed, pose)
            Species.FERN -> drawFern(canvas, m, seed, pose)
            Species.SUNFLOWER -> drawSunflower(canvas, m, seed, pose)
            Species.TULIP -> drawTulip(canvas, m, seed, pose)
            Species.MONSTERA -> drawMonstera(canvas, m, seed, pose)
            Species.BONSAI -> drawBonsai(canvas, m, seed, pose)
            Species.FLYTRAP -> drawFlytrap(canvas, m, seed, pose)
        }
    }

    // region Sprout

    private val leafTemplates = listOf(
        listOf("cb"),
        listOf(".cb", "ab."),
        listOf("..bb", ".bcca", "abca."),
        listOf("...bba", ".bccca", "bcccca", "abccb.", ".aa..."),
    )

    private fun leafColor(c: Char) = when (c) {
        'a' -> dark
        'b' -> mid
        'c' -> light
        else -> null
    }

    private fun drawSprout(g: PixelGrid, m: Float, seed: Int, pose: PlantPose) {
        val random = Random(seed)
        val firstSide = if (random.nextBoolean()) 1 else -1
        val scaleY = 1f + pose.squash
        val height = ((3 + m * 15 + pose.stretch) * scaleY).coerceAtLeast(2f)
        val top = height.roundToInt()

        // Stem, bending more towards the top.
        val stemX = IntArray(top + 1) { y -> bendX(y.toFloat(), height, pose) }
        for (y in 0 until top) {
            val x = stemX[y]
            g.plot(x - 1, y, stemColor)
            g.plot(x, y, dark)
            if (m > 0.35f && y < 2) g.plot(x - 2, y, stemColor)
        }

        // Alternating leaves along the stem: lower ones are older and bigger.
        var node = 0
        var y = (2 * scaleY).roundToInt()
        val step = max(2, (3 * scaleY).roundToInt())
        while (y <= top - 3) {
            val side = if (node % 2 == 0) firstSide else -firstSide
            val age = ((height - y) / (6f * scaleY)).coerceIn(0f, 1f)
            val size = (age * (leafTemplates.size - 1)).roundToInt()
            val flutter = sin(pose.time * (2.6f + node * 0.37f) + node * 1.9f) * pose.flutter
            drawLeaf(g, stemX[y], y, side, size, pose.flap + flutter - pose.droop * 2.5f)
            node++
            y += step
        }

        // Crown: a pair of young leaves at the tip.
        val crownSize = when {
            m < 0.15f -> 1
            m < 0.5f -> 2
            else -> 2
        }
        val crownY = max(0, top - 1)
        val crownFlutter = sin(pose.time * 3.3f) * pose.flutter
        drawLeaf(g, stemX[crownY], crownY, -1, crownSize, pose.flap + 0.5f + crownFlutter - pose.droop * 2f)
        drawLeaf(g, stemX[crownY], crownY, 1, crownSize, pose.flap + 0.5f - crownFlutter - pose.droop * 2f)

        // A flower opens when the sprout is fully grown.
        if (m > 0.88f) {
            val open = ((m - 0.88f) / 0.12f).coerceIn(0f, 1f)
            val fx = stemX[crownY]
            val fy = top + 1
            g.plot(fx - 1, top, stemColor)
            if (open < 0.5f) {
                g.plot(fx - 1, fy, bloom)
            } else {
                g.plot(fx - 1, fy, pollen)
                g.plot(fx - 2, fy, petal)
                g.plot(fx, fy, petal)
                g.plot(fx - 1, fy + 1, petal)
                g.plot(fx - 1, fy - 1, petal)
            }
        }
    }

    /** Draws a leaf attached at the stem column [stemX], row [y], growing to [side] (-1 / 1). */
    private fun drawLeaf(g: PixelGrid, stemX: Int, y: Int, side: Int, size: Int, flap: Float) {
        val template = leafTemplates[size.coerceIn(0, leafTemplates.size - 1)]
        val width = template.maxOf { it.length }
        val rows = template.size
        template.forEachIndexed { row, line ->
            line.forEachIndexed { col, c ->
                val color = leafColor(c) ?: return@forEachIndexed
                // Shear: outer pixels move further when the leaf flaps.
                val lift = (flap * (col + 1) / width).roundToInt()
                val x = if (side > 0) stemX + 1 + col else stemX - 2 - col
                g.plot(x, y + (rows - 1 - row) + lift, color)
            }
        }
    }

    private fun bendX(y: Float, height: Float, pose: PlantPose): Int {
        val t = (y / height).coerceIn(0f, 1f)
        val bend = (pose.sway + pose.lean) * t.pow(1.6f) + pose.droop * 6f * t.pow(2f)
        return bend.roundToInt()
    }

    // endregion

    // region Cactus

    private fun drawCactus(g: PixelGrid, m: Float, seed: Int, pose: PlantPose) {
        val random = Random(seed)
        val scaleY = 1f + pose.squash * 0.6f
        val width = if (m < 0.3f) 5 else 7
        val half = width / 2
        val height = ((3 + m * 11 + pose.stretch * 0.5f) * scaleY).roundToInt().coerceAtLeast(3)
        // Cacti are stiff: only a little sway, more droop when withered.
        val stiff = pose.copy(sway = pose.sway * 0.35f, lean = pose.lean * 0.4f)

        fun column(y: Int) = bendX(y.toFloat(), height.toFloat(), stiff)

        for (y in 0 until height) {
            val cx = column(y)
            val rounded = y == height - 1
            for (dx in -half..half) {
                if (rounded && abs(dx) == half) continue
                val color = when {
                    abs(dx) == half || (rounded && abs(dx) == half - 1) -> cOutline
                    dx == -half + 1 -> cLight
                    else -> cMid
                }
                g.plot(cx + dx, y, color)
            }
        }
        // Spines in a stable pattern per plant
        val spineRows = (1 until height - 1).filter { (it + random.nextInt(2)) % 3 == 0 }
        spineRows.forEachIndexed { i, y ->
            val cx = column(y)
            val side = if (i % 2 == 0) -half - 1 else half + 1
            g.plot(cx + side, y, spine)
            if (width > 5 && y % 2 == 0) g.plot(cx, y, spine)
        }

        // Arms grow out sideways and then up; they wave a little.
        fun arm(side: Int, attachFraction: Float, start: Float, length: Int) {
            if (m <= start) return
            val grow = ((m - start) / 0.25f).coerceIn(0f, 1f)
            val attach = (height * attachFraction).roundToInt()
            val cx = column(attach)
            val out = 1 + (grow * 2).roundToInt()
            val up = (grow * length).roundToInt()
            val wave = (sin(pose.time * 1.7f + side) * pose.flutter * 1.5f + pose.flap * 0.6f - pose.droop * 3f).roundToInt()
            for (i in 1..out) {
                for (row in -1..0) g.plot(cx + side * (half + i), attach + row, if (row == 0) cOutline else cMid)
            }
            val ax = cx + side * (half + out)
            for (j in 0..up) {
                val shift = if (j > up / 2) wave.coerceIn(-1, 1) * side else 0
                g.plot(ax + shift - 1, attach + j, cOutline)
                g.plot(ax + shift, attach + j, cMid)
                g.plot(ax + shift + 1, attach + j, cOutline)
            }
            g.plot(ax + (if (up > 1) wave.coerceIn(-1, 1) * side else 0), attach + up + 1, cOutline)
        }
        arm(-1, 0.4f, 0.45f, 4)
        arm(1, 0.55f, 0.62f, 3)

        // Flower on top
        if (m > 0.85f) {
            val open = ((m - 0.85f) / 0.15f).coerceIn(0f, 1f)
            val fx = column(height - 1)
            val bob = (sin(pose.time * 2f) * 0.6f).roundToInt().coerceAtLeast(0)
            val fy = height + bob
            if (open < 0.5f) {
                g.plot(fx, fy, bloom)
            } else {
                g.plot(fx, fy, pollen)
                g.plot(fx - 1, fy, bloom)
                g.plot(fx + 1, fy, bloom)
                g.plot(fx, fy + 1, bloom)
            }
        }
    }

    // endregion

    // region Sunflower

    private val sunPetal = Color(0xFFFFD21F)
    private val sunPetalDark = Color(0xFFE8A317)
    private val sunDisc = Color(0xFF6B3E1E)
    private val sunSeed = Color(0xFF3E2410)

    private fun drawSunflower(g: PixelGrid, m: Float, seed: Int, pose: PlantPose) {
        val random = Random(seed)
        val firstSide = if (random.nextBoolean()) 1 else -1
        val scaleY = 1f + pose.squash
        val height = ((3 + m * 17 + pose.stretch) * scaleY).coerceAtLeast(2f)
        val top = height.roundToInt()
        val stemX = IntArray(top + 1) { y -> bendX(y.toFloat(), height, pose) }
        for (y in 0 until top) {
            g.plot(stemX[y] - 1, y, stemColor)
            g.plot(stemX[y], y, dark)
            if (m > 0.4f && y < 3) g.plot(stemX[y] - 2, y, stemColor)
        }
        var node = 0
        var y = 2
        while (y <= top - 4) {
            val side = if (node % 2 == 0) firstSide else -firstSide
            val age = ((height - y) / 7f).coerceIn(0f, 1f)
            val flutter = sin(pose.time * (2.4f + node * 0.3f) + node) * pose.flutter
            drawLeaf(g, stemX[y], y, side, (age * 3).roundToInt(), pose.flap + flutter - pose.droop * 2.5f)
            node++
            y += 4
        }
        val tipX = stemX[max(0, top - 1)]
        when {
            m < 0.5f -> {
                drawLeaf(g, tipX, top - 1, -1, 1, pose.flap + 0.5f)
                drawLeaf(g, tipX, top - 1, 1, 1, pose.flap + 0.5f)
            }
            m < 0.7f -> {
                // Green bud swelling
                val r = if (m < 0.6f) 0 else 1
                for (dx in -1 - r..r) for (dy in 0..1 + r) g.plot(tipX + dx, top + dy, if (dy == 1 + r) light else mid)
            }
            else -> {
                // Open head facing the sun; at night it hangs down.
                val radius = if (m < 0.85f) 2 else 3
                val night = 1f - pose.daylight
                val face = (pose.sunDirection * pose.daylight * 1.4f).roundToInt()
                val hang = (night * 2f + pose.droop * 3f).roundToInt()
                val cx = tipX - 1 + face
                val cy = top + radius - hang
                val petals = pose.daylight > 0.3f
                for (dx in -radius - 1..radius + 1) for (dy in -radius - 1..radius + 1) {
                    val d = kotlin.math.sqrt((dx * dx + dy * dy).toFloat())
                    when {
                        d <= radius - 0.4f -> g.plot(cx + dx + face.coerceIn(-1, 1), cy + dy, if ((dx + dy) % 2 == 0) sunSeed else sunDisc)
                        petals && d <= radius + 1.3f -> g.plot(cx + dx, cy + dy, if ((dx * 3 + dy) % 3 == 0) sunPetalDark else sunPetal)
                    }
                }
            }
        }
    }

    // endregion

    // region Tulip

    private val tulipColors = listOf(
        Color(0xFFE53950) to Color(0xFFB0213A),
        Color(0xFFFF8FB8) to Color(0xFFD7609A),
        Color(0xFFFFD84D) to Color(0xFFE0A81E),
        Color(0xFFFF9A3C) to Color(0xFFD86A1E),
    )

    private fun drawTulip(g: PixelGrid, m: Float, seed: Int, pose: PlantPose) {
        val (petalLight, petalDark) = tulipColors[abs(seed) % tulipColors.size]
        val scaleY = 1f + pose.squash
        // Two long leaves from the base
        val leafLength = (3 + m * 6).roundToInt()
        for (side in listOf(-1, 1)) {
            val flutter = sin(pose.time * 2f + side) * pose.flutter
            for (r in 0 until leafLength) {
                val t = r / leafLength.toFloat()
                val x = (side * (1 + t * 2.5f + pose.droop * t * 3f + (flutter - pose.flap * 0.5f) * t * t)).roundToInt()
                val px = if (side < 0) x - 1 else x
                g.plot(px, r, mid)
                if (r < leafLength - 2) g.plot(px - side, r, if (side < 0) light else dark)
            }
        }
        val height = ((3 + m * 9 + pose.stretch) * scaleY).coerceAtLeast(2f)
        val top = height.roundToInt()
        val stemX = IntArray(top + 1) { y -> bendX(y.toFloat(), height, pose) }
        for (y in 0 until top) g.plot(stemX[y] - 1, y, stemColor)
        val cx = stemX[max(0, top - 1)] - 1
        if (m < 0.6f) {
            val r = if (m < 0.3f) 0 else 1
            for (dx in -r..r) for (dy in 0..1 + r) g.plot(cx + dx, top + dy, if (dx == 0) light else mid)
            return
        }
        // Cup of petals; it opens with daylight and when fully grown.
        val grown = ((m - 0.6f) / 0.4f).coerceIn(0f, 1f)
        val open = (pose.daylight * grown - pose.droop).coerceIn(0f, 1f)
        val spread = (open * 1.6f).roundToInt()
        val cupHeight = if (grown < 0.5f) 3 else 4
        for (row in 0 until cupHeight) {
            val y = top + row
            val upper = row >= cupHeight - 2
            val half = if (row == 0) 1 else 2
            for (dx in -half..half) g.plot(cx + dx, y, if (dx % 2 == 0) petalLight else petalDark)
            if (upper && spread > 0) {
                g.plot(cx - half - spread, y + 1 - (row - (cupHeight - 2)), petalLight)
                g.plot(cx + half + spread, y + 1 - (row - (cupHeight - 2)), petalLight)
            }
        }
        if (open > 0.5f) g.plot(cx, top + cupHeight - 1, pollen)
    }

    // endregion

    // region Monstera

    private val mDark = Color(0xFF1E6B2E)
    private val mMid = Color(0xFF2E8B3E)
    private val mVein = Color(0xFF57B05A)

    private val monsteraLeaves = listOf(
        listOf("cb", "cb", "cb", "b."),
        listOf(".aaa.", "abcba", "abcba", ".a.a."),
        listOf("..aaa..", ".abbba.", "abbcbba", "ab.c.ba", ".abcba.", "..a.a.."),
        listOf("...aaa...", "..abbba..", ".abbcbba.", "abb.c.bba", "abbbcbbba", "ab.bcb.ba", ".abbcbba.", "..aa.aa.."),
    )

    private fun drawMonstera(g: PixelGrid, m: Float, seed: Int, pose: PlantPose) {
        val random = Random(seed)
        val grown = floor(m * 4).toInt()
        val count = 1 + grown
        val newest = if (m >= 1f) 1f else m * 4 - grown
        val slots = (0 until count).map { i ->
            val spread = if (count == 1) 0f else i / (count - 1f) * 2f - 1f
            spread * 62f + random.nextInt(-6, 7)
        }
        val youngestIndex = slots.indices.minByOrNull { abs(slots[it]) } ?: 0
        slots.indices.sortedByDescending { abs(slots[it]) }.forEach { i ->
            val youngest = i == youngestIndex && m < 1f
            val growth = if (youngest) newest else 1f
            val length = (3 + m * 11) * (0.75f + random.nextFloat() * 0.25f) * (0.6f + 0.4f * growth) * (1f - abs(slots[i]) / 250f)
            val slope = sin(slots[i] / 180f * PI.toFloat()) * 1.5f
            val sway = (pose.sway + pose.lean) * 0.6f + sin(pose.time * (1.8f + i * 0.4f) + i) * pose.flutter * 1.5f
            val rows = max(2, (length * (1f - abs(slots[i]) / 160f)).roundToInt())
            var endX = 0
            for (r in 0 until rows) {
                val t = r / rows.toFloat()
                val droopBend = pose.droop * t * t * 4f * (if (slots[i] >= 0) 1 else -1)
                endX = (slope * r + sway * t * t + droopBend - 0.5f).roundToInt()
                g.plot(endX, r, stemColor)
            }
            val size = when {
                youngest && growth < 0.35f -> 0
                youngest && growth < 0.7f -> 1
                m < 0.6f -> 2
                else -> 3
            }
            val template = monsteraLeaves[size]
            val w = template.maxOf { it.length }
            val lift = (pose.flap * 0.5f - pose.droop * 2f).roundToInt()
            template.forEachIndexed { row, line ->
                line.forEachIndexed { col, c ->
                    val color = when (c) {
                        'a' -> mDark
                        'b' -> mMid
                        'c' -> mVein
                        else -> return@forEachIndexed
                    }
                    g.plot(endX - w / 2 + col, rows + template.size - 1 - row + lift, color)
                }
            }
        }
    }

    // endregion

    // region Bonsai

    private val bark = Color(0xFF6B4A2E)
    private val barkLight = Color(0xFF8D6744)
    private val padDark = Color(0xFF2F6B3A)
    private val padMid = Color(0xFF3F8F4A)
    private val padLight = Color(0xFF6FBF5A)

    private fun drawBonsai(g: PixelGrid, m: Float, seed: Int, pose: PlantPose) {
        val phase = (abs(seed) % 628) / 100f
        val scaleY = 1f + pose.squash * 0.5f
        val height = ((3 + m * 10 + pose.stretch * 0.4f) * scaleY).coerceAtLeast(2f)
        val top = height.roundToInt()
        val calm = pose.copy(sway = pose.sway * 0.3f, lean = pose.lean * 0.5f)
        fun trunkX(y: Int): Int {
            val t = y / height
            return (sin(t * PI.toFloat() * 1.2f + phase) * 1.6f * m + bendX(y.toFloat(), height, calm)).roundToInt()
        }
        for (y in 0 until top) {
            val x = trunkX(y)
            g.plot(x - 1, y, bark)
            g.plot(x, y, barkLight)
            if (m > 0.5f && y < top / 2) g.plot(x + 1, y, bark)
        }
        val breathe = sin(pose.time * 1.3f) * 0.35f
        fun pad(cx: Int, cy: Int, size: Float) {
            val rx = 1.5f + size * 2f + breathe
            val ry = 0.8f + size * 1f
            for (dx in -4..4) for (dy in -2..2) {
                val e = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry)
                if (e > 1f) continue
                val color = when {
                    dy > 0 && e < 0.6f -> padLight
                    e > 0.7f -> padDark
                    else -> padMid
                }
                g.plot(cx + dx, cy + dy, color)
            }
        }
        // Branches appear one by one, each ending in a foliage pad.
        listOf(Triple(0.45f, -1, 0.3f), Triple(0.65f, 1, 0.5f), Triple(0.82f, -1, 0.7f)).forEach { (at, side, from) ->
            if (m <= from) return@forEach
            val grow = ((m - from) / 0.25f).coerceIn(0f, 1f)
            val y = (height * at).roundToInt()
            val x0 = trunkX(y)
            val length = (1 + grow * 3).roundToInt()
            for (i in 1..length) g.plot(x0 + side * i - (if (side < 0) 1 else 0), y + i / 2, bark)
            pad(x0 + side * (length + 1), y + length / 2 + 1, grow)
        }
        pad(trunkX(top - 1), top, (0.4f + m * 0.6f))
    }

    // endregion

    // region Flytrap

    private val trapRed = Color(0xFFC8243C)
    private val trapGreen = Color(0xFF6CB33A)
    private val trapTooth = Color(0xFFE8F5B0)
    private val flyColor = Color(0xFF222034)

    private fun drawFlytrap(g: PixelGrid, m: Float, seed: Int, pose: PlantPose) {
        val random = Random(seed)
        val count = 1 + floor(m * 4).toInt()
        val big = m > 0.4f
        val half = if (big) 2 else 1
        val openness = (0.55f + pose.daylight * 0.45f) * (1f - pose.snap) * (1f - pose.droop)
        val gap = (openness * 2f).roundToInt()
        val slots = (0 until count).map { i ->
            val spread = if (count == 1) 0f else i / (count - 1f) * 2f - 1f
            spread * 60f + random.nextInt(-5, 6)
        }
        var firstTrap = Offset.Zero
        slots.indices.sortedByDescending { abs(slots[it]) }.forEach { i ->
            val slope = sin(slots[i] / 180f * PI.toFloat()) * 1.3f
            val length = ((2 + m * 4) * (0.8f + random.nextFloat() * 0.2f) * (1f - abs(slots[i]) / 200f)).roundToInt()
            val sway = (pose.sway + pose.lean) * 0.5f + sin(pose.time * 2f + i) * pose.flutter
            var x = 0
            for (r in 0 until length) {
                val t = r / length.toFloat()
                x = (slope * r + sway * t - 0.5f + pose.droop * t * 3f * (if (slots[i] >= 0) 1 else -1)).roundToInt()
                g.plot(x, r, stemColor)
            }
            // Jaws, bottom to top: teeth, lower jaw, red inside (gap rows), upper jaw, teeth
            var y = length
            fun row(color: (Int) -> Color?) {
                for (dx in -half..half) color(dx)?.let { g.plot(x + dx, y, it) }
                y++
            }
            if (gap == 0) {
                row { trapGreen }
                row { dx -> if (dx % 2 == 0) trapTooth else trapGreen }
                row { trapGreen }
            } else {
                row { dx -> if (dx % 2 == 0) trapTooth else null }
                row { trapGreen }
                repeat(gap) { row { dx -> if (abs(dx) == half) trapGreen else trapRed } }
                row { trapGreen }
                row { dx -> if (dx % 2 == 0) trapTooth else null }
            }
            if (i == slots.indices.minByOrNull { abs(slots[it]) }) firstTrap = Offset(x.toFloat(), y.toFloat())
        }
        if (pose.fly) {
            val fx = firstTrap.x + cos(pose.time * 3.1f) * 4f + sin(pose.time * 7f) * 0.6f
            val fy = firstTrap.y + 2 + sin(pose.time * 2.3f) * 2f
            g.plot(fx.roundToInt(), fy.roundToInt(), flyColor)
            if ((pose.time * 20).toInt() % 2 == 0) g.plot(fx.roundToInt(), fy.roundToInt() + 1, Color(0x99FFFFFF))
        }
    }

    // endregion

    // region Fern

    private fun drawFern(g: PixelGrid, m: Float, seed: Int, pose: PlantPose) {
        val random = Random(seed)
        val scaleY = 1f + pose.squash * 0.8f
        val grown = floor(m * 5).toInt()
        val frondCount = 2 + grown
        val newest = if (m >= 1f) 1f else m * 5 - grown // unrolling progress of the youngest frond
        val maxLength = 4 + m * 13 + pose.stretch

        // Fronds fan out; older ones lean further out, the youngest stands in the middle.
        val slots = (0 until frondCount).map { i ->
            val spread = if (frondCount == 1) 0f else i / (frondCount - 1f) * 2f - 1f
            spread * 42f + random.nextInt(-4, 5)
        }
        val youngestIndex = slots.indices.minByOrNull { abs(slots[it]) } ?: 0
        // Draw outer fronds first so inner ones overlap them.
        slots.indices.sortedByDescending { abs(slots[it]) }.forEach { i ->
            val youngest = i == youngestIndex
            val unroll = if (youngest) newest.coerceIn(0.2f, 1f) else 1f
            val variation = 0.8f + random.nextFloat() * 0.2f
            val length = maxLength * variation * (0.7f + 0.3f * (1 - abs(slots[i]) / 45f)) * (if (youngest) unroll else 1f)
            val flutter = sin(pose.time * (2.2f + i * 0.3f) + i) * pose.flutter * 5f
            drawFrond(g, slots[i] + flutter, length, unroll, pose, scaleY)
        }
        g.plot(-1, 0, stemColor)
        g.plot(0, 0, stemColor)
    }

    private val fiddlehead = listOf(".cc.", "c..c", "..bc", ".b..")

    /**
     * A frond drawn row by row: each row is sheared sideways (lean plus an outward arch), with
     * horizontal leaflets that get shorter towards the tip. Keeps clean pixel art at any angle.
     */
    private fun drawFrond(g: PixelGrid, angleDeg: Float, length: Float, unroll: Float, pose: PlantPose, scaleY: Float) {
        val side = if (angleDeg >= 0) 1 else -1
        val lean = abs(angleDeg) / 90f
        val rows = max(2, (length * (1f - lean * 0.45f) * scaleY).roundToInt())
        val visible = max(1, (rows * unroll).roundToInt())
        val slope = sin(angleDeg / 180f * PI.toFloat()) * 1.1f
        val arch = side * (0.6f + lean * 2.2f) * (1f + pose.droop * 2.5f) - side * pose.flap * 0.6f
        val wind = (pose.sway + pose.lean) * 0.8f

        fun centerX(r: Int): Int {
            val t = r / rows.toFloat()
            return (slope * r + arch * t * t * rows * 0.35f + wind * t * t - 0.5f).roundToInt()
        }

        for (r in 0 until visible) {
            val x = centerX(r)
            val y = r + 1
            g.plot(x, y, stemColor)
            if (r == 0) continue
            val taper = 1f - r / rows.toFloat()
            val reach = if (r % 2 == 0) (taper * 3f + 0.4f).toInt() else (taper * 2f + 0.3f).toInt()
            for (d in 1..reach) {
                val outer = d == reach
                g.plot(x - d, y, if (outer) mid else light)
                g.plot(x + d, y, if (outer) dark else mid)
            }
        }
        if (visible < rows) {
            // Still rolled up: fiddlehead on top of the unrolled part.
            val x = centerX(visible)
            fiddlehead.forEachIndexed { row, line ->
                line.forEachIndexed { col, c ->
                    val color = leafColor(c) ?: return@forEachIndexed
                    val dx = if (side > 0) col - 1 else 2 - col
                    g.plot(x + dx, visible + fiddlehead.size - row, color)
                }
            }
        }
    }

    // endregion
}

/** Pixel grid with origin at the bottom center: x to the right, y upwards. */
internal class PixelGrid(private val scope: DrawScope, private val base: Offset, private val pixel: Float) {
    private val size = Size(pixel + 0.5f, pixel + 0.5f)

    fun plot(x: Int, y: Int, color: Color) {
        if (y < 0) return
        scope.drawRect(color, Offset(base.x + x * pixel, base.y - (y + 1) * pixel), size)
    }
}

