package com.zou.zouaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.zou.zouaicodemother.ai.AiAppNameGenerationService;
import com.zou.zouaicodemother.ai.AiAppNameGenerationServiceFactory;
import com.zou.zouaicodemother.ai.AiCodeGenTypeRoutingService;
import com.zou.zouaicodemother.ai.AiCodeGenTypeRoutingServiceFactory;
import com.zou.zouaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.zou.zouaicodemother.utils.AppNameUtil;
import com.zou.zouaicodemother.constant.AppConstant;
import com.zou.zouaicodemother.core.AiCodeGeneratorFacade;
import com.zou.zouaicodemother.core.builder.VueProjectBuilder;
import com.zou.zouaicodemother.core.handler.StreamHandlerExecutor;
import com.zou.zouaicodemother.exception.BusinessException;
import com.zou.zouaicodemother.exception.ErrorCode;
import com.zou.zouaicodemother.exception.ThrowUtils;
import com.zou.zouaicodemother.model.dto.app.AppAddRequest;
import com.zou.zouaicodemother.model.dto.app.AppQueryRequest;
import com.zou.zouaicodemother.model.entity.App;
import com.zou.zouaicodemother.mapper.AppMapper;
import com.zou.zouaicodemother.model.entity.User;
import com.zou.zouaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.zou.zouaicodemother.model.enums.CodeGenTypeEnum;
import com.zou.zouaicodemother.model.vo.AppVO;
import com.zou.zouaicodemother.model.vo.UserVO;
import com.zou.zouaicodemother.service.AppService;
import com.zou.zouaicodemother.service.ChatHistoryService;
import com.zou.zouaicodemother.service.ScreenshotService;
import com.zou.zouaicodemother.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author zou
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {


	@Value("${code.deploy-host:http://localhost}")
	private String deployHost;

	@Resource
	private UserService userService;
	@Resource
	private AiCodeGeneratorFacade aiCodeGeneratorFacade;
	@Resource
	private ChatHistoryService chatHistoryService;
	@Resource
	private StreamHandlerExecutor streamHandlerExecutor;
	@Resource
	private VueProjectBuilder vueProjectBuilder;
	@Resource
	private ScreenshotService screenshotService;
	@Autowired
	private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;
	@Autowired
	private AiAppNameGenerationServiceFactory aiAppNameGenerationServiceFactory;
	@Resource
	private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;
    // 存储每个应用的当前流订阅句柄
    private final Map<Long, Disposable> appStreamDisposables = new ConcurrentHashMap<>();


	@Override
	public AppVO getAppVO(App app) {
		if (app == null) {
			return null;
		}
		AppVO appVO = new AppVO();
		BeanUtil.copyProperties(app, appVO);
		// 关联查询用户信息
		Long userId = app.getUserId();
		if (userId != null) {
			User user = userService.getById(userId);
			UserVO userVO = userService.getUserVO(user);
			appVO.setUser(userVO);
		}
		return appVO;
	}

	@Override
	public List<AppVO> getAppVOList(List<App> appList) {
		if (CollUtil.isEmpty(appList)) {
			return new ArrayList<>();
		}
		// 批量获取用户信息，避免 N+1 查询问题
		Set<Long> userIds = appList.stream()
				.map(App::getUserId)
				.collect(Collectors.toSet());
		Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
				.collect(Collectors.toMap(User::getId, userService::getUserVO));
		return appList.stream().map(app -> {
			AppVO appVO = getAppVO(app);
			UserVO userVO = userVOMap.get(app.getUserId());
			appVO.setUser(userVO);
			return appVO;
		}).collect(Collectors.toList());
	}

	@Override
	public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
		if (appQueryRequest == null) {
			throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
		}
		Long id = appQueryRequest.getId();
		String appName = appQueryRequest.getAppName();
		String cover = appQueryRequest.getCover();
		String initPrompt = appQueryRequest.getInitPrompt();
		String codeGenType = appQueryRequest.getCodeGenType();
		String deployKey = appQueryRequest.getDeployKey();
		Integer priority = appQueryRequest.getPriority();
		Long userId = appQueryRequest.getUserId();
		String sortField = appQueryRequest.getSortField();
		String sortOrder = appQueryRequest.getSortOrder();
		return QueryWrapper.create()
				.eq("id", id)
				.like("appName", appName)
				.like("cover", cover)
				.like("initPrompt", initPrompt)
				.eq("codeGenType", codeGenType)
				.eq("deployKey", deployKey)
				.eq("priority", priority)
				.eq("userId", userId)
				.orderBy(sortField, "ascend".equals(sortOrder));
	}


	/**
	 * 创建应用
	 *
	 * @param appAddRequest 应用创建请求
	 * @param loginUser     登录用户
	 * @return 应用ID
	 */
	@Override
	public Long createApp(AppAddRequest appAddRequest, User loginUser) {
		// 参数校验
		String initPrompt = appAddRequest.getInitPrompt();
		ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
		// 构造入库对象
		App app = new App();
		BeanUtil.copyProperties(appAddRequest, app);
		app.setUserId(loginUser.getId());
		// 并行调用 AI 生成应用名称与代码生成类型
		CompletableFuture<String> appNameFuture = CompletableFuture.supplyAsync(() -> {
			try {
				AiAppNameGenerationService nameService = aiAppNameGenerationServiceFactory.createAiAppNameGenerationService();
				String rawName = nameService.generateAppName(initPrompt);
				return AppNameUtil.sanitize(rawName, initPrompt);
			} catch (Exception e) {
				log.warn("AI 生成应用名称失败，使用兜底名称, prompt: {}", initPrompt, e);
				return AppNameUtil.fallbackName(initPrompt);
			}
		});
		CompletableFuture<CodeGenTypeEnum> codeGenTypeFuture = CompletableFuture.supplyAsync(() -> {
			AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
			return routingService.routeCodeGenType(initPrompt);
		});
		CompletableFuture.allOf(appNameFuture, codeGenTypeFuture).join();
		String appName = appNameFuture.join();
		CodeGenTypeEnum selectedCodeGenType = codeGenTypeFuture.join();
		app.setAppName(appName);
		app.setCodeGenType(selectedCodeGenType.getValue());
		// 插入数据库
		boolean result = this.save(app);
		ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
		log.info("应用创建成功，ID: {}, 名称: {}, 类型: {}", app.getId(), appName, selectedCodeGenType.getValue());
		return app.getId();
	}

	/**
	 * 用户向应用发送消息，AI 生成代码（流式）
	 *
	 * @param appId      应用ID
	 * @param message    用户消息
	 * @param loginUser  登录用户
	 * @return 生成的代码流
	 */
	@Override
	public Flux<ServerSentEvent<String>> chatToGenCode(Long appId, String message, User loginUser) {
		// 1. 参数校验
		ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
		ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
		// 2. 查询应用信息
		App app = this.getById(appId);
		ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
		// 3. 验证用户是否有权限访问该应用，仅本人可以生成代码
		if (!app.getUserId().equals(loginUser.getId())) {
			throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
		}
		// 4. 获取应用的代码生成类型
		String codeGenTypeStr = app.getCodeGenType();
		CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
		if (codeGenTypeEnum == null) {
			throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
		}
		// 5. 通过校验后，添加用户消息到对话历史
		chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
		// 5.5 创建代码快照备份（用于暂停回滚）
		try {
			aiCodeGeneratorFacade.createBackup(appId, codeGenTypeEnum);
		} catch (Exception e) {
			log.warn("创建代码备份失败，不影响继续生成, appId={}, error={}", appId, e.getMessage());
		}
		// 6. 调用 AI 生成代码（流式）
		Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
		// 7. 收集AI响应内容,并在完成后记录到对话历史
		Flux<String> stringFlux = streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum);
		//8.返回一个新的Flux，在订阅时个闹钟Disposable
		return Flux.create(sink -> {
			//订阅原始流
			Disposable disposable = stringFlux.map(chunk -> {
						//将内容包装为JSON对象
						Map<String, String> wrapper = Map.of("d", chunk);
						String jsonData = JSONUtil.toJsonStr(wrapper);
						return ServerSentEvent.<String>builder()
								.data(jsonData)
								.build();
					})
					.subscribe(sink::next, sink::error, () -> {
						sink.next(ServerSentEvent.<String>builder()
								.event("done")
								.data("")
								.build());
						sink.complete();
					});
			//存储Disposable到map中
			appStreamDisposables.put(appId, disposable);
			//处理取消订阅（中断）情况
			sink.onDispose(() -> {
						try {
						} catch (Exception e) {
						} finally {
							log.info("取消订阅，appId: {}", appId);
							//中断原始流
							if (disposable != null && !disposable.isDisposed()) {
								//取消订阅时，从map中移除Disposable
								appStreamDisposables.remove(appId);
								disposable.dispose();
								sink.complete();
							}
						}
					}
			);
		});
	}

	/**
	 * 暂停代码生成
	 *
	 * @param appId      应用ID
	 * @param loginUser  登录用户
	 * @return 是否暂停成功
	 */
	@Override
	public boolean pauseCodeGeneration(Long appId, User loginUser) {
		// 1. 参数校验
		ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
		// 2. 查询应用信息
		App app = this.getById(appId);
		ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
		// 3. 权限校验：只有应用创建者可以暂停
		if (!app.getUserId().equals(loginUser.getId())) {
			throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限暂停该应用的生成");
		}
		// 4. 中断订阅的流
		Disposable disposable = appStreamDisposables.remove(appId);
		if (disposable != null && !disposable.isDisposed()) {
			disposable.dispose();
			log.info("已中断应用流, appId={}", appId);
		}
		// 5. 回滚代码文件到上一次对话结束时的状态
		String codeGenTypeStr = app.getCodeGenType();
		CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
		if (codeGenTypeEnum != null) {
			boolean rolledBack = aiCodeGeneratorFacade.rollbackCode(appId, codeGenTypeEnum);
			if (rolledBack) {
				log.info("代码回滚成功, appId={}, type={}", appId, codeGenTypeEnum.getValue());
			} else {
				log.warn("代码回滚跳过（无备份数据）, appId={}, type={}", appId, codeGenTypeEnum.getValue());
			}
		}
		// 6. 清除AI实例缓存
		aiCodeGeneratorServiceFactory.clearCache(appId);
		return true;
	}

	/**
	 * 删除应用时关联删除对话历史
	 *
	 * @param id 应用ID
	 * @return 是否成功
	 */
	@Override
	public boolean removeById(Serializable id) {
		if (id == null) {
			return false;
		}
		// 转换为 Long 类型
		Long appId = Long.valueOf(id.toString());
		if (appId <= 0) {
			return false;
		}
		// 先删除关联的对话历史
		try {
			chatHistoryService.deleteByAppId(appId);
		} catch (Exception e) {
			// 记录日志但不阻止应用删除
			log.error("删除应用关联对话历史失败: {}", e.getMessage());
		}
		// 删除应用
		return super.removeById(id);
	}

	/**
	 * 应用部署
	 *
	 * @param appId   应用ID
	 * @param loginUser 登录用户
	 * @return 部署结果
	 */
	@Override
	public String deployApp(Long appId, User loginUser) {
		// 1. 参数校验
		ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
		ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
		// 2. 查询应用信息
		App app = this.getById(appId);
		ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
		// 3. 验证用户是否有权限部署该应用，仅本人可以部署
		if (!app.getUserId().equals(loginUser.getId())) {
			throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
		}
		// 4. 检查是否已有 deployKey
		String deployKey = app.getDeployKey();
		// 没有则生成 6 位 deployKey（大小写字母 + 数字）
		if (StrUtil.isBlank(deployKey)) {
			deployKey = RandomUtil.randomString(6);
		}
		// 5. 获取代码生成类型，构建源目录路径
		String codeGenType = app.getCodeGenType();
		String sourceDirName = codeGenType + "_" + appId;
		String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
		// 6. 检查源目录是否存在
		File sourceDir = new File(sourceDirPath);
		if (!sourceDir.exists() || !sourceDir.isDirectory()) {
			throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
		}
		//7.Vue项目特殊处理，执行构建
		CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
		if(codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT){
			//执行构建
			boolean builderSuccess = vueProjectBuilder.buildProject(sourceDirPath);
			ThrowUtils.throwIf(!builderSuccess,ErrorCode.SYSTEM_ERROR,"Vue项目构建失败，请检查代码和依赖");
			//检查dist目录是否存在
			File distDir = new File(sourceDirPath, "dist");
			ThrowUtils.throwIf(!distDir.exists(),ErrorCode.SYSTEM_ERROR,"Vue项目构建完成，但未生成dist目录");
			//将dist目录作为部署源
			sourceDir = distDir;
			log.info("Vue项目构建完成，dist目录: {}", distDir.getAbsolutePath());
		}
		// 8. 复制文件到部署目录
		String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
		try {
			FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
		}
		// 9. 更新应用的 deployKey 和部署时间
		App updateApp = new App();
		updateApp.setId(appId);
		updateApp.setDeployKey(deployKey);
		updateApp.setDeployedTime(LocalDateTime.now());
		boolean updateResult = this.updateById(updateApp);
		ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
		// 10. 构建应用访问 URL
		//String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
		// 10. 构建应用访问 URL
		String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);
		// 11. 异步生成截图并更新应用封面
		generateAppScreenshotAsync(appId, appDeployUrl);
		return appDeployUrl;

	}

	/**
	 * 异步生成应用截图并更新封面
	 *
	 * @param appId  应用ID
	 * @param appUrl 应用访问URL
	 */
	@Override
	public void generateAppScreenshotAsync(Long appId, String appUrl) {
		Thread.startVirtualThread(() -> {
			try {
				log.info("开始异步生成应用截图, appId={}, appUrl={}", appId, appUrl);
				String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
				if (StrUtil.isBlank(screenshotUrl)) {
					log.error("截图生成失败，screenshotUrl 为空, appId={}", appId);
					return;
				}
				App updateApp = new App();
				updateApp.setId(appId);
				updateApp.setCover(screenshotUrl);
				boolean updated = this.updateById(updateApp);
				if (!updated) {
					log.error("更新应用封面字段失败, appId={}", appId);
				} else {
					log.info("应用封面更新成功, appId={}, coverUrl={}", appId, screenshotUrl);
				}
			} catch (Exception e) {
				log.error("异步生成应用截图异常, appId={}", appId, e);
			}
		});
	}


}
