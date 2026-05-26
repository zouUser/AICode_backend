package com.zou.zouaicodemother.core.saver;

import com.zou.zouaicodemother.ai.model.HtmlCodeResult;
import com.zou.zouaicodemother.exception.BusinessException;
import com.zou.zouaicodemother.exception.ErrorCode;
import com.zou.zouaicodemother.model.enums.CodeGenTypeEnum;
import cn.hutool.core.util.StrUtil;


public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {
	@Override
	protected CodeGenTypeEnum getCodeType() {
		return CodeGenTypeEnum.HTML;
	}

	@Override
	protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
		writeToFile(baseDirPath,"index.html", result.getHtmlCode());
	}

	@Override
	protected void validateInput(HtmlCodeResult result) {
		if (StrUtil.isBlank(result.getHtmlCode()))
			throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码不能为空");
	}
}
