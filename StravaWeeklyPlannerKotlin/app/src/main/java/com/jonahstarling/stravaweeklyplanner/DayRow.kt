package com.jonahstarling.stravaweeklyplanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.preference.PreferenceManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import com.google.firebase.database.*
import org.jetbrains.annotations.Nullable
import kotlin.math.roundToInt


class DayRow : ConstraintLayout {

    private lateinit var firebaseDatabaseRef: DatabaseReference
    private lateinit var date: String

    constructor(context: Context) : super(context) { init(null) }
    constructor(context: Context, attrSet: AttributeSet) : super(context, attrSet) { init(attrSet) }
    constructor(context: Context, attrSet: AttributeSet, defStyleAttr: Int) : super(context, attrSet, defStyleAttr) { init(attrSet) }

    private fun ConstraintSet.match(view: View, parentView: View) {
        this.connect(view.id, ConstraintSet.TOP, parentView.id, ConstraintSet.TOP)
        this.connect(view.id, ConstraintSet.START, parentView.id, ConstraintSet.START)
        this.connect(view.id, ConstraintSet.END, parentView.id, ConstraintSet.END)
        this.connect(view.id, ConstraintSet.BOTTOM, parentView.id, ConstraintSet.BOTTOM)
    }

    private fun init(@Nullable attributeSet: AttributeSet?) {
        val view = LayoutInflater.from(context).inflate(R.layout.day_row, this, false)
        val set = ConstraintSet()
        addView(view)
        set.clone(this)
        set.match(view, this)

        firebaseDatabaseRef = FirebaseDatabase.getInstance().reference

        val dayTitle = findViewById<TextView>(R.id.day_title)
        val goalInput = findViewById<EditText>(R.id.goal_input)
        goalInput.onFocusChangeListener = InputFocusChangeListener()

        val ta = context.obtainStyledAttributes(attributeSet, R.styleable.DayRow)
        date = ta.getText(R.styleable.DayRow_day_title).toString()
        dayTitle.text = date
        ta.recycle()

        goalInput.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KEYCODE_ENTER) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm!!.hideSoftInputFromWindow(goalInput.windowToken, 0)
                goalInput.isFocusable = false
                goalInput.isFocusableInTouchMode = true
                true
            } else {
                false
            }
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        var isMeasurementPreferenceMiles = preferences.getBoolean("isMeasurementPreferenceMiles", true)
        val id = preferences.getString("athlete_id", "")
        goalInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                if (goalInput.hasFocus()) {
                    id?.let {
                        isMeasurementPreferenceMiles = preferences.getBoolean("isMeasurementPreferenceMiles", true)
                        var goalInputValue = p0?.toString()
                        var goalValue = goalInputValue?.toDoubleOrNull()
                        if (goalValue != null) {
                            if (isMeasurementPreferenceMiles) {
                                goalValue *= 1.60934
                            }
                            goalInputValue = roundDistance(goalValue)
                        }
                        firebaseDatabaseRef.child("athletes").child(id).child(date).setValue(goalInputValue)
                    }
                }
            }
        })

        getGoalFromFirebase()
    }

    private fun getGoalFromFirebase() {
        val goalInput = findViewById<EditText>(R.id.goal_input)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val isMeasurementPreferenceMiles = preferences.getBoolean("isMeasurementPreferenceMiles", true)
        val id = preferences.getString("athlete_id", "")

        val goalListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                id?.let {
                    var goalDistance = dataSnapshot.child("athletes").child(id).child(date).value.toString()
                    var goalValue = goalDistance.toDoubleOrNull()
                    if (goalValue != null) {
                        if (isMeasurementPreferenceMiles) {
                            goalValue *= 0.621371
                        }
                        goalDistance = roundDistance(goalValue)
                    }
                    goalInput.setText(goalDistance)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w("GoalListener", "onCancelled", databaseError.toException())
            }
        }
        firebaseDatabaseRef.addListenerForSingleValueEvent(goalListener)
    }

    fun setDaysActivityData(data: HashMap<String, String>) {
        val actualMileage = findViewById<EditText>(R.id.actual_mileage)
        val statBoxOne = findViewById<TextView>(R.id.stat_box_one)
        val statBoxTwo = findViewById<TextView>(R.id.stat_box_two)
        val statBoxThree = findViewById<TextView>(R.id.stat_box_three)

        actualMileage.setText(data["actualMileage"])
        statBoxOne.text = data["statBoxOne"]
        statBoxTwo.text = data["statBoxTwo"]
        val activityUrl = data["activityUrl"]
        if (activityUrl != null && activityUrl != "") {
            statBoxThree.text = resources.getText(R.string.view_on_strava)
            statBoxThree.setOnClickListener {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(activityUrl))
                startActivity(context, browserIntent, null)
            }
        } else {
            statBoxThree.text = resources.getText(R.string.no_activity_recorded)
        }
        getGoalFromFirebase()

        invalidate()
    }

    fun hideKeyboard(view: View) {
        context?.let {
            val inputMethodManager = ContextCompat.getSystemService(
                it,
                InputMethodManager::class.java
            )!!
            inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    fun roundDistance(mileageConverted: Double): String {
        var mileageString = mileageConverted.toString()
        when {
            mileageConverted > 100.0 -> mileageString = mileageConverted.roundToInt().toString()
            mileageConverted > 10.0 -> mileageString = "%.1f".format(mileageConverted)
            mileageConverted > 0.0 -> mileageString = "%.2f".format(mileageConverted)
        }
        return mileageString
    }

    inner class InputFocusChangeListener: View.OnFocusChangeListener {
        override fun onFocusChange(v: View?, hasFocus: Boolean) {
            if (!hasFocus) {
                v?.let {
                    hideKeyboard(it)
                }
            }
        }
    }
}