package com.zou.zouaicodemother.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.zou.zouaicodemother.ai.AiCodeGeneratorService;
import com.zou.zouaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.zou.zouaicodemother.ai.model.HtmlCodeResult;
import com.zou.zouaicodemother.ai.model.MultiFileCodeResult;
import com.zou.zouaicodemother.ai.model.message.AiResponseMessage;
import com.zou.zouaicodemother.ai.model.message.ToolExecutedMessage;
import com.zou.zouaicodemother.ai.model.message.ToolRequestMessage;
import com.zou.zouaicodemother.constant.AppConstant;
import com.zou.zouaicodemother.core.builder.VueProjectBuilder;
import com.zou.zouaicodemother.core.parser.CodeParserExecutor;
import com.zou.zouaicodemother.core.saver.CodeFileSaverExecutor;
import com.zou.zouaicodemother.exception.BusinessException;
import com.zou.zouaicodemother.exception.ErrorCode;
import com.zou.zouaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/*
* AI代码生成外观类，组合生成和保存功能
* */
@Slf4j
@Service
public class AiCodeGeneratorFacade {

	@Resource
	private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;
	@Resource
	private VueProjectBuilder vueProjectBuilder;

	/**
	 * 统一入口：根据类型生成并保存代码（使用 appId）
	 *
	 * @param userMessage     用户提示词
	 * @param codeGenType 生成类型
	 * @return 保存的目录
	 */
	public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenType, Long appId) {
		if (codeGenType == null) {
			throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
		}
		// 根据 appId 获取对应的 AI 服务实例
		AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,codeGenType);
		return switch (codeGenType) {
			case HTML -> {
				HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
				yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
			}
			case MULTI_FILE -> {
				MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
				yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
			}
			default -> {
				String errorMessage = "不支持的生成类型：" + codeGenType.getValue();
				throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
			}
		};
	}

	/**
	 * 统一入口：根据类型生成并保存代码（流式，使用 appId）
	 *
	 * @param userMessage     用户提示词
	 * @param codeGenType 生成类型
	 * @param appId           应用 ID
	 */
	public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenType, Long appId) {
		if (codeGenType == null) {
			throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
		}
		// 根据 appId 获取对应的 AI 服务实例
		AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,codeGenType);

		return switch (codeGenType) {
			case HTML -> {
				Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
				yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
			}
			case MULTI_FILE -> {
				Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
				yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
			}
			case VUE_PROJECT -> {
				TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
				yield processTokenStream(tokenStream,appId, codeGenType);
			}
			default -> {
				String errorMessage = "不支持的生成类型：" + codeGenType.getValue();
				throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
			}
		};
	}

	/**
	 * 通用流式代码处理方法（使用 appId）
	 *
	 * @param codeStream  代码流
	 * @param codeGenType 代码生成类型
	 * @param appId       应用 ID
	 * @return 流式响应
	 */
	private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
		StringBuilder codeBuilder = new StringBuilder();
		return codeStream.doOnNext(chunk -> {
			// 实时收集代码片段
			codeBuilder.append(chunk);
//			log.info("实时代码片段：{}", chunk);
		}).doOnComplete(() -> {
			// 流式返回完成后保存代码
			try {
				String completeCode = codeBuilder.toString();
				// 使用执行器解析代码
				Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
				// 使用执行器保存代码
				File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
				log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
			} catch (Exception e) {
				log.error("保存失败: {}", e.getMessage());
			}
			// 正常结束后删除备份
			deleteBackup(appId, codeGenType);
		});
	}

	/**
	 * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
	 *
	 * @param tokenStream TokenStream 对象
	 * @return Flux<String> 流式响应
	 */
	private Flux<String> processTokenStream(TokenStream tokenStream,Long appId, CodeGenTypeEnum codeGenType) {
		return Flux.create(sink -> {
			tokenStream.onPartialResponse((String partialResponse) -> {
						AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
						sink.next(JSONUtil.toJsonStr(aiResponseMessage));
					})
					.onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
						ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
						sink.next(JSONUtil.toJsonStr(toolRequestMessage));
					})
					.onToolExecuted((ToolExecution toolExecution) -> {
						ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
						sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
					})
					.onCompleteResponse((ChatResponse response) -> {
						//执行Vue项目构建（同步执行，确保预览时项目已就绪
						String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR +File.separator+"vue_project_"+appId;
						vueProjectBuilder.buildProject(projectPath);
						// 正常结束后删除备份
						deleteBackup(appId, codeGenType);
						sink.complete();
					})
					.onError((Throwable error) -> {
						error.printStackTrace();
						sink.error(error);
					})
					.start();
		});
	}

	/**
	 * 创建代码快照备份（在每次生成前调用）
	 *
	 * @param appId      应用 ID
	 * @param codeGenType 代码生成类型
	 */
	public void createBackup(Long appId, CodeGenTypeEnum codeGenType) {
		String sourceDir = getCodeDir(codeGenType, appId);
		String backupDir = getBackupDir(codeGenType, appId);
		File sourceFile = new File(sourceDir);
		if (sourceFile.exists()) {
			FileUtil.del(backupDir);
			FileUtil.mkdir(backupDir);
			copyDirectoryContents(sourceFile, new File(backupDir));
			log.info("代码已备份, appId={}, type={}, 备份路径={}", appId, codeGenType.getValue(), backupDir);
		} else {
			log.info("源目录不存在，跳过备份, appId={}, 路径={}", appId, sourceDir);
		}
	}

	/**
	 * 回滚代码到上一次备份状态
	 *
	 * @param appId      应用 ID
	 * @param codeGenType 代码生成类型
	 * @return 是否回滚成功
	 */
	public boolean rollbackCode(Long appId, CodeGenTypeEnum codeGenType) {
		String backupDir = getBackupDir(codeGenType, appId);
		String targetDir = getCodeDir(codeGenType, appId);
		File backupFile = new File(backupDir);
		if (backupFile.exists()) {
			FileUtil.del(targetDir);
			FileUtil.mkdir(targetDir);
			copyDirectoryContents(backupFile, new File(targetDir));
			log.info("代码已回滚, appId={}, type={}", appId, codeGenType.getValue());
			return true;
		} else {
			log.warn("无备份数据，跳过回滚, appId={}, type={}, 备份路径={}", appId, codeGenType.getValue(), backupDir);
			return false;
		}
	}

	/**
	 * 删除代码备份（AI响应正常结束后调用）
	 *
	 * @param appId      应用 ID
	 * @param codeGenType 代码生成类型
	 */
	public void deleteBackup(Long appId, CodeGenTypeEnum codeGenType) {
		String backupDir = getBackupDir(codeGenType, appId);
		File backupFile = new File(backupDir);
		if (backupFile.exists()) {
			FileUtil.del(backupDir);
			log.info("备份已删除, appId={}, type={}, 路径={}", appId, codeGenType.getValue(), backupDir);
		}
	}

	private void copyDirectoryContents(File srcDir, File destDir) {
		File[] files = srcDir.listFiles();
		if (files == null) return;
		for (File file : files) {
			FileUtil.copy(file, new File(destDir, file.getName()), true);
		}
	}

	private String getCodeDir(CodeGenTypeEnum codeGenType, Long appId) {
		return AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + codeGenType.getValue().toLowerCase() + "_" + appId;
	}

	private String getBackupDir(CodeGenTypeEnum codeGenType, Long appId) {
		return AppConstant.CODE_BACKUP_ROOT_DIR + File.separator + codeGenType.getValue().toLowerCase() + "_" + appId;
	}

}
