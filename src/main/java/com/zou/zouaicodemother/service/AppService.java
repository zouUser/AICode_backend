package com.zou.zouaicodemother.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.zou.zouaicodemother.model.dto.app.AppAddRequest;
import com.zou.zouaicodemother.model.dto.app.AppQueryRequest;
import com.zou.zouaicodemother.model.entity.App;
import com.zou.zouaicodemother.model.entity.User;
import com.zou.zouaicodemother.model.vo.AppVO;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author zou
 */
public interface AppService extends IService<App> {

	void generateAppScreenshotAsync(Long appId, String appUrl);

	/**
	 * 获取应用视图对象
	 *
	 * @param app 应用实体
	 * @return 应用视图对象
	 */
	AppVO getAppVO(App app);

	/**
	 * 获取应用视图对象列表
	 *
	 * @param appList 应用实体列表
	 * @return 应用视图对象列表
	 */
	List<AppVO> getAppVOList(List<App> appList);

	/**
	 * 获取查询条件包装器
	 *
	 * @param appQueryRequest 查询请求
	 * @return 查询条件包装器
	 */
	QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

	Long createApp(AppAddRequest appAddRequest, User loginUser);

	Flux<ServerSentEvent<String>> chatToGenCode(Long appId, String message, User loginUser);

	String deployApp(Long appId, User loginUser);

	boolean pauseCodeGeneration(Long appId, User loginUser);
}
