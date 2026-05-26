package com.zou.zouaicodemother.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Description("生成多个文件代码文件的结果")
@Data
public class MultiFileCodeResult {

	@Description("生成的HTML代码")
	private String htmlCode;
	@Description("生成的CSS代码")
	private String cssCode;
	@Description("生成的JS代码")
	private String jsCode;
	@Description("生成的HTML代码的描述")
	private String description;
}
