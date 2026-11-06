package io.github.crm08134gif.backend.country;

// 화면에 내보내는 나라 하나. 검수 상태(status)는 필터에만 쓰고 내보내지 않는다
record CountryResponse(String id, String name, String group, String flag,
                       Integer personnel, Integer killed, Integer casualties, String unit, String note) {
}
