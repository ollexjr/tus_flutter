package com.minimal.tus_flutter.service

import android.app.*
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import io.tus.android.client.TusPreferencesURLStore
import io.tus.java.client.ProtocolException
import io.tus.java.client.TusClient
import io.tus.java.client.TusExecutor
import io.tus.java.client.TusUpload
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.net.MalformedURLException
import java.net.URL

class Upload(
        appContext: Context, workerParams: WorkerParameters) :
        CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                    NotificationManager
    private val CHANNEL_UPLOAD = "upload";
    private val CHANNEL_UPLOAD_SUCCESS = "upload_success";
    private val CHANNEL_UPLOAD_FAILED = "upload_failed";


    // create foreground notification to alert user on upload progress
    // and stop android killing the upload job
    private fun createForegroundInfo(progress: String, fileName: String): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(CHANNEL_UPLOAD, "Uploads", importance)
            mChannel.description = "Uploads"

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notificationManager.createNotificationChannel(mChannel)
        }

        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_UPLOAD)
                .setContentTitle("${progress}... ${fileName}")
                .setTicker("${progress}... ${fileName}")
                .setOnlyAlertOnce(true)
                .setContentText(progress)
                .setSmallIcon(R.drawable.notification_bg_normal)
                .setOngoing(true)
                .build()
        return ForegroundInfo(fileName.hashCode(), notification);
    }
    override suspend fun doWork(): Result {
        val filePath = inputData.getString("filePath")
        if (filePath == null || filePath.isEmpty()) {
            return Result.failure()
        }

        val endpointUrl = inputData.getString("endpointUrl")
        if (endpointUrl == null || endpointUrl.isEmpty()) {
            return Result.failure()
        }

        val chunkSize = inputData.getInt("chunkSize", 1024)
        var headers = HashMap<String, String>()
        var json = JSONObject(inputData.getString("headers")?: "")

        // assume each key in the json object is a string (http headers) and copy them into a hashmap
        // in order to apply them to the TusClient connection
        json.keys().forEach {
            t ->
            headers[t] = json.getString(t)
        }
        var client = TusClient()
        client.headers = headers

        // apply preferences store to save upload fingerprint
        var prefs = applicationContext.getSharedPreferences("tus_flutter", 0)
        client.enableResuming(TusPreferencesURLStore(prefs))

        val file = File(filePath)

        // set endpoint URL on client
        try {
            client.setUploadCreationURL(URL(endpointUrl));
        } catch (e: MalformedURLException) {
            setForeground(createForegroundInfo("Uploading failed", file.name))
            return Result.failure()
        }
        
        val upload = TusUpload(file)
        setForeground(createForegroundInfo("Uploading", file.name))

        var resultUrl: String? = null

        // init tusExecutor to retry uploading until it fails
        var executor = object : TusExecutor() {
            override fun makeAttempt() {
                val uploader = client.resumeOrCreateUpload(upload)
                uploader.setChunkSize(chunkSize)
                do {
                    val bytesUploaded = uploader.getOffset()
                    val totalBytes = upload.getSize()
                    val progress: Double = (bytesUploaded.toDouble() / totalBytes.toDouble());
                    val progressPercent = progress * 100;

                    if (progress > 0) {
                        System.out.printf("Upload at %06.2f%%.\n", progressPercent)
                    }
                    if (progress > 0) {
                        setForegroundAsync(createForegroundInfo(String.format("Upload at %06.2f%%.\n", progressPercent), file.name))
                    }
                    setProgressAsync(workDataOf(
                            Pair("endpointUrl", endpointUrl),
                            Pair("bytesWritten", bytesUploaded),
                            Pair("bytesTotal", totalBytes)
                    ))
                } while (uploader.uploadChunk() > -1)

                uploader.finish()
                resultUrl = uploader.uploadURL.toString()
                setProgressAsync(workDataOf(Pair("endpointUrl", endpointUrl)))
            }
        }
        try {
            executor.makeAttempts()
        } catch (e: ProtocolException) {
            createForegroundInfo(String.format("Upload failed... retrying"), file.name)
            return Result.retry()
        } catch (e: IOException) {
            createForegroundInfo(String.format("Upload failed (${e.message?:""})"), file.name)
            return Result.retry()
        } catch (e: Exception) {
            createForegroundInfo(String.format("Upload failed"), file.name)
            return Result.failure()
        }
        return Result.success(
                workDataOf(
                        Pair("endpointUrl", endpointUrl),
                        Pair("resultUrl", resultUrl)))
    }
}
