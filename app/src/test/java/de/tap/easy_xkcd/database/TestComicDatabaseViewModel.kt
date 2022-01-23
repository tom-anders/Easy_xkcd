package de.tap.easy_xkcd.database

import android.app.Application
import com.google.common.truth.Truth.assertThat
import de.tap.easy_xkcd.CoroutinesTestRule
import de.tap.easy_xkcd.comicBrowsing.ComicDatabaseModel
import de.tap.easy_xkcd.mainActivity.ComicDatabaseViewModel
import de.tap.easy_xkcd.utils.PrefHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.*
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class TestComicDatabaseViewModel(
    private val previousNewestComic: Int,
    private val newestComicToBeFound: Int,
    private val shouldTriggerNewComicFound: Boolean,
) {
    @get:Rule
    var mockitoRule: MockitoRule = MockitoJUnit.rule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutinesTestRule()

    @Mock
    private lateinit var databaseModelMock: ComicDatabaseModel

    @Mock
    private lateinit var prefHelperMock: PrefHelper

    @Mock
    private lateinit var appMock: Application

    @ExperimentalCoroutinesApi
    @Test
    fun doesNotTriggerUpdateWhenOffline() = coroutinesTestRule.testDispatcher.runBlockingTest {
        whenever(prefHelperMock.isOnline(anyOrNull())).thenReturn(false)

        val viewModel = ComicDatabaseViewModel(appMock, databaseModelMock, prefHelperMock)

        verify(databaseModelMock, never()).updateDatabase(any(), any())

        assertThat(viewModel.initialized.value).isEqualTo(true)
        assertThat(viewModel.progress.value).isEqualTo(null)
        assertThat(viewModel.foundNewComic.value).isEqualTo(null)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(
            name = "previousNewest: {0}, newestToBeFound: {1}, shouldTriggerNewComioFound: {2} ")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf(123, 123, false),
                arrayOf(123, 100, false), // Should never happen in practise
                arrayOf(123, 124, true),
                arrayOf(123, 133, true),
            )
        }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun updatingDatabase() = coroutinesTestRule.testDispatcher.runBlockingTest {
        whenever(prefHelperMock.isOnline(anyOrNull())).thenReturn(true)
        whenever(databaseModelMock.findNewestComic()).thenReturn(newestComicToBeFound)
        whenever(prefHelperMock.newest).thenReturn(previousNewestComic)

        val viewModel = ComicDatabaseViewModel(appMock, databaseModelMock, prefHelperMock)

        assertThat(viewModel.progressMax).isEqualTo(newestComicToBeFound)

        if (shouldTriggerNewComicFound) {
            verify(prefHelperMock, times(1)).setNewestComic(newestComicToBeFound)
            assertThat(viewModel.foundNewComic.value).isEqualTo(true)
        } else {
            assertThat(viewModel.foundNewComic.value).isNotEqualTo(true)
        }

        verify(databaseModelMock, times(1)).updateDatabase(eq(newestComicToBeFound), any())

        assertThat(viewModel.initialized.value).isEqualTo(true)
        assertThat(viewModel.progress.value).isEqualTo(null)
    }
}
