package com.minimal.tus_flutter.upload

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.MethodChannel
import io.tus.java.client.ProtocolException
import io.tus.java.client.TusClient
import io.tus.java.client.TusExecutor
import io.tus.java.client.TusUpload
import java.io.File
import java.io.IOException
import java.lang.Exception

class Threaded(
        val client: TusClient,
        val endpointUrl: String,
        val uploadFilePath: String,
        val channel: MethodChannel,
        val chunkSize: Int) {
    private fun ret(key: String, args: HashMap<String, Any>?) {
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod(key, args)
        }
    }

    fun start() {
        Thread {
            val upload = TusUpload(File(uploadFilePath))
            var executor = object : TusExecutor() {
                override fun makeAttempt() {
                    val uploader = client.resumeOrCreateUpload(upload)
                    uploader.setChunkSize(chunkSize)
                    do {
                        val bytesUploaded = uploader.getOffset()
                        val totalBytes = upload.getSize()
                        val progress: Long = (bytesUploaded / totalBytes) * 100;

                        if (progress > 0) {
                            System.out.printf("Upload at %06.2f%%.\n", progress.toLong())
                        }
                        var out = HashMap<String, Any>()
                        out.put("endpointUrl", endpointUrl)
                        out.put("bytesWritten", bytesUploaded)
                        out.put("bytesTotal", totalBytes)

                        ret("progressChunk", out)
                    } while (uploader.uploadChunk() > -1)

                    uploader.finish()
                    var out = HashMap<String, Any>()
                    out.put("endpointUrl", endpointUrl)
                    out.put("resultUrl", uploader.uploadURL.toString())
                    ret("progressComplete", out)
                }
            }
            try {
                executor.makeAttempts()
            } catch (e: ProtocolException) {
                ret("uploadFail", null)
            } catch (e: IOException) {
                ret("uploadFail", null)
            } catch (e: Exception) {
                ret("uploadFail", null)
            }
        }.start()
    }
}
