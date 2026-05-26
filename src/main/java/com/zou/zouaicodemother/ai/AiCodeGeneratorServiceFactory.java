package com.zou.zouaicodemother.ai;

import com.zou.zouaicodemother.ai.guardrail.PromptSafetyInputGuardrail;
import com.zou.zouaicodemother.ai.tools.*;
import com.zou.zouaicodemother.exception.BusinessException;
import com.zou.zouaicodemother.exception.ErrorCode;
import com.zou.zouaicodemother.model.enums.CodeGenTypeEnum;
import com.zou.zouaicodemother.service.ChatHistoryService;
import com.zou.zouaicodemother.utils.SpringContextUtil;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

	@Resource(name = "openAiChatModel")
	private ChatModel chatModel;
	@Resource
	private ChatMemoryStore redisChatMemoryStore;

	@Resource
	private ChatHistoryService chatHistoryService;

	@Resource
	private ToolManager toolManager;
	/**
	 * AI 服务实例缓存
	 * 缓存策略：
	 * - 最大缓存 1000 个实例
	 * - 写入后 30 分钟过期
	 * - 访问后 10 分钟过期
	 */
	private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
			.maximumSize(1000)
			.expireAfterWrite(Duration.ofMinutes(30))
			.expireAfterAccess(Duration.ofMinutes(10))
			.removalListener((key, value, cause) -> {
				log.debug("AI 服务实例被移除，appId: {}, 原因: {}", key, cause);
			})
			.build();

	/**
	 * 根据 appId 获取服务（带缓存）,兼容历史逻辑
	 */
	public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
		return createAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
	}

	/**
	 * 根据 appId和代码生成类型获取服务（带缓存）
	 */
	public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
		String cacheKey = buildCacheKey(appId, codeGenType);
		return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
	}

	/**
	 * 创建新的 AI 服务实例
	 */
	private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
		log.info("为 appId: {} 创建新的 AI 服务实例", appId);
		// 根据 appId 构建独立的对话记忆
		MessageWindowChatMemory chatMemory = MessageWindowChatMemory
				.builder()
				.id(appId)
				.chatMemoryStore(redisChatMemoryStore)
				.maxMessages(20)
				.build();
		// 从数据库加载历史对话到记忆中
		chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
		return switch (codeGenType) {
			case VUE_PROJECT -> {
				//使用多例模式的StreamingChatModel解决并发问题
				StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
				yield  AiServices.builder(AiCodeGeneratorService.class)
						.streamingChatModel(reasoningStreamingChatModel)
						.chatMemoryProvider(memoryId -> chatMemory)
						.maxSequentialToolsInvocations(20) //最多调用工具30次
						.tools(toolManager.getAllTools())
						.hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
								toolExecutionRequest, "Error:there is no tool called" + toolExecutionRequest.name()
						))
						.inputGuardrails(new PromptSafetyInputGuardrail()) //添加输入护轨
						.build();
			}
			case HTML, MULTI_FILE -> {
				StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
				yield AiServices.builder(AiCodeGeneratorService.class)
						.chatModel(chatModel)
						.maxSequentialToolsInvocations(20)
						.streamingChatModel(openAiStreamingChatModel)
						.chatMemory(chatMemory)
						.inputGuardrails(new PromptSafetyInputGuardrail()) //添加输入护轨
						.build();
			}
			default ->
					throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型" + codeGenType.getValue());
		};
	}


	@Bean
	public AiCodeGeneratorService aiCodeGeneratorService() {
		return getAiCodeGeneratorService(0);
	}

	/**
	 * 构建缓存键
	 */
	private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
		return appId + "_" + codeGenType.getValue();
	}

	/**
	 * 清除指定应用的AI服务实例缓存
	 * @param appId 应用ID
	 */
	public void clearCache(long appId) {
		// 清除所有代码生成类型的缓存
		for (CodeGenTypeEnum codeGenType : CodeGenTypeEnum.values()) {
			String cacheKey = buildCacheKey(appId, codeGenType);
			serviceCache.invalidate(cacheKey);
			log.info("清除应用的AI服务实例缓存,appId: {}, codeGenType: {}", appId, codeGenType.getValue());
		}
	}
}
