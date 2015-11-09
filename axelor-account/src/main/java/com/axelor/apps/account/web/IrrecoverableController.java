/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2014 Axelor (<http://axelor.com>).
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
package com.axelor.apps.account.web;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.ReportSettings;
import com.axelor.apps.account.db.Irrecoverable;
import com.axelor.apps.account.db.repo.IrrecoverableRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.report.IReport;
import com.axelor.apps.account.service.IrrecoverableService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;

public class IrrecoverableController {

	@Inject 
	private IrrecoverableService irrecoverableService;
	
	@Inject
	private IrrecoverableRepository irrecoverableRepo;

	private static final Logger LOG = LoggerFactory.getLogger(IrrecoverableController.class);

	public void getIrrecoverable(ActionRequest request, ActionResponse response)  {

		Irrecoverable irrecoverable = request.getContext().asType(Irrecoverable.class);
		irrecoverable = irrecoverableRepo.find(irrecoverable.getId());

		try {
			irrecoverableService.getIrrecoverable(irrecoverable);
			response.setReload(true);
		}
		catch(Exception e)  { TraceBackService.trace(response, e); }	
	}

	public void createIrrecoverableReport(ActionRequest request, ActionResponse response)  {

		Irrecoverable irrecoverable = request.getContext().asType(Irrecoverable.class);
		irrecoverable = irrecoverableRepo.find(irrecoverable.getId());

		try {
			irrecoverableService.createIrrecoverableReport(irrecoverable);
			response.setReload(true);
		}
		catch(Exception e)  { TraceBackService.trace(response, e); }
	}

	public void passInIrrecoverable(ActionRequest request, ActionResponse response)  {

		Irrecoverable irrecoverable = request.getContext().asType(Irrecoverable.class);
		irrecoverable = irrecoverableRepo.find(irrecoverable.getId());

		try {
			int anomaly = irrecoverableService.passInIrrecoverable(irrecoverable);
			
			response.setReload(true);
			
			response.setFlash(I18n.get(IExceptionMessage.IRRECOVERABLE_5)+" - "+anomaly+" "+I18n.get(IExceptionMessage.IRRECOVERABLE_6));
		}
		catch(Exception e)  { TraceBackService.trace(response, e); }
	}

	public void printIrrecoverable(ActionRequest request, ActionResponse response)  {

		Irrecoverable irrecoverable = request.getContext().asType(Irrecoverable.class);

		if(irrecoverable.getExportTypeSelect() == null) {
			response.setFlash(I18n.get(IExceptionMessage.IRRECOVERABLE_7)); 
		} 
		else {
			StringBuilder url = new StringBuilder();
			
			url.append(new ReportSettings(IReport.IRRECOVERABLE, irrecoverable.getExportTypeSelect())
						.addParam("IrrecoverableID", irrecoverable.getId().toString())
						.getUrl());
			
			LOG.debug("URL : {}", url);

			Map<String,Object> mapView = new HashMap<String,Object>();
			mapView.put("title", I18n.get(IExceptionMessage.IRRECOVERABLE_8)+" "+irrecoverable.getName());
			mapView.put("resource", url);
			mapView.put("viewType", "html");
			response.setView(mapView);			
		}	
	}
}
