package com.zou.zouaicodemother.ai;

import com.zou.zouaicodemother.ai.model.HtmlCodeResult;
import com.zou.zouaicodemother.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiCodeGeneratorServiceTest {

	@Resource
	private AiCodeGeneratorService aiCodeGeneratorService;

	@Test
	void generateHtmlCode() {
		HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode("做个程序员zou的工作记录小工具,不超过20行");
		Assertions.assertNotNull(result);
	}

	@Test
	void generateMultiFileCode() {
		MultiFileCodeResult multiFileCode = aiCodeGeneratorService.generateMultiFileCode("做个程序员zou的留言板,不超过50行");
		Assertions.assertNotNull(multiFileCode);
	}
}
