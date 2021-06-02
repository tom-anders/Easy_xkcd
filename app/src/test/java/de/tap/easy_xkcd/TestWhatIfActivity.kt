package de.tap.easy_xkcd

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.tap.xkcd_reader.R
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.testing.TestInstallIn
import de.tap.easy_xkcd.utils.Article
import de.tap.easy_xkcd.whatIfArticleViewer.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import javax.inject.Inject

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class TestWhatIfActivity {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

//    @BindValue
//    val mockViewModel = Mockito.mock(WhatIfArticleViewModel::class.java)

    val articleModel: ArticleModelImpl = mock(ArticleModelImpl::class.java)

    // Need to use a spy() instead of mock() here,
    // otherwise we run into https://stackoverflow.com/questions/54012481/crash-after-updating-to-fragment-testing-library-v1-1-0-alpha03
    @BindValue
    val myViewModel = spy(WhatIfArticleViewModel(articleModel, SavedStateHandle()))
//    val myViewModel: WhatIfArticleViewModel = WhatIfArticleViewModel(articleModel)

    private lateinit var activityController: ActivityController<WhatIfActivity>

    @Before
    fun init() {
        activityController = Robolectric.buildActivity(WhatIfActivity::class.java)

        hiltRule.inject()
    }

    @Test
    fun helloWorld() {
//        `when`(articleModel.getRef(anyString())).thenReturn("Hallo")

        val s = MutableLiveData<String>("Hallo")
        `when`(myViewModel.getTitle()).thenReturn(s)

        activityController.create().resume()

        val test = activityController.get().findViewById<Toolbar>(R.id.toolbar).subtitle

        assertThat(test).isEqualTo("Hallo")

//        assertThat(activityController.)
    }
}