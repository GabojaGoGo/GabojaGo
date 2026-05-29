// app_theme.dart
// 앱 공통 색상 · 그라디언트 · 스타일 상수

import 'package:flutter/material.dart';

/// 로그인/온보딩/취향설정 공통 배경 그라디언트
/// (스플래시 톤 매칭, 채도 ↓)
const kAppGradient = BoxDecoration(
  gradient: LinearGradient(
    begin: Alignment(-0.9, -1.0),
    end: Alignment(0.9, 1.0),
    colors: [
      Color(0xFFFCD7E3), // 연 분홍 (top-left)
      Color(0xFFFFFFFF), // 흰색 (중간)
      Color(0xFFE0F8FB), // 연 하늘
      Color(0xFFC8E5F8), // 연 파랑 (bottom-right)
    ],
    stops: [0.0, 0.35, 0.75, 1.0],
  ),
);

/// 브랜드 메인 컬러
const kPrimaryColor = Color(0xFF2E7D6B);

/// 칩/카드 — 선택 안 됨 (그라디언트 배경 위 반투명 흰색)
const kChipUnselectedColor = Color(0xB3FFFFFF); // white 70%

/// 칩/카드 — 선택됨
const kChipSelectedColor = Color(0xF0FFFFFF); // white 94%

/// 입력 필드 배경
const kInputFillColor = Color(0xBFFFFFFF); // white 75%
