package com.example.mathfun

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
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
    private lateinit var scoreView: TextView
    private lateinit var prefs: SharedPreferences
    private var tts: TextToSpeech? = null
    private var language = "English"
    private var operation = "Addition"
    private var level = 1
    private var score = 0
    private var streak = 0
    private var answer = 0
    private var questionCount = 0
    private var soundEnabled = true
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
        if (!soundEnabled) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mathfun-feedback")
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20, 18, 20, 24)
        }
        scroll.addView(root)
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "Math Fun"
            textSize = 32f
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWrap())

        val languageRow = row()
        languageRow.addView(button("English") { language = "English"; setTtsLanguage(); newQuestion() }, weighted())
        languageRow.addView(button("हिन्दी") { language = "Hindi"; setTtsLanguage(); newQuestion() }, weighted())
        languageRow.addView(button("🔊 Sound") { soundEnabled = !soundEnabled; feedbackView.text = if (soundEnabled) "Sound on" else "Sound off" }, weighted())
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
            setPadding(8, 6, 8, 10)
        }
        root.addView(feedbackView, matchWrap())

        val choicesRow1 = row()
        val choicesRow2 = row()
        val answerButtons = mutableListOf<Button>()
        repeat(4) { index ->
            val b = button("?", null)
            b.textSize = 25f
            b.setOnClickListener { checkAnswer(b.tag as Int) }
            answerButtons.add(b)
            (if (index < 2) choicesRow1 else choicesRow2).addView(b, weighted())
        }
        root.addView(choicesRow1, matchWrap())
        root.addView(choicesRow2, matchWrap())
        choiceButtons = answerButtons

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
            feedbackView.text = "Progress reset"
            newQuestion()
        }, matchWrap())
    }

    private lateinit var choiceButtons: List<Button>

    private fun newQuestion() {
        if (!::questionView.isInitialized) return
        val max = when (level) {
            1 -> 9
            2 -> 19
            3 -> 49
            4 -> 99
            else -> (level * 100) - 1
        }
        val op = if (operation == "Mixed") listOf("Addition", "Subtraction", "Multiplication", "Division").random() else operation
        val a: Int
        val b: Int
        when (op) {
            "Addition" -> {
                a = Random.nextInt(1, max + 1); b = Random.nextInt(1, max + 1); answer = a + b
                questionView.text = "$a + $b = ?"
            }
            "Subtraction" -> {
                a = Random.nextInt(1, max + 1); b = Random.nextInt(1, max + 1)
                val high = maxOf(a, b); val low = minOf(a, b); answer = high - low
                questionView.text = "$high − $low = ?"
            }
            "Multiplication" -> {
                val cap = minOf(max, if (level == 1) 9 else 12)
                a = Random.nextInt(1, cap + 1); b = Random.nextInt(1, cap + 1); answer = a * b
                questionView.text = "$a × $b = ?"
            }
            else -> {
                b = Random.nextInt(1, minOf(max, 12) + 1)
                answer = Random.nextInt(1, minOf(max, 12) + 1)
                a = b * answer
                questionView.text = "$a ÷ $b = ?"
            }
        }
        choices.clear()
        choices.add(answer)
        while (choices.size < 4) {
            val spread = maxOf(5, max / 2)
            val candidate = (answer + Random.nextInt(-spread, spread + 1)).coerceAtLeast(0)
            if (candidate !in choices) choices.add(candidate)
        }
        choices.shuffle()
        choiceButtons.forEachIndexed { index, btn ->
            btn.text = choices[index].toString()
            btn.tag = choices[index]
            btn.isEnabled = true
        }
        feedbackView.text = if (language == "Hindi") "सही जवाब चुनो!" else "Choose the correct answer!"
        scoreView.text = "⭐ $score     🔥 $streak     Level $level"
        questionCount++
    }

    private fun checkAnswer(selected: Int) {
        choiceButtons.forEach { it.isEnabled = false }
        if (selected == answer) {
            score += 1
            streak += 1
            val message = if (language == "Hindi") "शाबाश! बहुत अच्छा!" else "Great job! Correct!"
            feedbackView.text = if (streak % 2 == 0) "$message 🏅 MEDAL!" else "$message ⭐"
            speak(message)
            if (streak % 2 == 0) speak(if (language == "Hindi") "तुम्हारा मेडल!" else "You earned a medal!")
            prefs.edit().putInt("score", score).putInt("level", level).apply()
        } else {
            streak = 0
            val message = if (language == "Hindi") "कोई बात नहीं। फिर कोशिश करो!" else "Not quite. Try again!"
            feedbackView.text = "$message  Answer: $answer"
            speak(message)
        }
        scoreView.text = "⭐ $score     🔥 $streak     Level $level"
        root.postDelayed({ newQuestion() }, 1300)
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun button(label: String, action: (() -> Unit)?): Button =
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
