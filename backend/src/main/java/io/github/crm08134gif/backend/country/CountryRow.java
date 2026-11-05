package io.github.crm08134gif.backend.country;

import com.fasterxml.jackson.annotation.JsonProperty;

// data/countries/countries.csv 한 줄 = 나라 하나. 빈칸은 null로 읽힌다
// group: combat(전투병 파견) · medical(의료지원)
// personnel: 참전 연인원, killed: 전사자, casualties: 전사·부상·실종·포로를 합친 수 (국가보훈부 표)
// unit: 의료지원국의 지원 부대, flag: frontend/public/flags/<flag>.svg (없으면 빈칸)
record CountryRow(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("group") String group,
    @JsonProperty("flag") String flag,
    @JsonProperty("personnel") Integer personnel,
    @JsonProperty("killed") Integer killed,
    @JsonProperty("casualties") Integer casualties,
    @JsonProperty("unit") String unit,
    @JsonProperty("note") String note,
    @JsonProperty("status") String status
) {
}
