import 'package:flutter/material.dart';

const _kPrimary = Color(0xFF2E7D6B);
const _kText1   = Color(0xFF1A1A1A);
const _kText2   = Color(0xFF707070);
const _kText3   = Color(0xFF9E9E9E);
const _kBorder  = Color(0xFFE8EAED);
const _kSurface = Color(0xFFF5F7F7);

class BucketListItem extends StatelessWidget {
  final Map<String, dynamic> data;
  final VoidCallback onDelete;
  final VoidCallback onToggleComplete;

  const BucketListItem({
    super.key,
    required this.data,
    required this.onDelete,
    required this.onToggleComplete,
  });

  @override
  Widget build(BuildContext context) {
    final completed = data['completed'] as bool? ?? false;
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: completed ? _kSurface : Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
            color: completed ? _kBorder.withValues(alpha: 0.5) : _kBorder),
      ),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        GestureDetector(
          onTap: onToggleComplete,
          child: Padding(
            padding: const EdgeInsets.only(top: 1),
            child: Icon(
              completed ? Icons.check_circle : Icons.radio_button_unchecked,
              size: 22,
              color: completed ? _kPrimary : _kText3,
            ),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(
              data['title'] as String? ?? '',
              style: TextStyle(
                fontWeight: FontWeight.w700,
                fontSize: 14,
                color: completed ? _kText3 : _kText1,
                decoration: completed ? TextDecoration.lineThrough : null,
              ),
            ),
            const SizedBox(height: 3),
            if ((data['area'] as String? ?? '').isNotEmpty)
              Row(children: [
                const Icon(Icons.place_outlined, size: 12, color: _kText3),
                const SizedBox(width: 3),
                Text(data['area'] as String? ?? '',
                    style: const TextStyle(fontSize: 11, color: _kText2)),
              ]),
            if ((data['note'] as String? ?? '').isNotEmpty) ...[
              const SizedBox(height: 3),
              Text(
                data['note'] as String? ?? '',
                style: TextStyle(
                    fontSize: 11,
                    color: Colors.grey.shade500,
                    fontStyle: FontStyle.italic),
              ),
            ],
          ]),
        ),
        GestureDetector(
          onTap: () => showModalBottomSheet(
            context: context,
            shape: const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
            ),
            builder: (_) => SafeArea(
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                const SizedBox(height: 8),
                Container(
                  width: 36, height: 4,
                  decoration: BoxDecoration(
                      color: _kBorder, borderRadius: BorderRadius.circular(2)),
                ),
                const SizedBox(height: 16),
                ListTile(
                  leading: Icon(Icons.delete_outline, color: Colors.red.shade400),
                  title: Text('삭제',
                      style: TextStyle(color: Colors.red.shade400)),
                  onTap: () {
                    Navigator.pop(context);
                    onDelete();
                  },
                ),
                const SizedBox(height: 8),
              ]),
            ),
          ),
          child: const Icon(Icons.more_horiz, color: _kText3, size: 20),
        ),
      ]),
    );
  }
}

class EmptyBucket extends StatelessWidget {
  const EmptyBucket({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 24),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: _kBorder),
        ),
        child: const Column(children: [
          Text('📋', style: TextStyle(fontSize: 30)),
          SizedBox(height: 8),
          Text('버킷리스트가 비어있어요',
              style: TextStyle(fontSize: 13, color: _kText2)),
          SizedBox(height: 4),
          Text('위의 + 추가 버튼을 눌러 추가해 보세요',
              style: TextStyle(fontSize: 11, color: _kText3)),
        ]),
      ),
    );
  }
}
