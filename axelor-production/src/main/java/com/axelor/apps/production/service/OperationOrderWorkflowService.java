/**
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
package com.axelor.apps.production.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import com.axelor.app.production.db.IOperationOrder;
import com.axelor.app.production.db.IWorkCenter;
import com.axelor.apps.production.db.Machine;
import com.axelor.apps.production.db.OperationOrder;
import com.axelor.apps.production.db.ProdHumanResource;
import com.axelor.apps.production.db.ProdProcessLine;
import com.axelor.apps.production.db.WorkCenter;
import com.axelor.apps.production.db.repo.OperationOrderRepository;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class OperationOrderWorkflowService {

	@Inject
	private OperationOrderStockMoveService operationOrderStockMoveService;

	@Inject
	protected AppProductionService appProductionService;
	
	@Inject
	protected OperationOrderRepository operationOrderRepo;

	private LocalDateTime today;
	
	@Inject
	public OperationOrderWorkflowService(AppProductionService appProductionService) {
		this.appProductionService = appProductionService;
		today = this.appProductionService.getTodayDateTime().toLocalDateTime();

	}

	@Transactional
	public OperationOrder plan(OperationOrder operationOrder) throws AxelorException  {

		operationOrder.setPlannedStartDateT(this.getLastOperationOrder(operationOrder));

		operationOrder.setPlannedEndDateT(this.computePlannedEndDateT(operationOrder));

		operationOrder.setPlannedDuration(
				this.getDuration(
				this.computeDuration(operationOrder.getPlannedStartDateT(), operationOrder.getPlannedEndDateT())));

		operationOrderStockMoveService.createToConsumeStockMove(operationOrder);

		operationOrder.setStatusSelect(IOperationOrder.STATUS_PLANNED);

		return Beans.get(OperationOrderRepository.class).save(operationOrder);

	}
	
	@Transactional
	public OperationOrder replan(OperationOrder operationOrder) throws AxelorException  {

		operationOrder.setPlannedStartDateT(this.getLastOperationOrder(operationOrder));

		operationOrder.setPlannedEndDateT(this.computePlannedEndDateT(operationOrder));

		operationOrder.setPlannedDuration(
				this.getDuration(
				this.computeDuration(operationOrder.getPlannedStartDateT(), operationOrder.getPlannedEndDateT())));

		return Beans.get(OperationOrderRepository.class).save(operationOrder);

	}


	public LocalDateTime getLastOperationOrder(OperationOrder operationOrder)  {

		OperationOrder lastOperationOrder = operationOrderRepo.all().filter("self.manufOrder = ?1 AND self.priority <= ?2 AND self.statusSelect >= 3 AND self.statusSelect < 6 AND self.id != ?3",
				operationOrder.getManufOrder(), operationOrder.getPriority(), operationOrder.getId()).order("-self.priority").order("-self.plannedEndDateT").fetchOne();
		
		if(lastOperationOrder != null)  {
			if(lastOperationOrder.getPriority() == operationOrder.getPriority())  {
				if(lastOperationOrder.getPlannedStartDateT() != null && lastOperationOrder.getPlannedStartDateT().isAfter(operationOrder.getManufOrder().getPlannedStartDateT()))  {
					if(lastOperationOrder.getMachineWorkCenter().equals(operationOrder.getMachineWorkCenter())){
						return lastOperationOrder.getPlannedEndDateT();
					}
					return lastOperationOrder.getPlannedStartDateT();
				}
				else  {
					return operationOrder.getManufOrder().getPlannedStartDateT();
				}
			}
			else  {
				if(lastOperationOrder.getPlannedEndDateT() != null && lastOperationOrder.getPlannedEndDateT().isAfter(operationOrder.getManufOrder().getPlannedStartDateT()))  {
					return lastOperationOrder.getPlannedEndDateT();
				}
				else  {
					return operationOrder.getManufOrder().getPlannedStartDateT();
				}
			}
		}

		return operationOrder.getManufOrder().getPlannedStartDateT();
	}
	



	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void start(OperationOrder operationOrder)  {

		operationOrder.setStatusSelect(IOperationOrder.STATUS_IN_PROGRESS);

		operationOrder.setRealStartDateT(today);
		
		operationOrder.setStartedBy(AuthUtils.getUser());
		
		operationOrder.setStartingDateTime(appProductionService.getTodayDateTime().toLocalDateTime());

		Beans.get(OperationOrderRepository.class).save(operationOrder);

	}


	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void cancel(OperationOrder operationOrder) throws AxelorException  {

		operationOrderStockMoveService.cancel(operationOrder);

		operationOrder.setStatusSelect(IOperationOrder.STATUS_CANCELED);

		Beans.get(OperationOrderRepository.class).save(operationOrder);

	}

	@Transactional
	public OperationOrder finish(OperationOrder operationOrder) throws AxelorException  {

		operationOrderStockMoveService.finish(operationOrder);

		operationOrder.setRealEndDateT(today);

		operationOrder.setStatusSelect(IOperationOrder.STATUS_FINISHED);

		return Beans.get(OperationOrderRepository.class).save(operationOrder);

	}


	public Duration computeDuration(LocalDateTime startDateTime, LocalDateTime endDateTime)  {

		return Duration.between(startDateTime, endDateTime);

	}

	public long getDuration(Duration duration)  {

		return duration.getSeconds();

	}


	public LocalDateTime computePlannedEndDateT(OperationOrder operationOrder)  {

		if(operationOrder.getWorkCenter() != null)  {
			return operationOrder.getPlannedStartDateT()
					.plusSeconds((int)this.computeEntireCycleDuration(operationOrder, operationOrder.getManufOrder().getQty()));
		}

		return operationOrder.getPlannedStartDateT();
	}


	public long computeEntireCycleDuration(OperationOrder operationOrder, BigDecimal qty)  {

		long machineDuration = this.computeMachineDuration(operationOrder, qty);

		long humanDuration = this.computeHumanDuration(operationOrder, qty);

		if(machineDuration >= humanDuration)  {
			return machineDuration;
		}
		else  {
			return humanDuration;
		}

	}


	public long computeMachineDuration(OperationOrder operationOrder, BigDecimal qty)  {
		ProdProcessLine prodProcessLine = operationOrder.getProdProcessLine();
		WorkCenter workCenter = prodProcessLine.getWorkCenter();
		
		long duration = 0;

		int workCenterTypeSelect = workCenter.getWorkCenterTypeSelect();

		if(workCenterTypeSelect == IWorkCenter.WORK_CENTER_MACHINE || workCenterTypeSelect == IWorkCenter.WORK_CENTER_BOTH)  {
			Machine machine = workCenter.getMachine();
			duration += machine.getStartingDuration();

			BigDecimal durationPerCycle = new BigDecimal(prodProcessLine.getDurationPerCycle());
			BigDecimal maxCapacityPerCycle = prodProcessLine.getMaxCapacityPerCycle();

			if (maxCapacityPerCycle.compareTo(BigDecimal.ZERO) == 0) {
				duration += qty.multiply(durationPerCycle).longValue();
			} else {
				duration += (qty.divide(maxCapacityPerCycle)).multiply(durationPerCycle).longValue();
			}

			duration += machine.getEndingDuration();

		}

		return duration;
	}


	public long computeHumanDuration(OperationOrder operationOrder, BigDecimal qty)  {
		WorkCenter workCenter = operationOrder.getProdProcessLine().getWorkCenter();

		long duration = 0;

		int workCenterTypeSelect = workCenter.getWorkCenterTypeSelect();

		if(workCenterTypeSelect == IWorkCenter.WORK_CENTER_HUMAN || workCenterTypeSelect == IWorkCenter.WORK_CENTER_BOTH)  {

			if(operationOrder.getProdHumanResourceList() != null)  {

				for(ProdHumanResource prodHumanResource : operationOrder.getProdHumanResourceList())  {

					duration += prodHumanResource.getDuration();

				}

			}

		}

		return qty.multiply(new BigDecimal(duration)).longValue();
	}






}

