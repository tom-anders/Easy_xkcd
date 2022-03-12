package de.tap.easy_xkcd.whatIf

import android.content.Intent
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import de.tap.easy_xkcd.whatIfArticleViewer.ArticleModel
import de.tap.easy_xkcd.whatIfArticleViewer.WhatIfActivity
import de.tap.easy_xkcd.whatIfArticleViewer.WhatIfArticleViewModel
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner


@RunWith(RobolectricTestRunner::class)
class TestWhatIfArticleViewModel {
//    @get:Rule // Needed for Single/Observable
//    var instantTaskExecutorRule: TestRule = InstantTaskExecutorRule()
//
//    @get:Rule
//    var mockitoRule: MockitoRule = MockitoJUnit.rule()
//
//    private lateinit var viewModelUnderTest: WhatIfArticleViewModel
//
//    @Mock
//    private lateinit var mockModel: ArticleModel
//
//    @Before
//    fun init() {
//    }
//
//    private fun makeValidSavedStateHandle(number: Int): SavedStateHandle {
//        val handle = SavedStateHandle()
//        handle.set(WhatIfActivity.INTENT_NUMBER, number)
//        return handle
//    }
//
//    private fun initViewModel(savedStateHandle: SavedStateHandle) {
//        viewModelUnderTest = WhatIfArticleViewModel(mockModel, savedStateHandle)
//    }
//
//    private fun initViewModelWithEmptyArticle(number: Int = 123) {
//        runBlocking {
//            whenever(mockModel.loadArticle(anyInt())).thenReturn("")
//            initViewModel(makeValidSavedStateHandle(number))
//        }
//    }
//
//    @Test
//    fun hasNextAndPreviousArticle() {
//        whenever(mockModel.hasNextArticle()).thenReturn(true)
//        whenever(mockModel.hasPreviousArticle()).thenReturn(true)
//
//        initViewModelWithEmptyArticle()
//
//        assertThat(viewModelUnderTest.hasNextArticle().value).isEqualTo(true)
//        assertThat(viewModelUnderTest.hasPreviousArticle().value).isEqualTo(true)
//    }
//
//    @Test
//    fun isFavorite() {
//        whenever(mockModel.isArticleFavorite()).thenReturn(true)
//        initViewModelWithEmptyArticle()
//        assertThat(viewModelUnderTest.isFavorite().value).isEqualTo(true)
//
//        whenever(mockModel.isArticleFavorite()).thenReturn(false)
//        initViewModelWithEmptyArticle()
//        assertThat(viewModelUnderTest.isFavorite().value).isEqualTo(false)
//    }
//
//    @Test
//    fun invalidSavedStateHandleDoesNotTriggerUpdate() {
//        initViewModel(SavedStateHandle())
//
//        val invalidSavedStateHandle = SavedStateHandle()
//        invalidSavedStateHandle.set("Some key that is not the one we're looking for", 123)
//        initViewModel(invalidSavedStateHandle)
//
//        invalidSavedStateHandle.set(WhatIfActivity.INTENT_NUMBER, "Not a number")
//        initViewModel(invalidSavedStateHandle)
//
//        runBlocking {
//            verify(mockModel, never()).loadArticle(anyInt())
//        }
//    }
//
//    @Test
//    fun loadValidArticle() {
//        val testHtml = "Here, have some html"
//        runBlocking {
//            whenever(mockModel.loadArticle(anyInt())).thenReturn(testHtml)
//        }
//
//        val testTitle = "Test Title"
//        whenever(mockModel.getTitle()).thenReturn(testTitle)
//
//        initViewModel(makeValidSavedStateHandle(123))
//
//        assertThat(viewModelUnderTest.getTitle().value).isEqualTo(testTitle)
//        assertThat(viewModelUnderTest.getArticleHtml().value).isEqualTo(testHtml)
//    }
//
//    @Test
//    fun showNextAndPreviousArticle() {
//        val testNumber = 123
//        initViewModelWithEmptyArticle(testNumber)
//        whenever(mockModel.getNumber()).thenReturn(testNumber)
//
//        runBlocking {
//            viewModelUnderTest.showNextArticle()
//            verify(mockModel, times(1)).loadArticle(testNumber + 1)
//
//            viewModelUnderTest.showPreviousArticle()
//            verify(mockModel, times(1)).loadArticle(testNumber - 1)
//        }
//    }
//
//    @Test
//    fun showRandomArticle() {
//        val randomNumber = 4 // Chosen by fair dice roll
//        whenever(mockModel.getRandomArticleNumber()).thenReturn(randomNumber)
//
//        initViewModelWithEmptyArticle()
//
//        viewModelUnderTest.showRandomArticle()
//
//        runBlocking {
//            verify(mockModel, times(1)).loadArticle(randomNumber)
//        }
//    }
//
//    @Test
//    fun toggleArticleFavorite() {
//        initViewModelWithEmptyArticle()
//
//        whenever(mockModel.isArticleFavorite()).thenReturn(true)
//        viewModelUnderTest.toggleArticleFavorite()
//        verify(mockModel, times(1)).toggleArticleFavorite()
//        assertThat(viewModelUnderTest.isFavorite().value).isEqualTo(true)
//        reset(mockModel)
//
//        whenever(mockModel.isArticleFavorite()).thenReturn(false)
//        viewModelUnderTest.toggleArticleFavorite()
//        verify(mockModel, times(1)).toggleArticleFavorite()
//        assertThat(viewModelUnderTest.isFavorite().value).isEqualTo(false)
//    }
//
//    @Test
//    fun openArticleInBrowser() {
//        initViewModelWithEmptyArticle()
//
//        whenever(mockModel.getNumber()).thenReturn(123)
//
//        val intent = viewModelUnderTest.openArticleInBrowser()
//
//        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
//        assertThat(intent.data).isEqualTo(Uri.parse("https://what-if.xkcd.com/123"))
//    }
//
//    @Test
//    fun shareArticle() {
//        initViewModelWithEmptyArticle()
//
//        whenever(mockModel.getTitle()).thenReturn("Test")
//        whenever(mockModel.getNumber()).thenReturn(123)
//
//        val intent = viewModelUnderTest.shareArticle()
//        assertThat(intent.action).isEqualTo(Intent.ACTION_SEND)
//        assertThat(intent.type).isEqualTo("text/plain")
//        assertThat(intent.getStringExtra(Intent.EXTRA_SUBJECT)).contains("Test")
//        assertThat(intent.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo("https://what-if.xkcd.com/123")
//    }
//
//    //TODO Test the bad case where this throws somewhere
//    @Test
//    fun openRedditThread() {
//        mockModel.stub { onBlocking { getRedditThread() }.doReturn("nase.de") }
//
//        initViewModelWithEmptyArticle()
//
//        runBlocking {
//            assertThat(viewModelUnderTest.getRedditThread()).isEqualTo("nase.de")
//        }
//    }
}