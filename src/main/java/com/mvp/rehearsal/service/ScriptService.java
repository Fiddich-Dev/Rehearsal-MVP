package com.mvp.rehearsal.service;

import com.mvp.rehearsal.dto.Line;
import com.mvp.rehearsal.dto.Script;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScriptService {

    private static final Script FIXED = new Script(
            "첫 만남",
            List.of("지수", "민준"),
            List.of(
                    new Line(0, "민준", "오래 기다렸어?"),
                    new Line(1, "지수", "아니, 나도 방금 왔어."),
                    new Line(2, "민준", "다행이다. 오늘 많이 떨려?"),
                    new Line(3, "지수", "조금. 근데 괜찮아, 같이 하면 되지."),
                    new Line(4, "민준", "맞아. 우리 잘 할 수 있어."),
                    new Line(5, "지수", "고마워. 시작할까?"),
                    new Line(6, "민준", "응, 가자."),
                    new Line(7, "지수", "좋아. 호흡 한 번만."),
                    new Line(8, "민준", "셋, 둘, 하나."),
                    new Line(9, "지수", "가자.")
            )
    );

    public Script getScript() {
        return FIXED;
    }
}
