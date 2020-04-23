import 'dart:async';

import 'package:flutter/services.dart';

class TusFlutter {
  static const MethodChannel _channel =
      const MethodChannel('tus_flutter');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }
}
