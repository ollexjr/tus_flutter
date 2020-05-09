package com.minimal.tus_flutter

import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.work.*
import com.minimal.tus_flutter.service.Upload
import com.minimal.tus_flutter.upload.Threaded
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
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
import java.util.concurrent.TimeUnit

/** TusFlutterPlugin */
public class TusFlutterPlugin : FlutterPlugin, MethodCallHandler {
    var methodChannel: MethodChannel? = null
    var sharedPreferences: SharedPreferences? = null
    var clients: HashMap<String, TusClient> = HashMap<String, TusClient>()

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {

    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        val channel = MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "tus_flutter")
        val tusPlugin = TusFlutterPlugin();
        tusPlugin.methodChannel = channel
        tusPlugin.sharedPreferences = flutterPluginBinding.getApplicationContext().getSharedPreferences("tus", 0);
        channel.setMethodCallHandler(tusPlugin)
        TusClient()
    }

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "tus_flutter")
            channel.setMethodCallHandler(TusFlutterPlugin())
        }
    }

    // get a client for an upload path
    fun getEndpointClient(url: String): TusClient? {
        var client: TusClient? = this.clients[url];
        if (client == null) {
            client = TusClient();
            client.enableResuming(TusPreferencesURLStore(sharedPreferences))
            try {
                client.setUploadCreationURL(URL(url));
            } catch (e: MalformedURLException) {
                return null;
            }
            clients.put(url, client);
        }
        return client;
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${Build.VERSION.RELEASE}")
            }
            "getPendingAll" -> {
                //sharedPreferences.getStringSet()
                var all = sharedPreferences?.all;
                all?.forEach { i, k ->
                    System.out.print(i)
                    System.out.println(k)
                }
            }
            "getAll" -> {

            }
            "cancelUpload" -> {

            }
            "createUpload" -> {
                val endpointUrl = call.argument<String>("endpointUrl");
                if (endpointUrl == null) {
                    result.error("InvalidURLProvided", "", "");
                    return
                }
                val client = getEndpointClient(endpointUrl);
                if (client == null) {
                    result.error("InvalidURLProvided", "", "");
                    return
                }

                val filePath = call.argument<String>("filePath")
                if (filePath == null || filePath.isEmpty()) {
                    result.error("InvalidFileProvided", "file path null or empty", "")
                }

                var headers: HashMap<String, String> = call.argument<HashMap<String, String>>("headers")
                        ?: HashMap<String, String>()
                client.headers = headers;
                if (this.methodChannel == null) {
                    result.error("InternalError", "", "")
                    return
                }
                //Threaded(client, endpointUrl, filePath!!, this.methodChannel!!, 512).start()


                var data = workDataOf(
                        Pair("endpointUrl", endpointUrl),
                        Pair("filePath", filePath),
                        Pair("headers", JSONObject(headers).toString()),
                        Pair("chunkSize", 1024))
                var workRequest = OneTimeWorkRequestBuilder<Upload>()
                        .setInputData(data)
                        .addTag("upload")
                        //.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .setConstraints(
                                Constraints
                                        .Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED)
                                        .build())
                        .build()
                WorkManager.getInstance()
                        .enqueueUniqueWork(endpointUrl + filePath, ExistingWorkPolicy.APPEND, workRequest)
                var out = HashMap<String, Any>()
                out.put(endpointUrl, endpointUrl)
                result.success(out)
            }
            else -> {
                result.notImplemented()
            }
        }
    }
}