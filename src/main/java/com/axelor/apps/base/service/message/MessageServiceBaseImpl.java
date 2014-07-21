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

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Locale;

import javax.mail.MessagingException;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.BirtTemplate;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.PrintingSettings;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.apps.base.service.user.UserInfoService;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.IMessage;
import com.axelor.apps.message.db.MailAccount;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.service.MessageServiceImpl;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.tool.template.TemplateMaker;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public abstract class MessageServiceBaseImpl extends MessageServiceImpl {

	private static final Logger LOG = LoggerFactory.getLogger(MessageServiceBaseImpl.class);
	
	private DateTime todayTime;
	
	@Inject
	private UserInfoService userInfoService;
	
	@Inject
	private TemplateMessageService templateMessageService;
	
	@Inject
	public MessageServiceBaseImpl(UserInfoService userInfoService) {

		this.todayTime = GeneralService.getTodayDateTime();
		this.userInfoService = userInfoService;
	}
	
	
	@Override
	@Transactional
	public Message createMessage(String model, int id, String subject, String content, List<EmailAddress> toEmailAddressList, List<EmailAddress> ccEmailAddressList, 
			List<EmailAddress> bccEmailAddressList, MailAccount mailAccount, String linkPath, String addressBlock, int mediaTypeSelect)  {
		
		Message message = super.createMessage(
				content, 
				null, 
				model, 
				id, 
				null, 
				0, 
				todayTime.toLocalDateTime(), 
				false, 
				IMessage.STATUS_DRAFT, 
				subject, 
				IMessage.TYPE_SENT,
				toEmailAddressList,
				ccEmailAddressList,
				bccEmailAddressList,
				mailAccount,
				linkPath,
				addressBlock,
				mediaTypeSelect)
				.save();
		
		message.setCompany(userInfoService.getUserActiveCompany());
		message.setSenderUserInfo(userInfoService.getUserInfo());
		return message;
		
	}	
	
	@Override
	@Transactional
	public Message sendMessageByEmail(Message message)  {
		try {
			
			super.sendByEmail(message);
			this.sendToUser(message);
			
			
		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return message;
		
	}
	
	
	private void sendToUser(Message message)  {
		
		if(!message.getSentByEmail() && message.getRecipientUserInfo()!=null)  {
			message.setStatusSelect(IMessage.STATUS_SENT);
			message.setSentByEmail(false);
			message.save();
		}
	}
	
	
	public String printMessage(Message message){
//		Company company = message.getCompany();
//		if(company == null)
//			return null;
//		PrintingSettings printSettings = company.getPrintingSettings();
//		printSettings = company.getPrintingSettings();
//		if(printSettings == null || printSettings.getDefaultMailBirtTemplate() == null)
//			return null;
//		BirtTemplate birtTemplate = printSettings.getDefaultMailBirtTemplate();
//		LOG.debug("Default BirtTemplate : {}",birtTemplate);
//		TemplateMaker maker = new TemplateMaker(new Locale("fr"), '$', '$');
//		maker.setContext(JPA.find(message.getClass(), message.getId()), "Message");
//		try {
//			return templateMessageService.generatePdfFromBirtTemplate(maker, birtTemplate, "url");
//		} catch (AxelorException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		return null;
	}
	
	
}