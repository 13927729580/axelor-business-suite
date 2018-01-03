/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2017 Axelor (<http://axelor.com>).
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
package com.axelor.apps.stock.db.repo;

import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.service.LocationSaveService;
import com.axelor.inject.Beans;

public class StockLocationStockRepository extends StockLocationRepository {

    /**
     * Override to remove incompatible locations in partners
     * @param entity
     * @return
     */
    @Override
    public StockLocation save(StockLocation entity) {
        Beans.get(LocationSaveService.class).removeForbiddenDefaultLocation(entity);
        return super.save(entity);
    }
}