package com.hrplatform.common.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @Test
    void maskNameKeepsFirstCharAndMasksTheRest() {
        assertThat(PiiMasker.maskName(null)).isNull();
        assertThat(PiiMasker.maskName("")).isEmpty();
        assertThat(PiiMasker.maskName("张")).isEqualTo("*");
        assertThat(PiiMasker.maskName("张三")).isEqualTo("张*");
        assertThat(PiiMasker.maskName("李华")).isEqualTo("李*");
    }

    @Test
    void maskNameKeepsFirstAndLastCharForLongerNames() {
        assertThat(PiiMasker.maskName("王小明")).isEqualTo("王*明");
        assertThat(PiiMasker.maskName("欧阳娜娜")).isEqualTo("欧**娜");
    }

    @Test
    void maskPhoneKeepsFirstThreeAndLastFour() {
        assertThat(PiiMasker.maskPhone(null)).isNull();
        assertThat(PiiMasker.maskPhone("")).isEmpty();
        assertThat(PiiMasker.maskPhone("13812345678")).isEqualTo("138****5678");
        assertThat(PiiMasker.maskPhone("15900001111")).isEqualTo("159****1111");
    }

    @Test
    void maskIdNumberKeepsFirstFourAndLastFour() {
        assertThat(PiiMasker.maskIdNumber(null)).isNull();
        assertThat(PiiMasker.maskIdNumber("")).isEmpty();
        assertThat(PiiMasker.maskIdNumber("110101199001011234")).isEqualTo("1101**********1234");
        assertThat(PiiMasker.maskIdNumber("11010119900101123X")).isEqualTo("1101**********123X");
    }

    @Test
    void maskObjectKeyKeepsFirstTwoSegmentsOnly() {
        assertThat(PiiMasker.maskObjectKey(null)).isNull();
        assertThat(PiiMasker.maskObjectKey("")).isEmpty();
        assertThat(PiiMasker.maskObjectKey("archive/123/uuid/photo.png"))
                .isEqualTo("archive/123/***");
        assertThat(PiiMasker.maskObjectKey("archive/123")).isEqualTo("archive/123");
    }

    @Test
    void maskTextScrubsPhoneAndIdNumbersInsideFreeText() {
        String raw = "员工张伟的手机号13812345678，身份证110101199001011234档案读取失败";
        String masked = PiiMasker.maskText(raw);

        assertThat(masked).doesNotContain("13812345678");
        assertThat(masked).contains("138****5678");
        assertThat(masked).doesNotContain("110101199001011234");
        assertThat(masked).contains("1101**********1234");
    }

    @Test
    void maskTextLeavesNullAndPlainTextAlone() {
        assertThat(PiiMasker.maskText(null)).isNull();
        assertThat(PiiMasker.maskText("service unavailable")).isEqualTo("service unavailable");
    }
}
