package com.zou.zouaicodemother.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.zou.zouaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.zou.zouaicodemother.model.entity.ChatHistory;
import com.zou.zouaicodemother.model.entity.User;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author zou
 */
public interface ChatHistoryService extends IService<ChatHistory> {

	boolean addChatMessage(Long appId, String message, String messageType, Long userId);

	boolean deleteByAppId(Long appId);


	Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
	                                           LocalDateTime lastCreateTime,
	                                           User loginUser);

	QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

	int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);
}
