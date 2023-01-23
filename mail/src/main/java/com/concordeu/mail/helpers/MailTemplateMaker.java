package com.concordeu.mail.helpers;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Map;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

@Service
public class MailTemplateMaker {

	public String getFinalEmailTemplate(String emailFileName, Map<String, String> fieldMap) throws IOException {

		String fileToString = loadFile(emailFileName);

		return populateFields(fileToString, fieldMap);
	}

	private String loadFile(String emailFileName) throws IOException {

		ResourceLoader resourceLoader = new DefaultResourceLoader();


		String resourcePath = "classpath:" + "templates/" + emailFileName.toLowerCase();


		Resource resource = resourceLoader.getResource(resourcePath);

		String fileFileContent;

		if(!resource.exists()){
			throw new FileNotFoundException("File not found! check your resource files!");
		}
		try {

			fileFileContent = resourceAsString(resource);

		}
		catch (Exception e){
			throw new IOException("Can not read the file");

		}
		return fileFileContent;

	}


	private String resourceAsString(Resource resource) {
		try (Reader reader = new InputStreamReader(resource.getInputStream(), UTF_8)) {
			return FileCopyUtils.copyToString(reader);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}


	/// {{user_firs_name} : John
	private String populateFields(String template, Map<String, String> fieldMap) throws IOException {

		if (template == null || fieldMap.isEmpty()){
			return "";
		}

		for (var entry : fieldMap.entrySet()) {
			template  = template.replace(makeFieldNameWithProperFormatting(entry.getKey()), entry.getValue());
		}
		return template;
	}


	private String makeFieldNameWithProperFormatting(String s){
		return  "{{" + s.toLowerCase() + "}}" ;
	}





}
