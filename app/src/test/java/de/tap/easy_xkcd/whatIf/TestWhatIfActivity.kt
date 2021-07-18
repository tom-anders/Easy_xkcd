package de.tap.easy_xkcd.whatIf

import android.content.Intent
import android.net.Uri
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.tap.xkcd_reader.R
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.tap.easy_xkcd.whatIfArticleViewer.ArticleModelImpl
import de.tap.easy_xkcd.whatIfArticleViewer.WhatIfActivity
import de.tap.easy_xkcd.whatIfArticleViewer.WhatIfArticleViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class TestWhatIfActivity {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private val articleModel: ArticleModelImpl = mock()

    // Need to use a spy() instead of mock() here,
    // otherwise we run into https://stackoverflow.com/questions/54012481/crash-after-updating-to-fragment-testing-library-v1-1-0-alpha03
    @BindValue
    val viewModelSpy = spy(WhatIfArticleViewModel(articleModel, SavedStateHandle()))

    private lateinit var activityController: ActivityController<WhatIfActivity>

    @Before
    fun init() {
        activityController = Robolectric.buildActivity(WhatIfActivity::class.java)

        hiltRule.inject()
    }

    @Test
    fun actionBarTitle() {
        whenever(viewModelSpy.getTitle()).thenReturn(MutableLiveData("Test"))

        activityController.create().resume()

        assertThat(activityController.get().findViewById<Toolbar>(R.id.toolbar).subtitle).isEqualTo("Test")
    }

    @Test
    fun backAndForwardMenuButtons() {
        //TODO Use values via data driven tests
        whenever(viewModelSpy.hasNextArticle()).thenReturn(MutableLiveData(false))
        whenever(viewModelSpy.hasPreviousArticle()).thenReturn(MutableLiveData(false))

        activityController.create().resume()

        val toolbar = activityController.get().findViewById<Toolbar>(R.id.toolbar)
        activityController.get().onCreateOptionsMenu(toolbar.menu)
        activityController.get().onPrepareOptionsMenu(toolbar.menu)

        assertThat(toolbar.menu.findItem(R.id.action_back).isVisible).isEqualTo(false)
        assertThat(toolbar.menu.findItem(R.id.action_next).isVisible).isEqualTo(false)

        whenever(viewModelSpy.hasNextArticle()).thenReturn(MutableLiveData(true))
        whenever(viewModelSpy.hasPreviousArticle()).thenReturn(MutableLiveData(true))

        activityController.get().onPrepareOptionsMenu(toolbar.menu)

        assertThat(toolbar.menu.findItem(R.id.action_back).isVisible).isEqualTo(true)
        assertThat(toolbar.menu.findItem(R.id.action_next).isVisible).isEqualTo(true)
    }

    fun swipeHidesForwardAndBackIfEnabled() {
        activityController.create().resume()

        val toolbar = activityController.get().findViewById<Toolbar>(R.id.toolbar)
        activityController.get().onCreateOptionsMenu(toolbar.menu)
        activityController.get().onPrepareOptionsMenu(toolbar.menu)

        shadowOf(activityController.get()).clickMenuItem(R.id.action_swipe)
        assertThat(toolbar.menu.findItem(R.id.action_swipe).isChecked).isEqualTo(true)

        assertThat(toolbar.menu.findItem(R.id.action_back).isVisible).isEqualTo(false)
        assertThat(toolbar.menu.findItem(R.id.action_next).isVisible).isEqualTo(false)

        shadowOf(activityController.get()).clickMenuItem(R.id.action_swipe)
        assertThat(toolbar.menu.findItem(R.id.action_swipe).isChecked).isEqualTo(false)

        assertThat(toolbar.menu.findItem(R.id.action_back).isVisible).isEqualTo(true)
        assertThat(toolbar.menu.findItem(R.id.action_next).isVisible).isEqualTo(true)
    }

    fun menuItemsThatUseTheViewModel() {
        activityController.create().resume()

        val toolbar = activityController.get().findViewById<Toolbar>(R.id.toolbar)
        activityController.get().onCreateOptionsMenu(toolbar.menu)
        activityController.get().onPrepareOptionsMenu(toolbar.menu)

        shadowOf(activityController.get()).clickMenuItem(R.id.action_next)
        verify(viewModelSpy, times(1)).showNextArticle()

        shadowOf(activityController.get()).clickMenuItem(R.id.action_back)
        verify(viewModelSpy, times(1)).showPreviousArticle()

        val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("test"))
        whenever(viewModelSpy.openArticleInBrowser()).thenReturn(testIntent)
        shadowOf(activityController.get()).clickMenuItem(R.id.action_browser)
        assertThat(shadowOf(activityController.get()).peekNextStartedActivity()).isEqualTo(testIntent)

        testIntent.action = Intent.ACTION_SEND
        testIntent.data = Uri.parse("Another test")
        shadowOf(activityController.get()).clickMenuItem(R.id.action_share)
        assertThat(shadowOf(activityController.get()).peekNextStartedActivity()).isEqualTo(testIntent)

        shadowOf(activityController.get()).clickMenuItem(R.id.action_random)
        verify(viewModelSpy, times(1)).showRandomArticle()

        shadowOf(activityController.get()).clickMenuItem(R.id.action_favorite)
        verify(viewModelSpy, times(1)).toggleArticleFavorite()

        shadowOf(activityController.get()).clickMenuItem(R.id.action_thread)
        runBlocking { verify(viewModelSpy, times(1)).getRedditThread() }
    }
}