package com.zou.zouaicodemother.ai;

import dev.langchain4j.service.SystemMessage;

/**
 * AI 应用名称生成服务
 * 根据用户需求描述生成简短的中文项目名称
 */
public interface AiAppNameGenerationService {

	/**
	 * 根据用户需求生成应用名称
	 *
	 * @param userPrompt 用户输入的需求描述
	 * @return 生成的应用名称
	 */
	@SystemMessage(fromResource = "prompt/app-name-generation-system-prompt.txt")
	String generateAppName(String userPrompt);
}
