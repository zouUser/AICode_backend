package com.zou.zouaicodemother.utils;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.Field;

/**
 * 独立截图测试（不需要 Spring 上下文）
 * 直接运行 main 方法验证 WebDriver + 截图功能
 */
@Slf4j
public class WebScreenshotStandaloneTest {

	public static void main(String[] args) {
		System.out.println("===== 开始截图测试 =====");

		String[] testUrls = {
				"https://www.baidu.com",
				"https://www.bilibili.com",
		};

		try {
			for (String testUrl : testUrls) {
				System.out.println("\n--- 测试截图: " + testUrl + " ---");
				long start = System.currentTimeMillis();
				try {
					String result = WebScreenshotUtils.saveWebPageScreenshot(testUrl);
					long elapsed = System.currentTimeMillis() - start;
					if (result != null) {
						System.out.println("✓ 截图成功! 耗时: " + elapsed + "ms");
						System.out.println("  文件路径: " + result);
						java.io.File file = new java.io.File(result);
						System.out.println("  文件存在: " + file.exists());
						System.out.println("  文件大小: " + file.length() + " bytes");
					} else {
						System.out.println("✗ 截图失败，返回 null");
						System.out.println("  耗时: " + elapsed + "ms");
					}
				} catch (Exception e) {
					long elapsed = System.currentTimeMillis() - start;
					System.out.println("✗ 截图异常! 耗时: " + elapsed + "ms");
					System.err.println("异常类型: " + e.getClass().getName());
					System.err.println("异常信息: " + e.getMessage());
					e.printStackTrace(System.err);
				}
			}
		} finally {
			cleanupWebDriver();
		}

		System.out.println("\n===== 截图测试完成 =====");
	}

	private static void cleanupWebDriver() {
		try {
			Field field = WebScreenshotUtils.class.getDeclaredField("webDriver");
			field.setAccessible(true);
			WebDriver driver = (WebDriver) field.get(null);
			if (driver != null) {
				driver.quit();
				System.out.println("\n✓ WebDriver 已清理关闭");
			}
		} catch (Exception e) {
			System.err.println("清理 WebDriver 时出错: " + e.getMessage());
		}
	}
}
