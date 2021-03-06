/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.production.service.batch;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.production.db.ManufOrder;
import com.axelor.apps.production.db.ProductionBatch;
import com.axelor.apps.production.db.repo.CostSheetRepository;
import com.axelor.apps.production.db.repo.ManufOrderRepository;
import com.axelor.apps.production.exceptions.IExceptionMessage;
import com.axelor.apps.production.service.costsheet.CostSheetService;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.db.Query;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatchComputeWorkInProgressValuation extends AbstractBatch {

  private CostSheetService costSheetService;

  protected static final int FETCH_LIMIT = 1;

  @Inject
  public BatchComputeWorkInProgressValuation(CostSheetService costSheetService) {
    this.costSheetService = costSheetService;
  }

  @Override
  protected void process() {
    ProductionBatch productionBatch = batch.getProductionBatch();
    Company company = productionBatch.getCompany();
    StockLocation workshopStockLocation = productionBatch.getWorkshopStockLocation();

    List<ManufOrder> manufOrderList = null;
    Map<String, Object> bindValues = new HashMap<String, Object>();
    String domain =
        "(self.statusSelect = :statusSelectInProgress or self.statusSelect = :statusSelectStandBy)";
    bindValues.put("statusSelectInProgress", ManufOrderRepository.STATUS_IN_PROGRESS);
    bindValues.put("statusSelectStandBy", ManufOrderRepository.STATUS_STANDBY);

    if (company != null && workshopStockLocation == null) {
      domain += " and self.company.id = :companyId";
      bindValues.put("companyId", company.getId());

    } else if (company == null && workshopStockLocation != null) {
      domain += " and self.workshopStockLocation.id = :stockLocationId";
      bindValues.put("stockLocationId", workshopStockLocation.getId());

    } else if (company != null && workshopStockLocation != null) {
      domain +=
          " and (self.company.id = :companyId and self.workshopStockLocation.id = :stockLocationId)";
      bindValues.put("companyId", company.getId());
      bindValues.put("stockLocationId", workshopStockLocation.getId());
    }

    Query<ManufOrder> manufOrderQuery =
        Beans.get(ManufOrderRepository.class).all().filter(domain).bind(bindValues);

    int offset = 0;

    while (!(manufOrderList = manufOrderQuery.fetch(FETCH_LIMIT, offset)).isEmpty()) {
      try {
        costSheetService.computeCostPrice(
            manufOrderList.get(0),
            CostSheetRepository.CALCULATION_WORK_IN_PROGRESS,
            productionBatch.getValuationDate());
        incrementDone();
      } catch (Exception e) {
        incrementAnomaly();
        TraceBackService.trace(e, IExceptionMessage.MANUF_ORDER_NO_GENERATION, batch.getId());
      }
      offset++;
    }
  }

  @Override
  protected void stop() {

    String comment =
        String.format(
            "\t* %s " + I18n.get(IExceptionMessage.BATCH_COMPUTE_VALUATION) + "\n",
            batch.getDone());

    comment +=
        String.format(
            "\t" + I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.ALARM_ENGINE_BATCH_4),
            batch.getAnomaly());

    addComment(comment);
    super.stop();
  }
}
