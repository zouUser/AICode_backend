package com.zou.zouaicodemother.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 应用名称工具类
 */
public final class AppNameUtil {

	private static final int MAX_APP_NAME_LENGTH = 64;

	private static final int FALLBACK_NAME_LENGTH = 12;

	private AppNameUtil() {
	}

	/**
	 * 清洗 AI 生成的应用名称
	 *
	 * @param rawName         AI 返回的原始名称
	 * @param fallbackPrompt  兜底时使用的 prompt（取前 12 字）
	 * @return 清洗后的名称，无效时返回兜底名称
	 */
	public static String sanitize(String rawName, String fallbackPrompt) {
		if (StrUtil.isNotBlank(rawName)) {
			String name = rawName.trim();
			// 去掉首尾引号
			if ((name.startsWith("\"") && name.endsWith("\""))
					|| (name.startsWith("'") && name.endsWith("'"))) {
				name = name.substring(1, name.length() - 1).trim();
			}
			// 去掉常见首尾标点
			name = name.replaceAll("^[「『【《\"'\\s]+", "")
					.replaceAll("[」』】》\"'\\s，。！？、；：]+$", "");
			if (StrUtil.isNotBlank(name)) {
				return name.substring(0, Math.min(name.length(), MAX_APP_NAME_LENGTH));
			}
		}
		return fallbackName(fallbackPrompt);
	}

	/**
	 * 从 prompt 截取兜底名称
	 */
	public static String fallbackName(String prompt) {
		if (StrUtil.isBlank(prompt)) {
			return "未命名应用";
		}
		return prompt.substring(0, Math.min(prompt.length(), FALLBACK_NAME_LENGTH));
	}
}
