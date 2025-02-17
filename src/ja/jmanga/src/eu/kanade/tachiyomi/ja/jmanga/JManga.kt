package eu.kanade.tachiyomi.extension.ja.jmanga

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class JManga : WPComics("JManga", "https://jmanga.vip", "ja", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.JAPANESE), null) {
    override fun popularMangaSelector() = "div.items article.item"
    override fun popularMangaNextPageSelector() = "li:nth-last-child(2) a.page-link"
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("article#item-detail").let { info ->
                author = info.select("li.author p.col-xs-8").text()
                status = when {
                    info.select("li.status p.col-xs-8").text().contains("連載中", true) -> SManga.ONGOING
                    info.select("li.status p.col-xs-8").text().contains("完結済み", true) -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
                genre = info.select("li.kind p.col-xs-8 a").joinToString { it.text() }
                description = info.select("div.detail-content").text()
                thumbnail_url = imageOrNull(info[0].selectFirst("div.col-image img")!!)
            }
        }
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.let { if (it.isEmpty()) getFilterList() else it }
        val url = "$baseUrl/search/manga".toHttpUrl().newBuilder()

        filterList.forEach { filter ->
            when (filter) {
                is GenreFilter -> filter.toUriPart()?.let { url.addQueryParameter("genre", it) }
                is StatusFilter -> filter.toUriPart()?.let { url.addQueryParameter("status", it) }
                else -> {}
            }
        }

        url.apply {
            addQueryParameter(queryParam, query)
            addQueryParameter("page", page.toString())
            addQueryParameter("sort", "-1")
        }

        return GET(url.build(), headers)
    }
    override fun chapterFromElement(element: Element): SChapter {
        val minuteWords = listOf("minute", "分")
        val hourWords = listOf("hour", "時間")
        val dayWords = listOf("day", "日")
        val weekWords = listOf("week", "週間")
        val monthWords = listOf("month", "月")
        val chapterDate = element.select("div.col-xs-4").text()

        return SChapter.create().apply {
            element.select("a").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            try {
                val trimmedDate = chapterDate.substringBefore("前").split(" ")
                val calendar = Calendar.getInstance()

                when {
                    monthWords.any {
                        trimmedDate[1].contains(
                            it,
                            ignoreCase = true,
                        )
                    } -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }

                    weekWords.any {
                        trimmedDate[1].contains(
                            it,
                            ignoreCase = true,
                        )
                    } -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }

                    dayWords.any {
                        trimmedDate[1].contains(
                            it,
                            ignoreCase = true,
                        )
                    } -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }

                    hourWords.any {
                        trimmedDate[1].contains(
                            it,
                            ignoreCase = true,
                        )
                    } -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }

                    minuteWords.any {
                        trimmedDate[1].contains(
                            it,
                            ignoreCase = true,
                        )
                    } -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
                }

                date_upload = calendar.timeInMillis
            } catch (_: Exception) {
                date_upload = 0L
            }
        }
    }
    override fun getStatusList(): Array<Pair<String?, String>> {
        return arrayOf(
            Pair("-1", "全て"),
            Pair("0", "完結済み"),
            Pair("1", "連載中"),
        )
    }
    override fun getGenreList(): Array<Pair<String?, String>> {
        return arrayOf(
            null to "全てのジャンル",
            "TL" to "TL",
            "BL" to "BL",
            " ファンタジー " to " ファンタジー ",
            "恋愛" to "恋愛",
            "ドラマ" to "ドラマ",
            "アクション" to "アクション",
            "ホラー・ミステリー" to "ホラー・ミステリー",
            "裏社会・アングラ" to "裏社会・アングラ",
            "スポーツ" to "スポーツ",
            "グルメ" to "グルメ",
            "日常" to "日常",
            "SF" to "SF",
        )
    }
}
