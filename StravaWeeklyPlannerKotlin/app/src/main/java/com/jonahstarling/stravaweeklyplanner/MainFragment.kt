package com.jonahstarling.stravaweeklyplanner

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.graphics.Rect
import android.preference.PreferenceManager
import android.support.constraint.ConstraintLayout
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.content.ContextCompat.getSystemService
import android.view.LayoutInflater
import android.widget.EditText
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import com.android.volley.Response
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.roundToInt


class MainFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_main, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val logoImage = view.findViewById<ImageView>(R.id.logo)
        val profileImage = view.findViewById<CircleImageView>(R.id.profile_image)
        val monday = view.findViewById<DayRow>(R.id.monday_row)
        val tuesday = view.findViewById<DayRow>(R.id.tuesday_row)
        val wednesday = view.findViewById<DayRow>(R.id.wednesday_row)
        val thursday = view.findViewById<DayRow>(R.id.thursday_row)
        val friday = view.findViewById<DayRow>(R.id.friday_row)
        val saturday = view.findViewById<DayRow>(R.id.saturday_row)
        val sunday = view.findViewById<DayRow>(R.id.sunday_row)

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val profile = preferences.getString("profile", "")
        val id = preferences.getString("athlete_id", "") ?: ""
        val accessToken = preferences.getString("access_token", "") ?: ""

        if (!profile.isNullOrEmpty()) {
            Glide.with(this@MainFragment).load(profile).into(profileImage)
        }

        val mainAnimator = ValueAnimator.ofFloat(0.0f, 1.0f)
        mainAnimator.duration = 500L
        mainAnimator.startDelay = 500L
        mainAnimator.interpolator = LinearInterpolator()
        mainAnimator.addUpdateListener {
            monday.alpha = mainAnimator.animatedValue as Float
            tuesday.alpha = mainAnimator.animatedValue as Float
            wednesday.alpha = mainAnimator.animatedValue as Float
            thursday.alpha = mainAnimator.animatedValue as Float
            friday.alpha = mainAnimator.animatedValue as Float
            saturday.alpha = mainAnimator.animatedValue as Float
            sunday.alpha = mainAnimator.animatedValue as Float

            val mondayParams = monday.layoutParams as ConstraintLayout.LayoutParams
            mondayParams.verticalBias = 0.1f - ((mainAnimator.animatedValue as Float) / 10.0f)
            monday.layoutParams = mondayParams
        }
        mainAnimator.start()

        fetchAthletesActivities(id, accessToken)
    }

    private fun fetchAthletesActivities(id: String, accessToken: String) {
        val requestQueue = Volley.newRequestQueue(context)
        val url = "https://www.strava.com/api/v3/athletes/$id/activities"
        val request = object: JsonArrayRequest(
            Method.GET, url, null,
            Response.Listener<JSONArray> { response ->
                parseActivities(response)
            },
            Response.ErrorListener {
                Toast.makeText(context, "There was a problem loading your activities. Please try again later.", Toast.LENGTH_LONG).show()
            }) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Authorization"] = "Bearer $accessToken"
                return headers
            }
        }
        requestQueue.add(request)
    }

    private fun parseActivities(activities: JSONArray) {
        val thisWeeksActivities = HashMap<String, JSONArray>()
        thisWeeksActivities["Monday"] = JSONArray()
        thisWeeksActivities["Tuesday"] = JSONArray()
        thisWeeksActivities["Wednesday"] = JSONArray()
        thisWeeksActivities["Thursday"] = JSONArray()
        thisWeeksActivities["Friday"] = JSONArray()
        thisWeeksActivities["Saturday"] = JSONArray()
        thisWeeksActivities["Sunday"] = JSONArray()

        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.clear(Calendar.MINUTE)
        calendar.clear(Calendar.SECOND)
        calendar.clear(Calendar.MILLISECOND)

        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek + 1) // Add one to get Monday (the true first day of the week)

        val beginningOfWeekMillis = calendar.timeInMillis

        var allFromThisWeekFound = false
        var i = 0
        while (i < activities.length() && !allFromThisWeekFound) {
            val activity = activities.getJSONObject(i)

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
            val activityDate = LocalDate.parse(activity.getString("start_date_local"), formatter)
            val activityMillis = activityDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (activityMillis >= beginningOfWeekMillis) {
                val activityDayOfWeek = activityDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                val daysActivities = thisWeeksActivities[activityDayOfWeek] as JSONArray
                daysActivities.put(activity)
            } else {
                allFromThisWeekFound = true
            }
            i +=1
        }

        renderActivities(thisWeeksActivities)
    }

    private fun renderActivities(thisWeeksActivities: HashMap<String, JSONArray>) {
        // Monday
        thisWeeksActivities["Monday"]?.let {
            val dailyTotals = calculateDailyTotals(it)
            view?.findViewById<DayRow>(R.id.monday_row)?.setDaysActivityData(dailyTotals)
        }

        // Tuesday
        thisWeeksActivities["Tuesday"]?.let {
            val dailyTotals = calculateDailyTotals(it)
            view?.findViewById<DayRow>(R.id.tuesday_row)?.setDaysActivityData(dailyTotals)
        }

        // Wednesday
        thisWeeksActivities["Wednesday"]?.let {
            val dailyTotals = calculateDailyTotals(it)
            view?.findViewById<DayRow>(R.id.wednesday_row)?.setDaysActivityData(dailyTotals)
        }

        // Thursday
        thisWeeksActivities["Thursday"]?.let {
            val dailyTotals = calculateDailyTotals(it)
            view?.findViewById<DayRow>(R.id.thursday_row)?.setDaysActivityData(dailyTotals)
        }

        // Friday
        thisWeeksActivities["Friday"]?.let {
            val dailyTotals = calculateDailyTotals(it)
            view?.findViewById<DayRow>(R.id.friday_row)?.setDaysActivityData(dailyTotals)
        }

        // Saturday
        thisWeeksActivities["Saturday"]?.let {
            val dailyTotals = calculateDailyTotals(it)
            view?.findViewById<DayRow>(R.id.saturday_row)?.setDaysActivityData(dailyTotals)
        }

        // Sunday
        thisWeeksActivities["Sunday"]?.let {
            val dailyTotals = calculateDailyTotals(it)
            view?.findViewById<DayRow>(R.id.sunday_row)?.setDaysActivityData(dailyTotals)
        }
    }

    private fun calculateDailyTotals(dayActivities: JSONArray): HashMap<String, String> {
        val dailyTotals = HashMap<String, String>()
        var mileage = 0L
        var time = 0L
        var climb = 0.0f

        for (i in 0 until dayActivities.length()) {
            val activity = dayActivities[i] as JSONObject
            mileage += activity.getLong("distance")
            time += activity.getLong("moving_time")
            climb += activity.getDouble("total_elevation_gain").toFloat()
        }

        var mileageString = "0"
        val mileageInKm = mileage / 1000.0f
        when {
            mileageInKm > 100.0f -> mileageString = mileageInKm.roundToInt().toString()
            mileageInKm > 10.0f -> mileageString = "%.1f".format(mileageInKm)
            mileageInKm > 0.0f -> mileageString = "%.2f".format(mileageInKm)
        }

        dailyTotals["actualMileage"] = mileageString
        dailyTotals["statBoxOne"] = "${time / 60}m ${time % 60}s"
        dailyTotals["statBoxTwo"] = "${climb}m"

        return dailyTotals
    }

    companion object {
        val TAG = MainFragment::class.java.simpleName

        fun newInstance() = MainFragment()
    }
}