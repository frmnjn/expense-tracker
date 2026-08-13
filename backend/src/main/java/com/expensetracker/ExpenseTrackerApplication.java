package com.expensetracker;

import com.expensetracker.model.ApiResponse;
import com.expensetracker.model.AiAnalysisResponse;
import com.expensetracker.model.AiInvoiceItem;
import com.expensetracker.model.BatchExpenseItem;
import com.expensetracker.model.BatchExpenseRequest;
import com.expensetracker.model.BudgetCreateRequest;
import com.expensetracker.model.BudgetOption;
import com.expensetracker.model.BudgetUpdateRequest;
import com.expensetracker.model.BudgetSummary;
import com.expensetracker.model.ExpenseRequest;
import com.expensetracker.model.ExpenseResponse;
import com.expensetracker.model.ExpensesResponse;
import com.expensetracker.model.InvoiceDetailResponse;
import com.expensetracker.model.InvoiceResponse;
import com.expensetracker.model.InvoicesResponse;
import com.expensetracker.model.OptionsResponse;
import com.expensetracker.model.PeriodsResponse;
import com.expensetracker.model.SummaryResponse;
import com.expensetracker.model.TopUpRequest;
import com.expensetracker.model.TopUpResponse;
import com.expensetracker.model.TopUpsResponse;
import com.expensetracker.model.TrendPoint;
import com.expensetracker.model.TrendResponse;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@RegisterReflectionForBinding({
        ApiResponse.class,
        AiAnalysisResponse.class,
        AiInvoiceItem.class,
        AiInvoiceItem[].class,
        BatchExpenseItem.class,
        BatchExpenseItem[].class,
        BatchExpenseRequest.class,
        BudgetCreateRequest.class,
        BudgetOption.class,
        BudgetUpdateRequest.class,
        BudgetSummary.class,
        ExpenseRequest.class,
        ExpenseResponse.class,
        ExpenseResponse[].class,
        ExpensesResponse.class,
        InvoiceDetailResponse.class,
        InvoiceResponse.class,
        InvoiceResponse[].class,
        InvoicesResponse.class,
        OptionsResponse.class,
        PeriodsResponse.class,
        SummaryResponse.class,
        TopUpRequest.class,
        TopUpResponse.class,
        TopUpsResponse.class,
        TrendPoint.class,
        TrendResponse.class,
})
public class ExpenseTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseTrackerApplication.class, args);
    }
}
