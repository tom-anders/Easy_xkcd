package de.tap.easy_xkcd.comicBrowsing

import app.cash.turbine.test
import de.tap.easy_xkcd.database.comics.Comic
import de.tap.easy_xkcd.database.comics.ComicContainer
import de.tap.easy_xkcd.database.comics.ComicRepository
import de.tap.easy_xkcd.utils.SharedPrefManager
import io.kotest.assertions.any
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@ExperimentalCoroutinesApi
class TestComicBrowserViewModel {
    @get:Rule
    var mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var repository: ComicRepository

    @Mock
    private lateinit var sharedPrefs: SharedPrefManager

    @Test
    fun `Jumps to last comic when initialized`() = runTest {
        whenever(sharedPrefs.lastComic).thenReturn(123)
        whenever(repository.comics).thenReturn(flowOf(emptyList()))

        val model = ComicBrowserViewModel(repository, sharedPrefs)

        model.selectedComicNumber.value shouldBe 123
    }

    @Test
    fun `Selects latest comic on first launch`() = runTest {
        val comics = listOf(Comic(1), Comic(2), Comic(3)).map { ComicContainer(it.number, it) }
        whenever(repository.comics).thenReturn(flowOf(comics))

        val model = ComicBrowserViewModel(repository, sharedPrefs)

        model.selectedComicNumber.value shouldBe comics.size
    }
}