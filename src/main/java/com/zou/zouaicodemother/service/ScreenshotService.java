package com.zou.zouaicodemother.service;

/**
 * *根据url进行截屏服务
 */
public interface ScreenshotService {

	/**
	 * 生成并上传截屏
	 *
	 * @param webUrl 网页URL
	 * @return 截屏URL
	 */
	String generateAndUploadScreenshot(String webUrl);
}
