package com.example.mathfun

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.RotateAnimation
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.SharedPreferences
import java.util.Locale
import kotlin.random.Random

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var root: LinearLayout
    private lateinit var questionView: TextView
    private lateinit var feedbackView: TextView
    private lateinit var resultView: TextView
    private lateinit var scoreView: TextView
    private lateinit var nextButton: Button
    private lateinit var retryButton: Button
    private lateinit var skipButton: Button
    private lateinit var choiceButtons: List<Button>
    private lateinit var prefs: SharedPreferences
    private var tts: TextToSpeech? = null
    private var language = "English"
    private var operation = "Addition"
    private var level = 1
    private var score = 0
    private var streak = 0
    private var answer = 0
    private var soundEnabled = true
    private var answered = false
    private val choices = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("mathfun", MODE_PRIVATE)
        score = prefs.getInt("score", 0)
        level = prefs.getInt("level", 1).coerceIn(1, 8)
        tts = TextToSpeech(this, this)
        buildUi()
        newQuestion()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) setTtsLanguage()
    }

    private fun setTtsLanguage() {
        val locale = if (language == "Hindi") Locale("hi", "IN") else Locale.US
        tts?.language = locale
    }

    private fun speak(text: String) {
        if (soundEnabled) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mathfun-feedback")
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20, 18, 20, 24)
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "Math Fun"
            textSize = 32f
            gravity = Gravity.CENTER
        }, matchWrap())

        val languageRow = row()
        languageRow.addView(button("English") { language = "English"; setTtsLanguage(); newQuestion() }, weighted())
        languageRow.addView(button("हिन्दी") { language = "Hindi"; setTtsLanguage(); newQuestion() }, weighted())
        languageRow.addView(button("🔊 Sound") {
            soundEnabled = !soundEnabled
            feedbackView.text = if (soundEnabled) "Sound on" else "Sound off"
        }, weighted())
        root.addView(languageRow, matchWrap())

        val operationRow = row()
        listOf("Addition", "Subtraction", "Multiplication", "Division", "Mixed").forEach { op ->
            operationRow.addView(button(op) { operation = op; newQuestion() }, weighted())
        }
        root.addView(operationRow, matchWrap())

        root.addView(button("Level: tap to change") {
            level = if (level >= 8) 1 else level + 1
            prefs.edit().putInt("level", level).apply()
            newQuestion()
        }, matchWrap())

        questionView = TextView(this).apply {
            textSize = 40f
            gravity = Gravity.CENTER
            setPadding(8, 26, 8, 18)
        }
        root.addView(questionView, matchWrap())

        feedbackView = TextView(this).apply {
            textSize = 23f
            gravity = Gravity.CENTER
            setPadding(8, 6, 8, 4)
        }
        root.addView(feedbackView, matchWrap())

        resultView = TextView(this).apply {
            textSize = 42f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(8, 0, 8, 8)
        }
        root.addView(resultView, matchWrap())

        val row1 = row()
        val row2 = row()
        val buttons = mutableListOf<Button>()
        repeat(4) { index ->
            val b = button("?")
            b.setOnClickListener { checkAnswer(bTag(b)) }
            b.textSize = 25f
            buttons.add(b)
            (if (index < 2) row1 else row2).addView(b, weighted())
        }
        root.addView(row1, matchWrap())
        root.addView(row2, matchWrap())
        choiceButtons = buttons

        val actionRow = row()
        nextButton = button("Next question") { newQuestion() }
        retryButton = button("Retry") { retryQuestion() }
        skipButton = button("Skip") { newQuestion() }
        actionRow.addView(nextButton, weighted())
        actionRow.addView(retryButton, weighted())
        actionRow.addView(skipButton, weighted())
        root.addView(actionRow, matchWrap())

        scoreView = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(8, 18, 8, 8)
        }
        root.addView(scoreView, matchWrap())

        root.addView(button("Parent: reset progress") {
            score = 0
            streak = 0
            level = 1
            prefs.edit().clear().apply()
            newQuestion()
        }, matchWrap())
    }

    private fun bTag(button: Button): Int = button.tag as Int

    // The level limit applies to the answer/result, not independently to each operand.
    private fun resultLimitExclusive(): Int = when (level) {
        1 -> 10
        2 -> 20
        3 -> 50
        4 -> 100
        5 -> 200
        6 -> 500
        7 -> 1000
        else -> 2000
    }

    private fun newQuestion() {
        if (!::questionView.isInitialized) return
        answered = false
        val limit = resultLimitExclusive()
        val maxAnswer = limit - 1
        val op = if (operation == "Mixed") {
            listOf("Addition", "Subtraction", "Multiplication", "Division").random()
        } else operation

        when (op) {
            "Addition" -> {
                // Both operands are positive and the sum is always below the level limit.
                val a = Random.nextInt(1, limit - 1)
                val b = Random.nextInt(1, limit - a)
                answer = a + b
                questionView.text = "$a + $b = ?"
            }
            "Subtraction" -> {
                val high = Random.nextInt(1, limit)
                val low = Random.nextInt(1, high + 1)
                answer = high - low
                questionView.text = "$high − $low = ?"
            }
            "Multiplication" -> {
                val operandCap = minOf(if (level == 1) 9 else 12, maxAnswer)
                var a: Int
                var b: Int
                do {
                    a = Random.nextInt(1, operandCap + 1)
                    b = Random.nextInt(1, operandCap + 1)
                } while (a * b >= limit)
                answer = a * b
                questionView.text = "$a × $b = ?"
            }
            else -> {
                // Keep division exact and its quotient below the level limit.
                val quotientCap = minOf(12, maxAnswer)
                val quotient = Random.nextInt(1, quotientCap + 1)
                val divisor = Random.nextInt(1, minOf(12, maxAnswer) + 1)
                answer = quotient
                questionView.text = "${quotient * divisor} ÷ $divisor = ?"
            }
        }

        choices.clear()
        choices.add(answer)
        while (choices.size < 4) {
            val spread = maxOf(5, limit / 2)
            val candidate = (answer + Random.nextInt(-spread, spread + 1)).coerceAtLeast(0)
            if (candidate < limit && candidate !in choices) choices.add(candidate)
        }
        choices.shuffle()
        choiceButtons.forEachIndexed { index, btn ->
            btn.text = choices[index].toString()
            btn.tag = choices[index]
            btn.isEnabled = true
        }
        feedbackView.text = if (language == "Hindi") "सही जवाब चुनो!" else "Choose the correct answer!"
        resultView.visibility = View.GONE
        nextButton.visibility = View.GONE
        retryButton.visibility = View.GONE
        skipButton.visibility = View.GONE
        scoreView.text = "⭐ $score     🔥 $streak     Level $level (answer < $limit)"
    }

    private fun checkAnswer(selected: Int) {
        if (answered) return
        if (selected == answer) {
            answered = true
            score++
            streak++
            choiceButtons.forEach { it.isEnabled = false }
            val message = if (language == "Hindi") "शाबाश! सही जवाब!" else "Great job! Correct!"
            val earnedMedal = true
            feedbackView.text = if (earnedMedal) {
                if (language == "Hindi") "बहुत खूब! तुम्हारा मेडल!" else "Amazing! You earned a medal!"
            } else message
            resultView.text = if (earnedMedal) "🏅" else "⭐"
            resultView.visibility = View.VISIBLE
            animateResult(medal = earnedMedal)
            speak(if (earnedMedal) {
                if (language == "Hindi") "शाबाश! तुम्हारा मेडल!" else "Great job! You earned a medal!"
            } else message)
            nextButton.visibility = View.VISIBLE
            retryButton.visibility = View.GONE
            skipButton.visibility = View.GONE
            prefs.edit().putInt("score", score).putInt("level", level).apply()
        } else {
            streak = 0
            val message = if (language == "Hindi") "कोई बात नहीं। फिर कोशिश करो!" else "Not quite. Try again or skip."
            feedbackView.text = message
            resultView.text = "❌"
            resultView.visibility = View.VISIBLE
            animateResult(medal = false)
            speak(message)
            retryButton.visibility = View.VISIBLE
            skipButton.visibility = View.VISIBLE
            nextButton.visibility = View.GONE
            // Keep the answer choices enabled so the child can try again.
        }
        scoreView.text = "⭐ $score     🔥 $streak     Level $level (answer < ${resultLimitExclusive()})"
    }

    private fun retryQuestion() {
        answered = false
        resultView.visibility = View.GONE
        feedbackView.text = if (language == "Hindi") "फिर से कोशिश करो!" else "Try again. You can do it!"
        choiceButtons.forEach { it.isEnabled = true }
        retryButton.visibility = View.GONE
        skipButton.visibility = View.GONE
    }

    private fun animateResult(medal: Boolean) {
        val scale = ScaleAnimation(
            0.2f, 1.15f, 0.2f, 1.15f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f
        ).apply { duration = 450; repeatCount = 1; repeatMode = Animation.REVERSE }
        val rotate = RotateAnimation(
            if (medal) -25f else -8f, if (medal) 335f else 8f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f
        ).apply { duration = if (medal) 700 else 180; repeatCount = if (medal) 0 else 2 }
        resultView.startAnimation(AnimationSet(true).apply {
            addAnimation(scale)
            addAnimation(rotate)
            fillAfter = false
        })
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun button(label: String, action: (() -> Unit)? = null): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setOnClickListener { action?.invoke() }
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = 6 }

    private fun weighted() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
