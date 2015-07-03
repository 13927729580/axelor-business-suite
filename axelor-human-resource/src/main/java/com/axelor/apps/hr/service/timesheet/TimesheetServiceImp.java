package com.axelor.apps.hr.service.timesheet;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.LocalDate;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.IPriceListLine;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.GeneralRepository;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.apps.hr.db.DayPlanning;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.Leave;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.WeeklyPlanning;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.db.repo.LeaveRepository;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.employee.EmployeeService;
import com.axelor.auth.AuthUtils;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;


public class TimesheetServiceImp extends TimesheetRepository implements TimesheetService{

	@Inject
	private EmployeeService employeeService;

	@Inject
	private PriceListService priceListService;

	@Override
	@Transactional(rollbackOn={Exception.class})
	public void getTimeFromTask(Timesheet timesheet){

		List<TimesheetLine> timesheetLineList = TimesheetLineRepository.of(TimesheetLine.class).all().filter("self.user = ?1 AND self.affectedToTimeSheet = null AND self.projectTask != null", timesheet.getUser()).fetch();
		for (TimesheetLine timesheetLine : timesheetLineList) {
			timesheet.addTimesheetLineListItem(timesheetLine);
		}
		JPA.save(timesheet);
	}

	@Override
	@Transactional(rollbackOn={Exception.class})
	public void cancelTimesheet(Timesheet timesheet){
		timesheet.setStatusSelect(5);
		List<TimesheetLine> timesheetLineList = timesheet.getTimesheetLineList();
		for (TimesheetLine timesheetLine : timesheetLineList) {
			if(timesheetLine.getProjectTask() != null){
				timesheetLine.setAffectedToTimeSheet(null);
				JPA.save(timesheetLine);
			}
		}
	}

	@Override
	public Timesheet generateLines(Timesheet timesheet) throws AxelorException{

		if(timesheet.getFromGenerationDate() == null) {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.TIMESHEET_FROM_DATE)), IException.CONFIGURATION_ERROR);
		}
		if(timesheet.getToGenerationDate() == null) {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.TIMESHEET_TO_DATE)), IException.CONFIGURATION_ERROR);
		}
		if(timesheet.getProduct() == null) {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.TIMESHEET_PRODUCT)), IException.CONFIGURATION_ERROR);
		}
		if(timesheet.getUser().getEmployee() == null){
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),timesheet.getUser().getName()), IException.CONFIGURATION_ERROR);
		}
		WeeklyPlanning planning = timesheet.getUser().getEmployee().getPlanning();
		if(planning == null){
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.TIMESHEET_EMPLOYEE_DAY_PLANNING),timesheet.getUser().getName()), IException.CONFIGURATION_ERROR);
		}
		List<DayPlanning> dayPlanningList = planning.getWeekDays();

		LocalDate fromDate = timesheet.getFromGenerationDate();
		LocalDate toDate = timesheet.getToGenerationDate();
		Map<Integer,String> correspMap = new HashMap<Integer,String>();
		correspMap.put(1, "monday");
		correspMap.put(2, "tuesday");
		correspMap.put(3, "wednesday");
		correspMap.put(4, "thursday");
		correspMap.put(5, "friday");
		correspMap.put(6, "saturday");
		correspMap.put(7, "sunday");
		List<Leave> leaveList = LeaveRepository.of(Leave.class).all().filter("self.user = ?1 AND (self.statusSelect = 2 OR self.statusSelect = 3)", timesheet.getUser()).fetch();
		while(!fromDate.isAfter(toDate)){
			DayPlanning dayPlanningCurr = new DayPlanning();
			for (DayPlanning dayPlanning : dayPlanningList) {
				if(dayPlanning.getName().equals(correspMap.get(fromDate.getDayOfWeek()))){
					dayPlanningCurr = dayPlanning;
					break;
				}
			}
			if(dayPlanningCurr.getMorningFrom() != null || dayPlanningCurr.getMorningTo() != null || dayPlanningCurr.getAfternoonFrom() != null || dayPlanningCurr.getAfternoonTo() != null)
			{
				boolean noLeave = true;
				if(leaveList != null){
					for (Leave leave : leaveList) {
						if((leave.getDateFrom().isBefore(fromDate) && leave.getDateTo().isAfter(fromDate))
							|| leave.getDateFrom().isEqual(fromDate) || leave.getDateTo().isEqual(fromDate))
						{
							noLeave = false;
							break;
						}
					}
				}
				if(noLeave){
					TimesheetLine timesheetLine = new TimesheetLine();
					timesheetLine.setDate(fromDate);
					timesheetLine.setUser(timesheet.getUser());
					timesheetLine.setProjectTask(timesheet.getProjectTask());
					timesheetLine.setVisibleDuration(timesheet.getLogTime());
					timesheetLine.setDurationStored(employeeService.getDurationHours(timesheet.getLogTime()));
					timesheetLine.setProduct(timesheet.getProduct());
					timesheet.addTimesheetLineListItem(timesheetLine);
				}
			}
			fromDate=fromDate.plusDays(1);
		}
		return timesheet;
	}

	@Override
	@Transactional
	public LocalDate getFromPeriodDate(){
		Timesheet timesheet = Beans.get(TimesheetRepository.class).all().filter("self.user = ?1 ORDER BY self.toDate DESC", AuthUtils.getUser()).fetchOne();
		if(timesheet != null){
			save(timesheet);
			return timesheet.getToDate();
		}
		else{
			return new LocalDate();
		}
	}

	public List<InvoiceLine> createInvoiceLines(Invoice invoice, List<TimesheetLine> timesheetLineList, int priority) throws AxelorException  {

		List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();
		int count = 0;
		for(TimesheetLine timesheetLine : timesheetLineList)  {

			invoiceLineList.addAll(this.createInvoiceLine(invoice, timesheetLine, priority*100+count));
			count++;
			timesheetLine.setInvoiced(true);
		}

		return invoiceLineList;

	}

	public List<InvoiceLine> createInvoiceLine(Invoice invoice, TimesheetLine timesheetLine, int priority) throws AxelorException  {

		Product product = null;
		Employee employee = timesheetLine.getUser().getEmployee();

		int discountTypeSelect = 1;
		product = timesheetLine.getProduct();
		if(product == null){
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.TIMESHEET_PRODUCT)), IException.CONFIGURATION_ERROR);
		}
		BigDecimal price = product.getSalePrice();
		BigDecimal discountAmount = product.getCostPrice();


		BigDecimal qtyConverted = timesheetLine.getVisibleDuration();
		qtyConverted = Beans.get(UnitConversionService.class).convert(product.getUnit(), GeneralService.getGeneral().getUnitHours(), timesheetLine.getVisibleDuration());
		Unit unit = GeneralService.getGeneral().getUnitHours();
		if(employee != null){
			if(employee.getTimeLoggingPreferenceSelect() == EmployeeRepository.TIME_PREFERENCE_DAYS){
				qtyConverted = Beans.get(UnitConversionService.class).convert(product.getUnit(), GeneralService.getGeneral().getUnitDays(), timesheetLine.getVisibleDuration());
				unit = GeneralService.getGeneral().getUnitDays();
			}
			else if(employee.getTimeLoggingPreferenceSelect() == EmployeeRepository.TIME_PREFERENCE_MINUTES){
				qtyConverted = Beans.get(UnitConversionService.class).convert(product.getUnit(), GeneralService.getGeneral().getUnitMinutes(), timesheetLine.getVisibleDuration());
				unit = GeneralService.getGeneral().getUnitMinutes();
			}

		}

		PriceList priceList = invoice.getPartner().getSalePriceList();
		if(priceList != null)  {
			PriceListLine priceListLine = priceListService.getPriceListLine(product, qtyConverted, priceList);
			if(priceListLine!=null){
				discountTypeSelect = priceListLine.getTypeSelect();
			}
			if((GeneralService.getGeneral().getComputeMethodDiscountSelect() == GeneralRepository.INCLUDE_DISCOUNT_REPLACE_ONLY && discountTypeSelect == IPriceListLine.TYPE_REPLACE) || GeneralService.getGeneral().getComputeMethodDiscountSelect() == GeneralRepository.INCLUDE_DISCOUNT)
			{
				Map<String, Object> discounts = priceListService.getDiscounts(priceList, priceListLine, price);
				discountAmount = (BigDecimal) discounts.get("discountAmount");
				price = priceListService.computeDiscount(price, (int) discounts.get("discountTypeSelect"), discountAmount);

			}
			else{
				Map<String, Object> discounts = priceListService.getDiscounts(priceList, priceListLine, price);
				discountAmount = (BigDecimal) discounts.get("discountAmount");
				if(discounts.get("price") != null)  {
					price = (BigDecimal) discounts.get("price");
				}
			}

		}

		InvoiceLineGenerator invoiceLineGenerator = new InvoiceLineGenerator (invoice, product, product.getName(), price,
				null,qtyConverted,unit,priority,discountAmount,discountTypeSelect,
				price.multiply(qtyConverted),null,false){

			@Override
			public List<InvoiceLine> creates() throws AxelorException {

				InvoiceLine invoiceLine = this.createInvoiceLine();

				List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
				invoiceLines.add(invoiceLine);

				return invoiceLines;
			}
		};

		return invoiceLineGenerator.creates();
	}
}
