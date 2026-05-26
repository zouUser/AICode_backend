package com.zou.zouaicodemother.controller;

import com.mybatisflex.core.paginate.Page;
import com.zou.zouaicodemother.common.BaseResponse;
import com.zou.zouaicodemother.common.ResultUtils;
import com.zou.zouaicodemother.model.entity.ChatHistory;
import com.zou.zouaicodemother.model.entity.User;
import com.zou.zouaicodemother.service.ChatHistoryService;
import com.zou.zouaicodemother.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 控制层。
 *
 * @author zou
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Autowired
    private ChatHistoryService chatHistoryService;
    @Autowired
    private UserService userService;
    /**
     * 分页查询某个应用的对话历史（游标查询）
     *
     * @param appId          应用ID
     * @param pageSize       页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @param request        请求
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<ChatHistory>> listAppChatHistory(@PathVariable Long appId,
                                                              @RequestParam(defaultValue = "10") int pageSize,
                                                              @RequestParam(required = false) LocalDateTime lastCreateTime,
                                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Page<ChatHistory> result = chatHistoryService.listAppChatHistoryByPage(appId, pageSize, lastCreateTime, loginUser);
        return ResultUtils.success(result);
    }

}
