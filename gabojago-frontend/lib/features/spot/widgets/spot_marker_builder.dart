import 'dart:typed_data';
import 'dart:ui' as ui;
import 'dart:math' as math;

import 'package:flutter/foundation.dart' show compute;
import 'package:image/image.dart' as img;

class SpotMarkerBuilder {
  SpotMarkerBuilder._();

  static Future<Uint8List> buildSpotMarker() async {
    const double w = 48, h = 68;
    const double cx = w / 2;
    const double headR = 18.0;
    const double headCy = headR + 2;
    const double tipY = h - 3;

    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder);

    canvas.drawPath(
      _pinPath(cx, headCy + 1.5, headR, tipY + 1.5),
      ui.Paint()
        ..color = const ui.Color(0x40000000)
        ..maskFilter = const ui.MaskFilter.blur(ui.BlurStyle.normal, 4),
    );
    canvas.drawPath(
      _pinPath(cx, headCy, headR, tipY),
      ui.Paint()..color = const ui.Color(0xFF1B8C6E),
    );
    canvas.drawCircle(
        ui.Offset(cx, headCy), 7, ui.Paint()..color = const ui.Color(0xFFFFFFFF));

    final image = await recorder.endRecording().toImage(w.toInt(), h.toInt());
    return _toRgba8Png(image);
  }

  static Future<Uint8List> buildSpotMarkerHighlighted() async {
    const double w = 48, h = 68;
    const double cx = w / 2;
    const double headR = 18.0;
    const double headCy = headR + 2;
    const double tipY = h - 3;

    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder);

    canvas.drawPath(
      _pinPath(cx, headCy + 1.5, headR, tipY + 1.5),
      ui.Paint()
        ..color = const ui.Color(0x40000000)
        ..maskFilter = const ui.MaskFilter.blur(ui.BlurStyle.normal, 4),
    );
    canvas.drawPath(
      _pinPath(cx, headCy, headR, tipY),
      ui.Paint()..color = const ui.Color(0xFFFF6D00),
    );
    canvas.drawCircle(
        ui.Offset(cx, headCy), 7, ui.Paint()..color = const ui.Color(0xFFFFFFFF));

    final image = await recorder.endRecording().toImage(w.toInt(), h.toInt());
    return _toRgba8Png(image);
  }

  static Future<Uint8List> buildCurrentLocationMarker() async {
    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder);
    const size = 88.0;
    const center = ui.Offset(size / 2, size / 2);

    canvas.drawCircle(center, 24, ui.Paint()..color = const ui.Color(0x881565C0));
    canvas.drawCircle(center, 16, ui.Paint()..color = const ui.Color(0xFFFFFFFF));
    canvas.drawCircle(center, 10, ui.Paint()..color = const ui.Color(0xFF1565C0));

    final image = await recorder.endRecording().toImage(size.toInt(), size.toInt());
    return _toRgba8Png(image);
  }

  static ui.Path _pinPath(double cx, double cy, double r, double tipY) {
    return ui.Path()
      ..arcTo(ui.Rect.fromCircle(center: ui.Offset(cx, cy), radius: r), 0,
          -math.pi, false)
      ..quadraticBezierTo(cx - r * 0.5, cy + r * 1.6, cx, tipY)
      ..quadraticBezierTo(cx + r * 0.5, cy + r * 1.6, cx + r, cy)
      ..close();
  }

  /// KakaoMaps SDK가 지원하는 8-bit RGBA PNG로 변환.
  /// Flutter 기본 인코더가 일부 iOS에서 16-bit PNG를 생성해 KakaoMaps 크래시 발생,
  /// image 패키지로 강제 8-bit 인코딩. CPU 집약적이므로 isolate에서 실행.
  static Future<Uint8List> _toRgba8Png(ui.Image uiImage) async {
    final rawData = await uiImage.toByteData(format: ui.ImageByteFormat.rawRgba);
    if (rawData == null) throw StateError('marker rawRgba failed');
    return compute(_encodePng8bit, _PngEncodeParams(
      width: uiImage.width,
      height: uiImage.height,
      rgba: rawData.buffer.asUint8List(),
    ));
  }
}

class _PngEncodeParams {
  final int width;
  final int height;
  final Uint8List rgba;
  const _PngEncodeParams({
    required this.width,
    required this.height,
    required this.rgba,
  });
}

Uint8List _encodePng8bit(_PngEncodeParams p) {
  final image = img.Image.fromBytes(
    width: p.width,
    height: p.height,
    bytes: p.rgba.buffer,
    numChannels: 4,
  );
  return Uint8List.fromList(img.encodePng(image));
}
