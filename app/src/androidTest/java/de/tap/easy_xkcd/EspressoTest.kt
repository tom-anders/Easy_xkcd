package de.tap.easy_xkcd

import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.tap.xkcd_reader.R
import de.tap.easy_xkcd.mainActivity.MainActivity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class HelloWorldEspressoTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    // Regression test for #288
    @Test
    fun favoriteIconUpdatedWhenPressed() {
        onView(withId(R.id.action_favorite))
            .check(matches(Matchers.withActionIconDrawable(R.drawable.ic_favorite_off_24dp)))
            .perform(click())
            .check(matches(Matchers.withActionIconDrawable(R.drawable.ic_favorite_on_24dp)))
            .perform(click())
            .check(matches(Matchers.withActionIconDrawable(R.drawable.ic_favorite_off_24dp)))
    }

    @Test
    fun favoritesShowUpInOverview() {
        // Add current comic as favorite
        onView(withId(R.id.action_favorite))
            .perform(click())

        // ... and a random one
        onView(withId(R.id.fab))
            .perform(click())
        onView(withId(R.id.action_favorite))
            .perform(click())

        // Switch to overview and show only favorites
        onView(withId(R.id.nav_overview))
            .perform(click())
        onView(withId(R.id.action_favorite))
            .perform(click())

        onView(withId(R.id.rv))
            .check { view, _ ->
                val recyclerView = view as RecyclerView
                recyclerView.adapter?.itemCount shouldBe 2
            }

    }

}
