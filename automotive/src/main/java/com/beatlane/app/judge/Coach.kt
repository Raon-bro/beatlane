package com.beatlane.app.judge

/** 주행 후 리포트의 코칭 한마디. 가장 자주 점수를 깎은 구간을 기준으로 고른다. */
object Coach {
    fun message(powertrain: Powertrain, notes: List<NoteResult>, hardDecelCount: Int): String {
        if (notes.isEmpty()) {
            return "아직 판정된 감속이 없어요. 다음 주행에서 만나요!"
        }
        if (hardDecelCount > 0) {
            return "급감속이 ${hardDecelCount}번 있었어요. 앞차와 거리를 넉넉히 두고, 조금 일찍 천천히 밟아 보세요."
        }
        val weakest = notes
            .filter { it.grade == Grade.GOOD || it.grade == Grade.MISS }
            .mapNotNull { it.weakestPhase }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: return "동승자가 편안했을 주행이에요. 지금 감각을 그대로 유지하세요!"

        return when (weakest) {
            NotePhase.START -> "브레이크를 처음 밟을 때 더 천천히, 지그시 눌러 주세요."
            NotePhase.HOLD -> "밟은 뒤에는 힘을 일정하게 유지해 보세요. 밟았다 뗐다 하면 몸이 흔들려요."
            NotePhase.END -> if (powertrain == Powertrain.GASOLINE) {
                "멈추기 직전에 브레이크를 살짝 풀어 주면 울컥임이 사라져요."
            } else {
                "정지 직전에는 브레이크 힘을 조금 빼 주세요. 마지막 울컥임이 줄어요."
            }
            NotePhase.LIFT, NotePhase.REGEN -> "가속 페달에서 발을 천천히 떼면 회생 제동이 부드러워져요."
        }
    }
}
