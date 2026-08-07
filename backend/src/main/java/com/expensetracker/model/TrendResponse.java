package com.expensetracker.model;

import java.util.List;

public record TrendResponse(List<TrendPoint> periods) {
}
