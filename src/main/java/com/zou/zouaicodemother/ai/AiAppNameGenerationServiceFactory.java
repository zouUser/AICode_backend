package com.zou.zouaicodemother.ai;

import com.zou.zouaicodemother.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 应用名称生成服务工厂
 */
@Slf4j
@Configuration
public class AiAppNameGenerationServiceFactory {

	/**
	 * 创建 AI 应用名称生成服务实例
	 */
	public AiAppNameGenerationService createAiAppNameGenerationService() {
		ChatModel chatModel = SpringContextUtil.getBean("routingChatModelPrototype", ChatModel.class);
		return AiServices.builder(AiAppNameGenerationService.class)
				.chatModel(chatModel)
				.build();
	}

	/**
	 * 默认提供一个 Bean
	 */
	@Bean
	public AiAppNameGenerationService aiAppNameGenerationService() {
		return createAiAppNameGenerationService();
	}
}
