package de.tap.easy_xkcd.database

import android.content.Context
import android.content.res.Resources
import app.cash.turbine.test
import de.tap.easy_xkcd.database.comics.Comic
import de.tap.easy_xkcd.database.comics.ComicDao
import de.tap.easy_xkcd.database.comics.ComicRepositoryImpl
import de.tap.easy_xkcd.explainXkcd.ExplainXkcdApi
import de.tap.easy_xkcd.utils.PrefHelper
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

    fun Comic.toJson() =
        JSONObject().apply {
            put("num", number)
            put("title", title)
            put("img", url)
            put("alt", altText)
            put("transcript", transcript)
        }

    private fun returnComicFromNextResponse(comic: Comic?) {
        var body: ResponseBody? = null
        if (comic != null) {
            body = mock<ResponseBody>().apply {
                whenever(string()).thenReturn(comic.toJson().toString())
            }
        }

        val call = mock<Call>().apply {
            whenever(this.enqueue(any())).then {
                (it.arguments.first() as Callback).onResponse(this, mock<Response>().apply {
                    whenever(this.body).thenReturn(body)
                })
            }
        }

        whenever(httpClientMock.newCall(any())).thenReturn(call)
    }

    @Test
    fun `Repository queries latest comic and emits its number`() = runTest {
        val initialNewest = 123
        whenever(prefHelperMock.newest).thenReturn(initialNewest)

        val newComic = Comic(456)
        returnComicFromNextResponse(newComic)

        val repository = ComicRepositoryImpl(contextMock, prefHelperMock, comicDaoMock, httpClientMock, this, explainXkcdApiMock, xkcdApiMock)

        repository.newestComicNumber.test {
            awaitItem() shouldBe initialNewest
            awaitItem() shouldBe newComic.number
        }

        verify(prefHelperMock).setNewestComic(newComic.number)
    }

    @Test
    fun `Number of comics in the list is equal to the number of the latest comic`() = runTest {
//        whenever(comicDaoMock.getComics()).thenReturn(flowOf(emptyMap()))
//        val repository = ComicRepositoryImpl(contextMock, prefHelperMock,
//            Room.databaseBuilder(ApplicationProvider.getApplicationContext(), ComicRoomDatabase::class.java, "comic_database").build().comicDao(),
//            httpClientMock, this)


//        repository.comics.test {
//            assertThat(awaitItem().size).isEqualTo(0)
//            awaitItem().shouldBeEmpty()
//            awaitItem()
//        }
    }
}