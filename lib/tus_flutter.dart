import 'dart:async';
import 'dart:collection';

import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

typedef void OnCompleteCallback(TusUpload);
typedef void OnProgressCallback(TusUpload);

class TusFlutter {
  static const MethodChannel _channel = const MethodChannel('tus_flutter');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  // upload table
  List<TusUpload> uploads;

  // uploads this plugin has called
  SharedPreferences _store;

  TusFlutter(SharedPreferences prefs) {
    this._store = prefs;
    _channel.setMethodCallHandler(this._handler);
  }

  Future<void> _handler(MethodCall call) {
    switch (call.method) {
    }
  }

  Function(TusUpload) onUploadProgress;

  Future<TusUpload> createUpload(
      String endpoint, String filePath, Map<String, String> headers) async {
    assert(endpoint != null)
    assert(filePath != null)
    TusUpload upload =
        TusUpload(_channel, UploadState.STARTING, endpoint, filePath);
    Map<String, dynamic> ret = await _channel.invokeMethod(
        "createUpload", <String, dynamic>{
      "endpointUrl": endpoint,
      "filePath": filePath,
      "headers": headers
    });

    this.uploads.add(upload);
    if (_store != null)
      this
          ._store
          .setStringList("uploads", this.uploads.map((t) => t.toString()));
  }

  static Future<List<TusUpload>> getPendingUploads(String endpoint) async {
    List<Map<String, dynamic>> pending =
        await _channel.invokeMethod("getPendingAll");
  }

  static Future<List<TusUpload>> getPendingUploadsEndpoint(
      String endpoint) async {
    List<Map<String, dynamic>> pending = await _channel
        .invokeMethod("getPending", <String, dynamic>{"endpoint": endpoint});
  }
}

enum UploadState {
  STARTING,
  UPLOADING,
  PAUSED,
  FINISHED,
  CANCELED,
}

class TusUpload {
  MethodChannel _channel;

  UploadState state;
  String endpoint;
  String path;

  resume(Map<String, String> headers) {
    _channel.invokeMethod("createThread", <String, dynamic>{
      "filePath": path,
      "endpointUrl": endpoint,
      "headers": headers,
    });
  }

  cancel() {
    _channel.invokeMethod("uploadCancel", null);
  }

  TusUpload(this._channel, this.state, this.endpoint, this.path);
}
