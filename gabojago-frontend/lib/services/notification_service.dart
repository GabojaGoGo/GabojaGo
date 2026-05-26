// notification_service.dart
// 숙박세일페스타 오픈 알림 — 발급 10분 전·5분 전·발급 시각 3종

import 'dart:io';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/data/latest_all.dart' as tz;
import 'package:timezone/timezone.dart' as tz;

class NotificationService {
  static final NotificationService instance = NotificationService._();
  NotificationService._();

  final _plugin = FlutterLocalNotificationsPlugin();
  bool _initialized = false;

  Future<void> init() async {
    if (_initialized) return;
    tz.initializeTimeZones();
    tz.setLocalLocation(tz.getLocation('Asia/Seoul'));

    const androidInit = AndroidInitializationSettings('@mipmap/launcher_icon');
    const iosInit = DarwinInitializationSettings(
      requestAlertPermission: false,
      requestBadgePermission: false,
      requestSoundPermission: false,
    );
    await _plugin.initialize(
      const InitializationSettings(android: androidInit, iOS: iosInit),
    );
    _initialized = true;
  }

  Future<bool> requestPermission() async {
    if (Platform.isIOS) {
      final ios = _plugin.resolvePlatformSpecificImplementation<
          IOSFlutterLocalNotificationsPlugin>();
      return await ios?.requestPermissions(
              alert: true, badge: true, sound: true) ??
          false;
    }
    if (Platform.isAndroid) {
      final android = _plugin.resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin>();
      return await android?.requestNotificationsPermission() ?? false;
    }
    return false;
  }

  /// 다음 오전 10시를 기준으로 알림 3종 예약
  /// 1003: 10분 전 / 1004: 5분 전 / 1005: 발급 시각
  Future<void> scheduleSaleFestaAlarms() async {
    await init();
    await requestPermission();

    final now = tz.TZDateTime.now(tz.local);
    var base = tz.TZDateTime(tz.local, now.year, now.month, now.day, 10, 0, 0);
    if (!now.isBefore(base)) {
      base = base.add(const Duration(days: 1));
    }

    const androidDetails = AndroidNotificationDetails(
      'sale_festa_channel',
      '숙박세일페스타 알림',
      channelDescription: '봄 숙박세일페스타 쿠폰 발급 알림',
      importance: Importance.high,
      priority: Priority.high,
    );
    const details = NotificationDetails(
      android: androidDetails,
      iOS: DarwinNotificationDetails(presentAlert: true, presentSound: true),
    );

    await _plugin.zonedSchedule(
      1003,
      '🏨 쿠폰 발급 10분 전!',
      '곧 봄 숙박세일페스타 쿠폰이 발급됩니다. 앱을 준비해두세요!',
      base.subtract(const Duration(minutes: 10)),
      details,
      androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
      uiLocalNotificationDateInterpretation:
          UILocalNotificationDateInterpretation.absoluteTime,
    );

    await _plugin.zonedSchedule(
      1004,
      '🏨 쿠폰 발급 5분 전!',
      '잠시 후 선착순 쿠폰 발급 시작! 지금 앱을 열어두세요.',
      base.subtract(const Duration(minutes: 5)),
      details,
      androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
      uiLocalNotificationDateInterpretation:
          UILocalNotificationDateInterpretation.absoluteTime,
    );

    await _plugin.zonedSchedule(
      1005,
      '🎉 지금 바로 쿠폰 받으세요!',
      '봄 숙박세일페스타 선착순 쿠폰 발급이 시작됐습니다!',
      base,
      details,
      androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
      uiLocalNotificationDateInterpretation:
          UILocalNotificationDateInterpretation.absoluteTime,
    );
  }

  Future<void> cancelSaleFestaAlarms() async {
    await _plugin.cancel(1003);
    await _plugin.cancel(1004);
    await _plugin.cancel(1005);
  }
}
