package de.tap.easy_xkcd.database

import android.content.Context
import android.content.res.Resources
import app.cash.turbine.test
import de.tap.easy_xkcd.database.comics.*
import de.tap.easy_xkcd.explainXkcd.ExplainXkcdApi
import de.tap.easy_xkcd.utils.PrefHelper
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.*
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@ExperimentalCoroutinesApi
class TestComicRepository() {
    @get:Rule
    var mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var prefHelperMock: PrefHelper

    @Mock
    private lateinit var comicDaoMock: ComicDao

    @Mock
    private lateinit var contextMock: Context

    @Mock
    private lateinit var httpClientMock: OkHttpClient

    @Mock
    private lateinit var explainXkcdApiMock: ExplainXkcdApi

    @Mock
    private lateinit var xkcdApiMock: XkcdApi

    @Before
    fun init() {
        // TODO dont need this anymore when the class does not use the resources for this anymore
        val resources = mock<Resources>().apply {
            whenever(getIntArray(any())).thenReturn(IntArray(0))
        }
        whenever(contextMock.resources).thenReturn(resources)

        whenever(comicDaoMock.getFavorites()).thenReturn(flow { emit(emptyList<Comic>()) })
    }

    fun comicWithNumber(number: Int) = XkcdApiComic(number, "", "", "", "", "", "", "")

    @Test
    fun `Found latest comic without error, but number is same as cached`() = runTest {
        whenever(prefHelperMock.newest).thenReturn(123)
        whenever(xkcdApiMock.getNewestComic()).thenReturn(comicWithNumber(123))
        whenever(comicDaoMock.getComics()).thenReturn(flow { emit(emptyMap()) } )

        ComicRepositoryImpl(contextMock, prefHelperMock, comicDaoMock, httpClientMock, this, explainXkcdApiMock, xkcdApiMock)
            .comics.test {
                awaitItem() shouldHaveSize 123
                awaitComplete()
            }

        verify(prefHelperMock, never()).setNewestComic(any())
    }
}