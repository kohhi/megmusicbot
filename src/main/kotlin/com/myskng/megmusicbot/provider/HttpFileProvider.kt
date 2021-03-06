package com.myskng.megmusicbot.provider

import com.myskng.megmusicbot.bot.music.RawOpusStreamProvider
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.io.IOException
import java.util.logging.Level

class HttpFileProvider(rawOpusStreamProvider: RawOpusStreamProvider, private val url: String) : KoinComponent,
    AbstractFileProvider(rawOpusStreamProvider) {
    companion object {
        const val httpBufferSize = 1024 * 256
    }

    private val okHttpClient by inject<OkHttpClient>()

    override fun fetchOriginStream() = GlobalScope.async(coroutineContext) {
        try {
            logger.log(Level.INFO, "[HTTPFile] Download start. address=$url")
            val request = Request.Builder().url(url).build()
            val httpResponse = okHttpClient.newCall(request).execute()
            httpResponse.use {
                if (it.isSuccessful.not()) {
                    throw IOException("HTTP(S) connection fault with response code ${it.code}.(URL=$url)")
                }
                val stream = it.body!!.byteStream()
                val streamSource = stream.source()
                val streamBuffer = streamSource.buffer()
                if (!streamBuffer.exhausted()) {
                    isOriginStreamAlive = true
                }
                while (streamBuffer.exhausted().not() && isActive) {
                    if (streamBuffer.request(httpBufferSize.toLong())) {
                        originStreamQueue.send(streamBuffer.readByteArray(httpBufferSize.toLong()))
                    } else {
                        originStreamQueue.send(streamBuffer.readByteArray())
                    }
                }
                originStreamQueue.send(byteArrayOf())
                logger.log(Level.INFO, "[HTTPFile] Downloaded all bytes.")
            }
        } catch (ex: Exception) {
            logger.log(Level.SEVERE, "[HTTPFile] $ex")
            cleanupOnError()
            reportError(ex)
        }
    }
}