package app.vehplayer.android.dashboard

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import app.vehplayer.android.R

/**
 * Purpose-built on-screen keyboard for the car dashboard, replacing the
 * system IME. The system keyboard is phone-shaped (small keys tuned for
 * thumb-typing on a handheld screen held close to the face) and pops up
 * unpredictably sized over whatever's mirrored - wrong fit for a car screen
 * viewed at arm's length. This one is always-large-target (44dp keys, at
 * Material's minimum recommended touch target), always laid out the same
 * regardless of system IME state, and lives inside the mirrored view
 * itself so what the car shows is exactly this, not the phone's default
 * keyboard.
 *
 * Letters + a 123/symbols toggle (not an always-visible digits row): an
 * always-visible 5th row was tried first and, measured against the actual
 * available height inside the dashboard (see NEXT_SESSION.md - the hero
 * card is a fraction of the screen, not the full activity), it pushed the
 * whole keyboard taller than the space that was ever going to be available
 * for it, hiding the search field it's supposed to serve. A toggle key
 * costs one extra tap for the rarer case (house numbers) to keep the
 * common case (typing a street/place name) at a height that actually fits.
 */
class CarKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onKey: ((String) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onSpace: (() -> Unit)? = null
    var onSearch: (() -> Unit)? = null

    private val keyHeightPx = dp(44)
    private val keyMarginPx = dp(3)

    private var digitsMode = false
    private lateinit var row1Keys: List<Button>
    private lateinit var row2Keys: List<Button>
    private lateinit var row3Keys: List<Button>
    private lateinit var modeToggle: Button

    private val lettersRow1 = "qwertyuiop"
    private val lettersRow2 = "asdfghjkl"
    private val lettersRow3 = "zxcvbnm"
    private val digitsRow1 = "1234567890"
    private val digitsRow2 = "-/():;€&@\""
    private val digitsRow3 = ".,?!'#%+"

    init {
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.dash_surface))
        setPadding(dp(8), dp(8), dp(8), dp(8))

        val row1 = keyRow(lettersRow1.length)
        val row2 = keyRow(lettersRow2.length)
        val row3 = bottomKeyRow(lettersRow3.length)
        row1Keys = row1.second
        row2Keys = row2.second
        row3Keys = row3.second
        addView(row1.first)
        addView(row2.first)
        addView(row3.first)
        addView(actionRow())
        applyMode()
    }

    private fun keyRow(count: Int): Pair<LinearLayout, List<Button>> {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        val buttons = (0 until count).map { i ->
            keyButton("", weight = 1f) { row1KeyPressed(row, i) }.also { row.addView(it) }
        }
        return row to buttons
    }

    // Every row's click just reports the button's current text, so the
    // same handler works whether it's showing a letter or a digit/symbol.
    private fun row1KeyPressed(row: LinearLayout, index: Int) {
        val button = row.getChildAt(index) as Button
        onKey?.invoke(button.text.toString())
    }

    private fun bottomKeyRow(letterCount: Int): Pair<LinearLayout, List<Button>> {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        val buttons = (0 until letterCount).map { i ->
            keyButton("", weight = 1f) { (row.getChildAt(i) as Button).text.toString().let { onKey?.invoke(it) } }
                .also { row.addView(it) }
        }
        row.addView(
            keyButton("⌫", weight = 1.5f, contentDescription = "Backspace") { onBackspace?.invoke() },
        )
        return row to buttons
    }

    private fun actionRow() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        modeToggle = keyButton("123", weight = 1f, contentDescription = "Numbers and symbols") {
            digitsMode = !digitsMode
            applyMode()
        }
        addView(modeToggle)
        addView(
            keyButton("Space", weight = 3f, contentDescription = "Space") { onSpace?.invoke() },
        )
        addView(
            keyButton("Search", weight = 1.5f, accent = true, contentDescription = "Search") {
                onSearch?.invoke()
            },
        )
    }

    private fun applyMode() {
        val (r1, r2, r3) = if (digitsMode) {
            Triple(digitsRow1, digitsRow2, digitsRow3)
        } else {
            Triple(lettersRow1, lettersRow2, lettersRow3)
        }
        r1.forEachIndexed { i, c -> row1Keys.getOrNull(i)?.text = c.toString() }
        r2.forEachIndexed { i, c -> row2Keys.getOrNull(i)?.text = c.toString() }
        r3.forEachIndexed { i, c -> row3Keys.getOrNull(i)?.text = c.toString() }
        modeToggle.text = if (digitsMode) "ABC" else "123"
    }

    /** Called by the host after a fresh open so it doesn't reopen mid-symbols. */
    fun resetMode() {
        digitsMode = false
        applyMode()
    }

    private fun keyButton(
        label: String,
        weight: Float,
        accent: Boolean = false,
        contentDescription: String? = null,
        onClick: () -> Unit,
    ) = Button(context).apply {
        text = label
        this.contentDescription = contentDescription
        isAllCaps = false
        typeface = Typeface.DEFAULT
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label.length > 1) 13f else 16f)
        setTextColor(
            context.getColor(if (accent) R.color.dash_bg else R.color.dash_text_primary),
        )
        background = context.getDrawable(
            if (accent) R.drawable.bg_keyboard_key_accent else R.drawable.bg_keyboard_key,
        )
        gravity = Gravity.CENTER
        minWidth = 0
        minimumWidth = 0
        stateListAnimator = null
        layoutParams = LayoutParams(0, keyHeightPx, weight).apply {
            setMargins(keyMarginPx, keyMarginPx, keyMarginPx, keyMarginPx)
        }
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
