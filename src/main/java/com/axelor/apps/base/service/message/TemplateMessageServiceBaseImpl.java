/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2012-2014 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.base.service.message;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.ReportSettings;
import com.axelor.apps.base.db.BirtTemplate;
import com.axelor.apps.base.db.BirtTemplateParameter;
import com.axelor.apps.base.db.Template;
import com.axelor.apps.message.service.MailAccountService;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.message.service.TemplateMessageServiceImpl;
import com.axelor.apps.tool.net.URLService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.tool.template.TemplateMaker;
import com.google.inject.Inject;

public class TemplateMessageServiceBaseImpl extends TemplateMessageServiceImpl {

	private static final Logger LOG = LoggerFactory.getLogger(TemplateMessageServiceBaseImpl.class); 

	@Inject
	private MessageService messageService;
	
	@Inject
	private MailAccountService mailAccountService;
	
	
	
	@Override
	protected String getFilePath(Template template)  throws AxelorException  {

		String filePath = null;
		BirtTemplate birtTemplate = template.getBirtTemplate();
		if(birtTemplate != null)  {
			filePath = this.generatePdfFromBirtTemplate(this.maker, birtTemplate, "filePath");
		}
		if(filePath == null)  {
			filePath = template.getFilePath();
		}
		
		return filePath;
		
	}
	
	
	public Map<String,String> generatePdfFile(TemplateMaker maker, String name, String modelPath, String generatedFilePath, String format, List<BirtTemplateParameter> birtTemplateParameterList) throws AxelorException {
		Map<String,String> result = new HashMap<String,String>();
		if(modelPath != null && !modelPath.isEmpty())  {
			
			ReportSettings reportSettings = new ReportSettings(modelPath, format);
			
			for(BirtTemplateParameter birtTemplateParameter : birtTemplateParameterList)  {
				
				maker.setTemplate(birtTemplateParameter.getValue());

				reportSettings.addParam(birtTemplateParameter.getName(), maker.make());
			}
			
			String url = reportSettings.getUrl();
			
			LOG.debug("URL : {}", url);
			String urlNotExist = URLService.notExist(url.toString());
			if (urlNotExist != null){
				throw new AxelorException(String.format("%s : Le chemin vers le template Birt est incorrect"), IException.CONFIGURATION_ERROR);
			}
			result.put("url",url);
			final int random = new Random().nextInt();
			String filePath = generatedFilePath;
			String fileName = name+"_"+random+"."+format;
			
			try {
				URLService.fileDownload(url, filePath, fileName);
			} catch (IOException e) {
				throw new AxelorException(String.format("Erreur lors de l'édition du fichier : \n %s", e), IException.CONFIGURATION_ERROR);
			}
			
			result.put("filePath",filePath+fileName);
			
		}
		return result;
	}
	
	public String generatePdfFromBirtTemplate(TemplateMaker maker, BirtTemplate birtTemplate, String value) throws AxelorException{
		Map<String,String> result =  this.generatePdfFile(
				maker, 
				birtTemplate.getName(),
				birtTemplate.getTemplateLink(), 
				birtTemplate.getGeneratedFilePath(), 
				birtTemplate.getFormat(), 
				birtTemplate.getBirtTemplateParameterList());
		if(result != null)
			return result.get(value);
		return null;
	}
	
}
