package com.jaycefr.jayto.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.jaycefr.jayto.data.local.dao.SongDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumArtFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao
) {
    private val client = OkHttpClient()
    private val userAgent = "JaytoMusic/1.0 ( jayce@example.com )" // Required by MusicBrainz

    suspend fun searchArtUrls(query: String): List<String> = withContext(Dispatchers.IO) {
        val urls = mutableListOf<String>()
        
        // Strategy 1: MusicBrainz + Cover Art Archive
        try {
            Log.d("JAYTO_SEARCH", "Original Song Title: $query")
            val musicBrainzUrl = "https://musicbrainz.org/ws/2/recording/?query=recording:\"${Uri.encode(query)}\"&fmt=json"
            Log.d("JAYTO_SEARCH", "MusicBrainz Query URL: $musicBrainzUrl")

            val request = Request.Builder()
                .url(musicBrainzUrl)
                .header("User-Agent", userAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val recordings = json.optJSONArray("recordings")
                    
                    for (i in 0 until (recordings?.length() ?: 0)) {
                        val recording = recordings?.optJSONObject(i)
                        val releases = recording?.optJSONArray("releases")
                        
                        for (j in 0 until (releases?.length() ?: 0)) {
                            val release = releases?.optJSONObject(j)
                            val releaseId = release?.optString("id")
                            
                            if (!releaseId.isNullOrEmpty()) {
                                // Fetch cover art for this release
                                val coverArtUrl = "https://coverartarchive.org/release/$releaseId/front"
                                // We check if the image exists by doing a HEAD request or just adding it if it's likely to exist
                                // CAA redirects to the actual image
                                urls.add(coverArtUrl)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AlbumArtFetcher", "MusicBrainz error", e)
        }

        // Strategy 2: Fallback to Google Images if MusicBrainz yields nothing or we want more variety
        if (urls.isEmpty()) {
            try {
                val fullQuery = "$query album art"
                val searchUrl = "https://www.google.com/search?q=${Uri.encode(fullQuery)}&tbm=isch"
                Log.d("JAYTO_SEARCH", "Fallback Google URL: $searchUrl")

                val document = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                    .get()

                document.select("img[data-src]").forEach {
                    val src = it.attr("data-src")
                    if (src.startsWith("http")) urls.add(src)
                }

                document.select("img[src]").forEach {
                    val src = it.attr("src")
                    if (src.startsWith("http") && !src.contains("favicon")) {
                        urls.add(src)
                    }
                }
            } catch (e: Exception) {
                Log.e("AlbumArtFetcher", "Google fallback error", e)
            }
        }

        Log.d("AlbumArtFetcher", "Found ${urls.size} images total")
        return@withContext urls.distinct().take(15)
    }

    suspend fun downloadAndSaveArt(songId: Long, imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(imageUrl).build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body ?: return@withContext null
                val fileName = "album_art_${songId}_${System.currentTimeMillis()}.jpg"
                val dir = File(context.getExternalFilesDir(null), "album_art")
                if (!dir.exists()) dir.mkdirs()
                
                val imageFile = File(dir, fileName)
                FileOutputStream(imageFile).use { out ->
                    body.byteStream().copyTo(out)
                }

                val artworkUri = Uri.fromFile(imageFile).toString()
                songDao.updateArtworkUri(songId, artworkUri)
                return@withContext artworkUri
            }
        } catch (e: Exception) {
            Log.e("AlbumArtFetcher", "Error downloading art", e)
        }
        return@withContext null
    }

    suspend fun fetchAndSaveArt(songId: Long, query: String): Boolean {
        val urls = searchArtUrls(query)
        if (urls.isNotEmpty()) {
            return downloadAndSaveArt(songId, urls.first()) != null
        }
        return false
    }
}
