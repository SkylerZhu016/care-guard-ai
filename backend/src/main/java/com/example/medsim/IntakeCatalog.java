package com.example.medsim;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class IntakeCatalog {
    static final String VERSION = "intake-catalog-2026.07";
    private final Map<String, CatalogSymptomView> byCode;

    IntakeCatalog() {
        var values = new LinkedHashMap<String, CatalogSymptomView>();
        add(values, "CHEST_PAIN", "胸痛", "胸部与呼吸", SupportLevel.RULE_SUPPORTED, true, List.of(
            yesNo("chest.current", "现在有没有胸口疼、发紧或不舒服？"),
            yesNo("chest.dyspnea", "同时有没有喘不上气？"),
            yesNo("chest.syncope", "有没有真的晕倒过？"),
            choice("chest.feeling", "更接近哪种感觉？", "PAIN", "疼痛", "TIGHTNESS", "发紧", "PRESSURE", "压迫感", "OTHER", "其他", "UNKNOWN", "说不清")
        ));
        add(values, "DYSPNEA", "呼吸困难", "胸部与呼吸", SupportLevel.RULE_SUPPORTED, true, List.of(
            yesNo("dyspnea.current", "现在有没有喘不上气或觉得气不够用？"),
            choice("dyspnea.context", "通常在什么情况下出现？", "REST", "休息时", "ACTIVITY", "活动后", "BOTH", "都有", "UNKNOWN", "说不清"),
            yesNo("dyspnea.full_sentence", "现在能否不间断地完整说一句话？"),
            yesNo("dyspnea.sudden", "是否突然发生？")
        ));
        add(values, "SYNCOPE", "晕厥", "神经与意识", SupportLevel.RULE_SUPPORTED, true, List.of(
            yesNo("syncope.loss_of_consciousness", "是否真的失去过意识，而不只是头晕？"),
            yesNo("syncope.recovered", "现在是否已经恢复？"),
            choice("syncope.duration", "失去意识大约持续多久？", "SECONDS", "几秒", "MINUTES", "几分钟", "LONGER", "更久", "UNKNOWN", "说不清"),
            yesNo("syncope.chest_pain", "当时是否同时胸痛？"),
            yesNo("syncope.dyspnea", "当时是否同时呼吸困难？")
        ));
        add(values, "ALTERED_CONSCIOUSNESS", "意识异常", "神经与意识", SupportLevel.RULE_SUPPORTED, true, List.of(
            yesNo("consciousness.present", "你或身边的人是否发现反应明显变慢、答非所问、认不清人或地点？"),
            yesNo("consciousness.current", "这种情况现在是否仍存在？"),
            choice("consciousness.onset", "大约何时发现？", "JUST_NOW", "刚刚", "TODAY", "今天", "EARLIER", "更早", "UNKNOWN", "说不清")
        ));
        add(values, "HEADACHE", "头痛", "头部与神经", SupportLevel.RECORD_ONLY, true, List.of());
        add(values, "DIZZINESS", "头晕", "头部与神经", SupportLevel.RECORD_ONLY, true, List.of());
        add(values, "FEVER", "发热", "全身不适", SupportLevel.RECORD_ONLY, true, List.of());
        add(values, "COUGH", "咳嗽", "胸部与呼吸", SupportLevel.RECORD_ONLY, true, List.of());
        add(values, "ABDOMINAL_PAIN", "腹痛", "消化系统", SupportLevel.RECORD_ONLY, true, List.of());
        add(values, "NAUSEA_VOMITING", "恶心或呕吐", "消化系统", SupportLevel.RECORD_ONLY, false, List.of());
        add(values, "DIARRHEA", "腹泻", "消化系统", SupportLevel.RECORD_ONLY, false, List.of());
        add(values, "FATIGUE", "乏力", "全身不适", SupportLevel.RECORD_ONLY, false, List.of());
        add(values, "PALPITATIONS", "心悸", "胸部与呼吸", SupportLevel.RECORD_ONLY, false, List.of());
        add(values, "RASH_ALLERGY", "皮疹或过敏不适", "皮肤与过敏", SupportLevel.RECORD_ONLY, false, List.of());
        add(values, "LIMB_WEAKNESS_NUMBNESS", "肢体无力或麻木", "头部与神经", SupportLevel.RECORD_ONLY, false, List.of());
        add(values, "OTHER", "其他不适", "其他", SupportLevel.CUSTOM, false, List.of());
        byCode = Map.copyOf(values);
    }

    IntakeCatalogView view() { return new IntakeCatalogView(VERSION, List.copyOf(byCode.values())); }

    CatalogSymptomView require(String code) {
        var value = byCode.get(code);
        if (value == null) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
            "INTAKE_SYMPTOM_UNKNOWN", "症状目录中不存在该项目，请刷新后重试");
        return value;
    }

    private static void add(Map<String, CatalogSymptomView> values, String code, String name, String category,
                            SupportLevel supportLevel, boolean common, List<CatalogQuestionView> questions) {
        values.put(code, new CatalogSymptomView(code, name, category, supportLevel, common, questions));
    }

    private static CatalogQuestionView yesNo(String id, String prompt) {
        return choice(id, prompt, "YES", "是", "NO", "否", "UNKNOWN", "不知道/说不清");
    }

    private static CatalogQuestionView choice(String id, String prompt, String... options) {
        var values = new java.util.ArrayList<QuestionOptionView>();
        for (int index = 0; index < options.length; index += 2) values.add(new QuestionOptionView(options[index], options[index + 1]));
        return new CatalogQuestionView(id, prompt, "SINGLE_CHOICE", false, List.copyOf(values));
    }
}
