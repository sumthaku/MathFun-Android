package com.example.mathfun

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.RotateAnimation
import android.view.animation.ScaleAnimation
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.SharedPreferences
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.Locale
import kotlin.random.Random

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var root: LinearLayout
    private lateinit var questionView: TextView
    private lateinit var feedbackView: TextView
    private lateinit var resultView: TextView
    private lateinit var scoreView: TextView
    private lateinit var answerInput: EditText
    private lateinit var submitInputButton: Button
    private lateinit var inputRow: LinearLayout
    private lateinit var choiceArea: LinearLayout
    private lateinit var nextButton: Button
    private lateinit var retryButton: Button
    private lateinit var skipButton: Button
    private lateinit var choiceButtons: List<Button>
    private lateinit var operationSpinner: Spinner
    private lateinit var levelSpinner: Spinner
    private lateinit var answerModeSpinner: Spinner
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
    private val palette = listOf(Color.rgb(255, 224, 130), Color.rgb(178, 235, 242), Color.rgb(206, 196, 255), Color.rgb(255, 204, 188))

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
        tts?.language = if (language == "Hindi") Locale("hi", "IN") else Locale.US
    }

    private fun speak(text: String) {
        if (soundEnabled) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mathfun-feedback")
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(18, 16, 18, 24)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(255, 249, 196), Color.rgb(225, 245, 254))).apply { cornerRadius = 24f }
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "Math Fun"
            textSize = 34f
            setTextColor(Color.rgb(103, 58, 183))
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 12)
        }, matchWrap())

        // Parent controls stay visible at the top and require a confirmation phrase.
        root.addView(colorButton("Parent controls · Reset progress", palette[3]) {
            showParentResetDialog()
        }, matchWrap())

        val languageRow = row()
        languageRow.addView(colorButton("English", palette[1]) { language = "English"; setTtsLanguage(); newQuestion() }, weighted())
        languageRow.addView(colorButton("हिन्दी", palette[2]) { language = "Hindi"; setTtsLanguage(); newQuestion() }, weighted())
        languageRow.addView(colorButton(if (soundEnabled) "🔊 Sound" else "🔇 Sound", palette[0]) {
            soundEnabled = !soundEnabled
            feedbackView.text = if (soundEnabled) "Sound on" else "Sound off"
        }, weighted())
        root.addView(languageRow, matchWrap())

        root.addView(label("Choose operation"), matchWrap())
        operationSpinner = spinner(listOf("Addition", "Subtraction", "Multiplication", "Division", "Mixed"))
        operationSpinner.setSelection(0)
        operationSpinner.onItemSelectedListener = simpleSelection { selected ->
            operation = selected
            if (::questionView.isInitialized) newQuestion()
        }
        root.addView(operationSpinner, matchWrap())

        root.addView(label("Difficulty level"), matchWrap())
        levelSpinner = spinner((1..8).map { n -> when(n) {
            1 -> "Level 1 (answer < 10)"
            2 -> "Level 2 (answer < 20)"
            3 -> "Level 3 (answer < 50)"
            4 -> "Level 4 (answer < 100)"
            5 -> "Level 5 (answer < 200)"
            6 -> "Level 6 (answer < 500)"
            7 -> "Level 7 (answer < 1000)"
            else -> "Level 8 (answer < 2000)"
        }})
        levelSpinner.setSelection(level - 1)
        levelSpinner.onItemSelectedListener = simpleSelection { selected ->
            level = selected.substringAfter("Level ").substringBefore(" ").toIntOrNull() ?: 1
            prefs.edit().putInt("level", level).apply()
            if (::questionView.isInitialized) newQuestion()
        }
        root.addView(levelSpinner, matchWrap())

        root.addView(label("Answer style"), matchWrap())
        answerModeSpinner = spinner(listOf("Type the answer (default)", "Multiple choice"))
        answerModeSpinner.onItemSelectedListener = simpleSelection {
            updateAnswerMode()
        }
        root.addView(answerModeSpinner, matchWrap())

        questionView = TextView(this).apply {
            textSize = 42f
            setTextColor(Color.rgb(40, 53, 147))
            gravity = Gravity.CENTER
            setPadding(8, 26, 8, 18)
        }
        root.addView(questionView, matchWrap())

        feedbackView = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.rgb(55, 71, 79))
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }
        root.addView(feedbackView, matchWrap())

        resultView = TextView(this).apply {
            textSize = 52f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(8, 0, 8, 8)
        }
        root.addView(resultView, matchWrap())

        answerInput = EditText(this).apply {
            hint = if (language == "Hindi") "यहाँ जवाब लिखें" else "Type your answer"
            textSize = 32f
            gravity = Gravity.CENTER
            minHeight = dp(76)
            minimumHeight = dp(76)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true)
            isFocusable = true
            isFocusableInTouchMode = true
            background = rounded(Color.WHITE, 18f)
        }
        submitInputButton = colorButton("Submit", palette[0]) {
            val typed = answerInput.text.toString().trim().toIntOrNull()
            if (typed == null) {
                feedbackView.text = if (language == "Hindi") "पहले जवाब लिखो!" else "Please type an answer first."
            } else checkAnswer(typed)
        }
        inputRow = row()
        inputRow.addView(answerInput, LinearLayout.LayoutParams(0, dp(76), 3f).apply { rightMargin = dp(10) })
        inputRow.addView(submitInputButton, LinearLayout.LayoutParams(0, dp(76), 1.5f))
        root.addView(inputRow, matchWrap())

        choiceArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = row()
        val row2 = row()
        val buttons = mutableListOf<Button>()
        repeat(4) { index ->
            val b = colorButton("? ", palette[index])
            b.setOnClickListener { checkAnswer(bTag(b)) }
            b.textSize = 26f
            buttons.add(b)
            (if (index < 2) row1 else row2).addView(b, weighted())
        }
        choiceArea.addView(row1, matchWrap())
        choiceArea.addView(row2, matchWrap())
        root.addView(choiceArea, matchWrap())
        choiceButtons = buttons

        val actionRow = row()
        nextButton = colorButton("Next question", palette[1]) { newQuestion() }
        retryButton = colorButton("Retry", palette[2]) { retryQuestion() }
        skipButton = colorButton("Skip", palette[3]) { newQuestion() }
        actionRow.addView(nextButton, weighted())
        actionRow.addView(retryButton, weighted())
        actionRow.addView(skipButton, weighted())
        root.addView(actionRow, matchWrap())

        scoreView = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.rgb(49, 87, 44))
            gravity = Gravity.CENTER
            setPadding(8, 18, 8, 8)
        }
        root.addView(scoreView, matchWrap())

        updateAnswerMode()
    }

    private fun showParentResetDialog() {
        val confirmationInput = EditText(this).apply {
            hint = "Type PARENTS to confirm"
            textSize = 18f
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 8, 28, 0)
            addView(TextView(this@MainActivity).apply {
                text = "This resets the score and difficulty level.\nType PARENTS to continue."
                textSize = 16f
                setTextColor(Color.rgb(55, 71, 79))
            }, matchWrap())
            addView(confirmationInput, matchWrap())
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Parent confirmation")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset progress", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (confirmationInput.text.toString().trim().equals("PARENTS", ignoreCase = true)) {
                    score = 0
                    streak = 0
                    level = 1
                    prefs.edit().clear().apply()
                    levelSpinner.setSelection(0)
                    newQuestion()
                    dialog.dismiss()
                } else {
                    confirmationInput.error = "Type PARENTS to confirm"
                }
            }
        }
        dialog.show()
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTextColor(Color.rgb(69, 39, 160))
        setPadding(4, 4, 4, 2)
    }

    private fun spinner(items: List<String>): Spinner {
        val s = Spinner(this)
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        return s
    }

    private fun simpleSelection(action: (String) -> Unit) =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = parent?.getItemAtPosition(position)?.toString() ?: return
                action(selected)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

    private fun updateAnswerMode() {
        if (!::answerModeSpinner.isInitialized || !::inputRow.isInitialized) return
        val multipleChoice = answerModeSpinner.selectedItemPosition == 1
        inputRow.visibility = if (multipleChoice) View.GONE else View.VISIBLE
        choiceArea.visibility = if (multipleChoice) View.VISIBLE else View.GONE
        if (::nextButton.isInitialized && !answered) {
            nextButton.visibility = View.GONE
            retryButton.visibility = View.GONE
            skipButton.visibility = View.GONE
        }
    }

    private fun bTag(button: Button): Int = button.tag as Int

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
        val op = if (operation == "Mixed") listOf("Addition", "Subtraction", "Multiplication", "Division").random() else operation

        when (op) {
            "Addition" -> {
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
                val quotient = Random.nextInt(1, minOf(12, maxAnswer) + 1)
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
        // Re-enable the typing controls after every answer so the numeric keypad works on the next question.
        answerInput.isEnabled = true
        submitInputButton.isEnabled = true
        answerInput.setText("")
        answerInput.hint = if (language == "Hindi") "Type answer here" else "Type answer here"
        answerInput.clearFocus()
        feedbackView.text = if (language == "Hindi") "सही जवाब चुनो!" else "Solve the question!"
        resultView.visibility = View.GONE
        nextButton.visibility = View.GONE
        retryButton.visibility = View.GONE
        skipButton.visibility = View.GONE
        scoreView.text = "⭐ $score     🔥 $streak     Level $level (answer < $limit)"
        updateAnswerMode()
    }

    private fun checkAnswer(selected: Int) {
        if (answered) return
        if (selected == answer) {
            answered = true
            score++
            streak++
            choiceButtons.forEach { it.isEnabled = false }
            answerInput.isEnabled = false
            submitInputButton.isEnabled = false
            val message = if (language == "Hindi") "शाबाश! सही जवाब!" else "Great job! Correct!"
            feedbackView.text = if (language == "Hindi") "बहुत खूब! तुम्हारा मेडल!" else "Amazing! You earned a medal!"
            resultView.text = "🏅"
            resultView.setTextColor(Color.rgb(255, 152, 0))
            resultView.visibility = View.VISIBLE
            animateResult(medal = true)
            speak(if (language == "Hindi") "शाबाश! तुम्हारा मेडल!" else "Great job! You earned a medal!")
            nextButton.visibility = View.VISIBLE
            retryButton.visibility = View.GONE
            skipButton.visibility = View.GONE
            prefs.edit().putInt("score", score).putInt("level", level).apply()
        } else {
            streak = 0
            feedbackView.text = if (language == "Hindi") "कोई बात नहीं। फिर कोशिश करो!" else "Not quite. Try again or skip."
            resultView.text = "❌"
            resultView.setTextColor(Color.rgb(229, 57, 53))
            resultView.visibility = View.VISIBLE
            animateResult(medal = false)
            speak(if (language == "Hindi") "फिर कोशिश करो!" else "Try again!")
            retryButton.visibility = View.VISIBLE
            skipButton.visibility = View.VISIBLE
            nextButton.visibility = View.GONE
        }
        scoreView.text = "⭐ $score     🔥 $streak     Level $level (answer < ${resultLimitExclusive()})"
    }

    private fun retryQuestion() {
        answered = false
        resultView.visibility = View.GONE
        feedbackView.text = if (language == "Hindi") "फिर से कोशिश करो!" else "Try again. You can do it!"
        choiceButtons.forEach { it.isEnabled = true }
        answerInput.isEnabled = true
        submitInputButton.isEnabled = true
        answerInput.setText("")
        retryButton.visibility = View.GONE
        skipButton.visibility = View.GONE
    }

    private fun animateResult(medal: Boolean) {
        val scale = ScaleAnimation(0.2f, 1.15f, 0.2f, 1.15f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            .apply { duration = 450; repeatCount = 1; repeatMode = Animation.REVERSE }
        val rotate = RotateAnimation(if (medal) -25f else -8f, if (medal) 335f else 8f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            .apply { duration = if (medal) 700 else 180; repeatCount = if (medal) 0 else 2 }
        resultView.startAnimation(AnimationSet(true).apply { addAnimation(scale); addAnimation(rotate); fillAfter = false })
    }

    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        setStroke(2, Color.rgb(144, 164, 174))
    }

    private fun colorButton(label: String, color: Int, action: (() -> Unit)? = null): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.rgb(38, 50, 56))
            background = rounded(color, 18f)
            setPadding(6, 8, 6, 8)
            setOnClickListener { action?.invoke() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = 7 }

    private fun weighted() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
