package com.axelor.apps.supplychain.web;

import java.util.ArrayList;
import java.util.List;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.service.SaleOrderLineService;
import com.axelor.apps.supplychain.db.Subscription;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceService;
import com.axelor.apps.supplychain.service.SubscriptionService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;

public class SubscriptionController {

	@Inject
	protected SubscriptionService subscriptionService;

	public void generateSubscriptions(ActionRequest request, ActionResponse response) throws AxelorException{
		SaleOrderLine saleOrderLine = request.getContext().asType(SaleOrderLine.class);

		if(saleOrderLine.getId()!=null && saleOrderLine.getId()>0){
			saleOrderLine=Beans.get(SaleOrderLineRepository.class).find(saleOrderLine.getId());
		}

		saleOrderLine = subscriptionService.generateSubscriptions(saleOrderLine);
		response.setValue("subscriptionList", saleOrderLine.getSubscriptionList());
	}

	public void generateAllSubscriptions(ActionRequest request, ActionResponse response) throws AxelorException{
		SaleOrderLine saleOrderLine = request.getContext().asType(SaleOrderLine.class);

		SaleOrder saleOrder = saleOrderLine.getSaleOrder();

		if(saleOrder == null){
			saleOrder = request.getContext().getParentContext().asType(SaleOrder.class);
		}

		saleOrder  = Beans.get(SaleOrderRepository.class).find(saleOrder.getId());

		for (SaleOrderLine saleOrderLineIt : saleOrder.getSaleOrderLineList()) {
			if(saleOrderLineIt.getProduct().getProductTypeSelect().equals(ProductRepository.PRODUCT_TYPE_SUBSCRIPTABLE)){
				subscriptionService.generateSubscriptions(saleOrderLineIt,saleOrderLine);
			}
		}

		response.setReload(true);
	}

	public void generateInvoice(ActionRequest request, ActionResponse response)  {

		Subscription subscription = request.getContext().asType(Subscription.class);

		subscription = subscriptionService.find(subscription.getId());

		if (subscription != null){

			SaleOrderLine saleOrderLine  = Beans.get(SaleOrderLineService.class).find(subscription.getSaleOrderLine().getId());

			SaleOrder saleOrder  = Beans.get(SaleOrderRepository.class).find(saleOrderLine.getSaleOrder().getId());

			List<SaleOrderLine> saleOrderLineList = new ArrayList<SaleOrderLine>();

			saleOrderLineList.add(saleOrderLine);
			try {
				Invoice invoice = Beans.get(SaleOrderInvoiceService.class).generateSubscriptionInvoice(subscription,saleOrderLineList,saleOrder);

				if(invoice != null)  {
					response.setReload(true);
					response.setFlash(I18n.get(IExceptionMessage.PO_INVOICE_2));
				}
			}
			catch(Exception e)  { TraceBackService.trace(response, e); }
		}
	}
}
